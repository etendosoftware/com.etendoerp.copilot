import uuid

from langchain_core.messages import HumanMessage, AIMessage
from langgraph.checkpoint.aiosqlite import AsyncSqliteSaver
from langgraph.checkpoint.sqlite import SqliteSaver
from langsmith import traceable

from .agent import AgentResponse, CopilotAgent
from .agent import AssistantResponse
from ..langgraph.copilot_langgraph import CopilotLangGraph
from ..langgraph.members_util import MembersUtil
from ..langgraph.patterns import SupervisorPattern
from ..langgraph.patterns.base_pattern import GraphMember
from ..memory.memory_handler import MemoryHandler
from ..schemas import GraphQuestionSchema
from ..utils import read_optional_env_var, read_optional_env_var_int, copilot_debug, copilot_debug_event


class LanggraphAgent(CopilotAgent):
    _memory: MemoryHandler = None

    @traceable
    def __init__(self):
        super().__init__()
        self._memory = MemoryHandler()

    # The agent state is the input to each node in the graph
    @traceable
    def execute(self, question: GraphQuestionSchema) -> AgentResponse:
        thread_id = question.conversation_id
        _tools = self._configured_tools

        members: list[GraphMember] = MembersUtil().get_members(question)

        memory = SqliteSaver.from_conn_string("checkpoints.sqlite")
        lang_graph = CopilotLangGraph(members, question.graph, SupervisorPattern(), memory=memory)
        full_question = question.question
        if question.local_file_ids is not None and len(question.local_file_ids) > 0:
            full_question += "\n\n" + "LOCAL FILES: " + "\n".join(question.local_file_ids)

        final_response = lang_graph.invoke(question=full_question, thread_id=thread_id,
                                           get_image=question.generate_image)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=final_response, conversation_id=thread_id
            )
        )

    @traceable
    async def aexecute(self, question: GraphQuestionSchema) -> AgentResponse:
        copilot_stream_debug = read_optional_env_var("COPILOT_STREAM_DEBUG", "false").lower() == "true"  # Debug mode
        members: list[GraphMember] = MembersUtil().get_members(question)
        memory = AsyncSqliteSaver.from_conn_string("checkpoints.sqlite")
        lang_graph = CopilotLangGraph(members, question.graph, SupervisorPattern(), memory=memory,
                                      full_question=question)

        full_question = question.question
        if question.local_file_ids is not None and len(question.local_file_ids) > 0:
            full_question += "\n\n" + "LOCAL FILES: " + "\n".join(question.local_file_ids)
        _input = HumanMessage(content=full_question)
        config = {
            "configurable": {
            },
            "recursion_limit": read_optional_env_var_int("LANGGRAPH_RECURSION_LIMIT", 50)
        }

        if question.conversation_id is not None:
            config["configurable"]["thread_id"] = question.conversation_id
        else:
            config["configurable"]["thread_id"] = str(uuid.uuid4())
        async for event in lang_graph._graph.astream_events({"messages": [_input]}, version="v2", config=config):
            try:
                if copilot_stream_debug:
                    yield AssistantResponse(
                        response=str(event), conversation_id=question.conversation_id, role="debug"
                    )
                copilot_debug_event(f"Event: {str(event)}")
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
                                    elif node == "output":
                                        message = "Got it! Writing the answer ..."
                                    else:
                                        message = "Asking for this to the agent '" + node + "'"
                                    yield AssistantResponse(
                                        response=message, conversation_id=question.conversation_id, role="node"
                                    )
                elif kind == "on_tool_start":
                    if len(event["parent_ids"]) == 1:
                        yield AssistantResponse(
                            response=event["name"], conversation_id=question.conversation_id, role="tool"
                        )
                elif kind == "on_chain_end":
                    if len(event["parent_ids"]) == 0:
                        output = event["data"]["output"]
                        if output.get("messages") != None and len(output["messages"]) > 0:
                            message = output["messages"][-1]
                            if type(message) == HumanMessage or type(message) == AIMessage:
                                yield AssistantResponse(
                                    response=message.content, conversation_id=question.conversation_id
                                )
                else:
                    copilot_debug_event(f"Event kind not recognized: {kind}")
            except Exception as e:
                copilot_debug(f"Error in event processing: {str(e)}")
