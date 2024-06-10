import logging

from IPython.display import Image, display
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.sqlite import SqliteSaver

from copilot.core.schemas import AssistantGraph
from .patterns.base_pattern import BasePattern
from .patterns.loop_pattern import LoopPattern
from .patterns.supervisor_pattern import SupervisorPattern

logger = logging.getLogger(__name__)

memory = SqliteSaver.from_conn_string("checkpoints.sqlite")

class CopilotLangGraph:

    assistant_graph: AssistantGraph
    _pattern: BasePattern

    def __init__(self, members, assistant_graph, pattern: BasePattern = None):
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
        message = None
        config = {
            "configurable": {"thread_id": thread_id},
            "recursion_limit": 50
        }
        messages = self._graph.stream(
                {"messages": [HumanMessage(content=question)]},
            config
        )

        for message in messages:
            if "__end__" not in message:
                print("----")


        return message[list(message.keys())[0]]["messages"][-1].content

