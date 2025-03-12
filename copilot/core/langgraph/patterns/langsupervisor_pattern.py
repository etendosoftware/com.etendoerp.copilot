from langchain_openai import ChatOpenAI
from langgraph_supervisor import create_supervisor

from copilot.core.langgraph.tools.TaskManagementTool import task_management_tool, LangSupervisorState
from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.langgraph.special_nodes.supervisor_node import get_supervisor_system_prompt


class LangSupervisorPattern(BasePattern):
    _first = None

    def __init__(self, members, assistant_graph, pattern: BasePattern, memory, full_question=None):
        self._pattern = pattern
        self._full_question = full_question
        model = ChatOpenAI(model="gpt-4o", temperature=0.1)
        # Create supervisor workflow
        sv_prompt = get_supervisor_system_prompt(full_question)

        tools=[task_management_tool]

        supervisor = create_supervisor(
            members,
            model=model,
            tools = tools,
            prompt=sv_prompt ,
            output_mode='full_history',
            state_schema=LangSupervisorState,
            config_schema={
                "configurable": {},
                "recursion_limit": 500,
                "max_iterations": 1000,
            }
        )

        # Compile and run
        self._graph = supervisor.compile(
            checkpointer=memory, name="supervisor"
        )
        self._graph.get_graph().print_ascii()

    def get_graph(self):
        return self._graph
