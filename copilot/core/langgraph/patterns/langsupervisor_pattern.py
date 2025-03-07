from typing import Annotated, Sequence

from colorama import Fore
from langchain_core.messages import BaseMessage
from langchain_openai import ChatOpenAI
from langgraph.graph import add_messages
from langgraph.prebuilt.chat_agent_executor import AgentState
from langgraph.store.memory import InMemoryStore
from langgraph_supervisor import create_supervisor

from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.langgraph.special_nodes.supervisor_node import get_supervisor_system_prompt
from typing import Annotated, List
import operator

from copilot.core.utils import copilot_debug_custom


class LangSupervisorState(AgentState):
    tasks_to_process: Annotated[List[str], operator.add]  # Lista de elementos extraídos del CSV
    messages: Annotated[Sequence[BaseMessage], add_messages]
    task_to_process: str

store = InMemoryStore()

class LangSupervisorPattern(BasePattern):
    _first = None

    def __init__(self, members, assistant_graph, pattern: BasePattern, memory, full_question=None):
        self._pattern = pattern
        self._full_question = full_question
        model = ChatOpenAI(model="gpt-4o", temperature=0.1)
        # Create supervisor workflow
        sv_prompt = get_supervisor_system_prompt(full_question)

        from langchain_core.tools import tool

        @tool()
        def get_next_task(state: LangSupervisorState) :
            """Tool to get the next task to process."""
            if not state["tasks_to_process"]:
                state["messages"].append("No more tasks left! Maybe load a new file?")
                return {"processing_data": {}, "messages": state["messages"]}

            next_task = state["tasks_to_process"].pop(0)
            state["task_to_process"] = next_task

            copilot_debug_custom(f"[State] get_next_task {next_task}", Fore.LIGHTMAGENTA_EX)
            return {
                "task_to_process": next_task,
                "messages": [f"Next task to process: {next_task}"]
            }

        @tool()
        def add_task(state: LangSupervisorState, next_task: str) :
            """Tool to add an task to the list."""
            state["tasks_to_process"].append(next_task)
            copilot_debug_custom(f"[State] add_task {next_task}", Fore.LIGHTMAGENTA_EX)
            return {"messages": [f"Added task {next_task} to the list."]}

        @tool()
        def add_tasks(state: LangSupervisorState, next_tasks: List[str]) :
            """Tool to add a list of tasks to the todo list."""
            state["tasks_to_process"] += next_tasks
            copilot_debug_custom(f"[State] add_tasks {len(next_tasks)}", Fore.LIGHTMAGENTA_EX)
            return {"messages": [f"Added {len(next_tasks)} tasks to the list."], "tasks_to_process": next_tasks}

        @tool
        def tasks_status(state: LangSupervisorState):
            """Tool to know how many tasks are left to process."""
            copilot_debug_custom(f"[State] tasks_status {len(state['tasks_to_process'])}", Fore.LIGHTMAGENTA_EX)
            return {"messages": [f"There are {len(state['tasks_to_process'])} tasks to process."]}

        workflow = create_supervisor(
            members,
            model=model,
            tools=[get_next_task, add_task, add_tasks, tasks_status],
            prompt=sv_prompt,
            output_mode='full_history',
            state_schema = LangSupervisorState
        )

        # Compile and run
        self._graph = workflow.compile(
            checkpointer=memory,
            store=store
        )
        self._graph.get_graph().print_ascii()

    def get_graph(self):
        return self._graph