import logging

from copilot.core.schemas import AssistantGraph
from langchain_core.messages import HumanMessage

from ..utils import read_optional_env_var_int
from .patterns.base_pattern import BasePattern
from .patterns.loop_pattern import LoopPattern

logger = logging.getLogger(__name__)


class CopilotLangGraph:
    assistant_graph: AssistantGraph
    _pattern: BasePattern
    _graph: LoopPattern

    def __init__(self, members, assistant_graph, pattern: BasePattern, memory, full_question=None):
        self._pattern = pattern
        self._full_question = full_question
        workflow = self.get_pattern().construct_nodes(members, assistant_graph, full_question)
        self.get_pattern().connect_graph(assistant_graph, workflow)
        self._assistant_graph = assistant_graph
        self._graph = workflow.compile(checkpointer=memory)
        self._graph.get_graph().print_ascii()

    def get_pattern(self):
        return self._pattern

    def invoke(self, question, thread_id, get_image=False):
        config = {
            "configurable": {"thread_id": thread_id},
            "recursion_limit": read_optional_env_var_int("LANGGRAPH_RECURSION_LIMIT", 50),
            "max_iterations": 100
        }
        if get_image:
            import base64

            return base64.b64encode(self._graph.get_graph().draw_mermaid_png()).decode()
        message = self.print_messages(config, question)

        if message is None:
            return ""
        return message[list(message.keys())[0]]["messages"][-1].content

    def print_messages(self, config, question):
        message = None
        for message in self._graph.stream({"messages": [HumanMessage(content=question)]}, config):
            if "__end__" not in message:
                print("----")
        return message
