import os
import uuid

from langchain.agents.openai_assistant.base import OpenAIAssistantAction
from langchain_core.agents import AgentFinish
from langchain_core.messages import HumanMessage
from langchain_core.runnables import AddableDict
from langgraph.checkpoint.aiosqlite import AsyncSqliteSaver
from langgraph.checkpoint.sqlite import SqliteSaver

from .agent import AgentResponse, CopilotAgent
from .agent import AssistantResponse
from ..langgraph.copilot_langgraph import CopilotLangGraph
from ..langgraph.members_util import MembersUtil
from ..langgraph.patterns import SupervisorPattern
from ..langgraph.patterns.base_pattern import GraphMember
from ..memory.memory_handler import MemoryHandler
from ..schemas import GraphQuestionSchema


class LanggraphAgent(CopilotAgent):
    _memory: MemoryHandler = None

    def __init__(self):
        super().__init__()
        self._memory = MemoryHandler()

    # The agent state is the input to each node in the graph
    def execute(self, question: GraphQuestionSchema) -> AgentResponse:
        thread_id = question.conversation_id
        _tools = self._configured_tools

        members: list[GraphMember] = MembersUtil().get_members(question)

        memory = SqliteSaver.from_conn_string("checkpoints.sqlite")
        lang_graph = CopilotLangGraph(members, question.graph, SupervisorPattern(), memory=memory)

        final_response = lang_graph.invoke(question=question.question, thread_id=thread_id, get_image=question.generate_image)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=final_response, conversation_id=thread_id
            )
        )

    async def aexecute(self, question: GraphQuestionSchema) -> AgentResponse:
        copilot_stream_debug = os.getenv("COPILOT_STREAM_DEBUG", "false").lower() == "true" # Debug mode
        members: list[GraphMember] = MembersUtil().get_members(question)
        memory = AsyncSqliteSaver.from_conn_string("checkpoints.sqlite")
        lang_graph = CopilotLangGraph(members, question.graph, SupervisorPattern(), memory=memory)

        full_question = question.question
        _input = HumanMessage(content=full_question)
        config = {
            "configurable": {
            }
        }
        if question.conversation_id is not None:
            config["configurable"]["thread_id"] = question.conversation_id
        else:
            config["configurable"]["thread_id"] = str(uuid.uuid4())
        async for event in lang_graph._graph.astream_events({"messages": [_input]}, version="v2", config=config):
            if copilot_stream_debug:
                yield AssistantResponse(
                    response=str(event), conversation_id=question.conversation_id, role="debug"
                )
            kind = event["event"]
            if kind == "on_chain_start":
                if event.get("metadata") != None:
                    metadata = event["metadata"]
                    if metadata.get("langgraph_node") != None:
                        if event.get("tags") != None:
                            graph_step = False
                            for tag in event["tags"]:
                                if tag.startswith("graph:step") and tag != 'graph:step:0':
                                    graph_step = True
                            if graph_step:
                                node = metadata["langgraph_node"]
                                if node.startswith("supervisor-stage"):
                                    message = "Thinking what to do next ..."
                                else:
                                    if node == "output":
                                        message = "Got it! Writing the answer ..."
                                    else:
                                        message = "Asking for this to the agent '" + node + "'"
                                yield AssistantResponse(
                                    response=message, conversation_id=question.conversation_id, role="node"
                                )
            if kind == "on_tool_start":
                if len(event["parent_ids"]) == 1:
                    yield AssistantResponse(
                        response=event["name"], conversation_id=question.conversation_id, role="tool"
                    )
            elif kind == "on_chain_end":
                if type(event["data"]["output"]) == list:
                    pass
                else:
                    if type(event["data"]["output"]) == OpenAIAssistantAction:
                        pass
                    else:
                        if not type(event["data"]["output"]) == AddableDict:
                            output = event["data"]["output"]
                            if type(output) == AgentFinish:
                                if event.get("metadata") != None and event["metadata"].get("langgraph_node") != None and event["metadata"]["langgraph_node"] == "output":
                                    return_values = output.return_values
                                    yield AssistantResponse(
                                        response=str(return_values["output"]), conversation_id=""
                                    )
