import logging

from langchain_core.messages import HumanMessage
from langgraph.checkpoint.sqlite import SqliteSaver

from copilot.core.schemas import AssistantGraph
from .patterns.base_pattern import BasePattern
from .patterns.loop_pattern import LoopPattern

logger = logging.getLogger(__name__)

memory = SqliteSaver.from_conn_string("checkpoints.sqlite")


class CopilotLangGraph:
    assistant_graph: AssistantGraph
    _pattern: BasePattern
    _graph: LoopPattern

    def __init__(self, members, assistant_graph, pattern: BasePattern):
        self._pattern = pattern
        workflow = self.get_pattern().construct_nodes(members, assistant_graph)
        self.get_pattern().connect_graph(assistant_graph, workflow)
        self._assistant_graph = assistant_graph
        self._graph = workflow.compile(checkpointer=memory)
        self._graph.get_graph().print_ascii()
        try:
            binary_content = self._graph.get_graph().draw_mermaid_png()
            with open("graph.png", 'wb') as file:
                file.write(binary_content)
        except Exception as e:
            logger.exception(e)

    def get_pattern(self):
        return self._pattern

    def invoke(self, question, thread_id):
        config = {
            "configurable": {"thread_id": thread_id},
            "recursion_limit": 50
        }
        message = self.print_messages(config, question)

        if message is None:
            return ""
        return message[list(message.keys())[0]]["messages"][-1].content

    def print_messages(self, config, question):
        message = None
        for message in self._graph.stream(
                {"messages": [HumanMessage(content=question)]},
                config
        ):
            if "__end__" not in message:
                print("----")
        return message
