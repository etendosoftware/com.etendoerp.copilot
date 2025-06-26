from typing import Annotated, List

from copilot.core.langgraph.members_util import codify_name
from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.langgraph.special_nodes.supervisor_node import (
    get_supervisor_system_prompt,
)
from langchain_core.messages import ToolMessage
from langchain_core.tools import InjectedToolCallId
from langchain_openai import ChatOpenAI
from langgraph.prebuilt import InjectedState
from langgraph.prebuilt.chat_agent_executor import AgentState
from langgraph.store.memory import InMemoryStore
from langgraph.types import Command
from langgraph_supervisor import create_supervisor


def new_toolmessage(next_tasks, tool_call_id):
    return [ToolMessage(content=str(next_tasks), tool_call_id=tool_call_id)]


class LangSupervisorState(AgentState):
    tasks_to_process: List[str]  # pending
    current_task: str  # current
    done_tasks: List[str]  # Already done


store = InMemoryStore()


class LangSupervisorPattern(BasePattern):
    _first = None

    def __init__(self, members, assistant_graph, pattern: BasePattern, memory, full_question=None):
        self._pattern = pattern
        self._full_question = full_question
        model = ChatOpenAI(model="gpt-4o", temperature=0.1)
        # Create supervisor workflow
        members_names = [m.name for m in full_question.assistants]
        members_descriptions = [m.description for m in full_question.assistants]
        sv_prompt = get_supervisor_system_prompt(full_question, members_names, members_descriptions)

        from langchain_core.tools import tool

        @tool()
        def task_management_tool(
            mode: Annotated[str, "Mode of operation: 'get_next', 'add_tasks', 'status', 'mark_done'"],
            state: Annotated[dict, InjectedState],
            tool_call_id: Annotated[str, InjectedToolCallId],
            new_tasks: List[str] = None,
        ):
            """
            Unified tool to manage tasks. Modes:
            - 'get_next': Retrieve and set the next task to process.
            - 'add_tasks': Add a list of tasks to the queue.
            - 'status': Retrieve the status of tasks (pending, current, done).
            - 'mark_done': Mark the current task as done.
            """
            if "tasks_to_process" not in state or state["tasks_to_process"] is None:
                state["tasks_to_process"] = []
            if "done_tasks" not in state or state["done_tasks"] is None:
                state["done_tasks"] = []
            if "current_task" not in state:
                state["current_task"] = None

            if mode == "get_next":
                if not state["tasks_to_process"]:
                    return Command(
                        update={
                            "task_to_process": [],
                            "current_task": state["current_task"],
                            "done_tasks": state["done_tasks"],
                            "messages": new_toolmessage("There are no tasks to process.", tool_call_id),
                        }
                    )
                next_task = state["tasks_to_process"].pop(0)
                state["current_task"] = next_task
                return Command(
                    update={
                        "tasks_to_process": state["tasks_to_process"],
                        "current_task": next_task,
                        "done_tasks": state["done_tasks"],
                        "messages": new_toolmessage(f"New Current Task is '{next_task}'", tool_call_id),
                    }
                )

            elif mode == "add_tasks":
                if new_tasks:
                    state["tasks_to_process"].extend(new_tasks)
                return Command(
                    update={
                        "tasks_to_process": state["tasks_to_process"],
                        "current_task": state["current_task"],
                        "done_tasks": state["done_tasks"],
                        "messages": new_toolmessage(
                            f"Added {len(new_tasks)} tasks." if new_tasks else "No tasks added.", tool_call_id
                        ),
                    }
                )

            elif mode == "status":
                status_message = f"Tasks to process: {len(state['tasks_to_process'])}. Current task: {state['current_task']}. Done tasks: {len(state['done_tasks'])}"
                return Command(
                    update={
                        "tasks_to_process": state["tasks_to_process"],
                        "current_task": state["current_task"],
                        "done_tasks": state["done_tasks"],
                        "messages": new_toolmessage(status_message, tool_call_id),
                    }
                )

            elif mode == "mark_done":
                if state["current_task"]:
                    state["done_tasks"].append(state["current_task"])
                    state["current_task"] = None
                return Command(
                    update={
                        "tasks_to_process": state["tasks_to_process"],
                        "current_task": None,
                        "done_tasks": state["done_tasks"],
                        "messages": new_toolmessage(
                            (
                                f"Task '{state['done_tasks'][-1]}' marked as done."
                                if state["done_tasks"]
                                else "No current task to mark as done."
                            ),
                            tool_call_id,
                        ),
                    }
                )

            else:
                return Command(
                    update={
                        "messages": new_toolmessage("Invalid mode selected.", tool_call_id),
                    }
                )

        _tool = []
        if full_question is not None and (full_question.tools is not None) and len(full_question.tools) > 0:
            for tl in full_question.tools:
                if tl.function.name == "TaskManagementTool":
                    _tool.append(task_management_tool)
        workflow = create_supervisor(
            members,
            model=model,
            tools=_tool,
            prompt=sv_prompt,
            supervisor_name=(
                codify_name(full_question.name) if full_question.name is not None else "Supervisor"
            ),
            # output_mode="full_history",
            state_schema=LangSupervisorState,
        )

        # Compile and run
        self._graph = workflow.compile(checkpointer=memory, store=store)
        self._graph.get_graph().print_ascii()

    def get_graph(self):
        return self._graph
