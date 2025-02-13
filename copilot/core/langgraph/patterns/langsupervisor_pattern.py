from langchain_openai import ChatOpenAI
from langgraph_supervisor import create_supervisor

from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.langgraph.special_nodes.supervisor_node import SupervisorNode, get_supervisor_system_prompt, \
    get_supervisor_temperature
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.store.memory import InMemoryStore

store = InMemoryStore()

class LangSupervisorPattern(BasePattern):
    _first = None

    def __init__(self, members, assistant_graph, pattern: BasePattern, memory, full_question=None):
        self._pattern = pattern
        self._full_question = full_question
        model = ChatOpenAI(model="gpt-4o")
        # Create supervisor workflow
        sv_prompt = get_supervisor_system_prompt(full_question)

        workflow = create_supervisor(
            members,
            model=model,
            prompt=sv_prompt
        )

        # Compile and run
        self._graph = workflow.compile(
            checkpointer=memory,
            store=store
        )
        self._graph.get_graph().print_ascii()

    def get_graph(self):
        return self._graph