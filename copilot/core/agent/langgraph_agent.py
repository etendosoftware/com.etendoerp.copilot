import uuid
from typing import AsyncGenerator

from copilot.baseutils.logging_envvar import (
    copilot_debug,
    copilot_debug_event,
    read_optional_env_var,
    read_optional_env_var_int,
)
from copilot.core.agent.agent import AgentResponse, AssistantResponse, CopilotAgent
from copilot.core.agent.agent_utils import (
    build_metadata,
    get_checkpoint_file,
    process_local_files,
)
from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.langgraph.patterns.langsupervisor_pattern import LangSupervisorPattern
from copilot.core.memory.memory_handler import MemoryHandler
from copilot.core.schema.graph_member import GraphMember
from copilot.core.schemas import GraphQuestionSchema
from copilot.core.threadcontextutils import (
    read_accum_usage_data_from_msg_arr,
)
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from langgraph.graph.state import CompiledStateGraph

SQLLITE_STORE_NAME = "store.sqlite"


def fullfill_question(local_file_ids, question):
    """
    Constructs the full question by appending local file IDs to the original question.

    Args:
        local_file_ids (list): A list of local file IDs to be appended to the question.
        question (GraphQuestionSchema): The question schema containing the original question.

    Returns:
        str: The full question with local file IDs appended.
    """
    full_question = question.question
    if local_file_ids is not None and len(local_file_ids) > 0:
        full_question += "\n\n" + "LOCAL FILES: " + "\n".join(local_file_ids)
    return full_question


def setup_graph(question, memory, store):
    """
    Sets up the language graph for processing the given question.

    Args:
        question (GraphQuestionSchema): The question schema containing the details of the question.
        memory (SqliteSaver): The memory saver instance to be used for the graph.

    Returns:
        tuple: A tuple containing the language graph and the thread ID associated with the question.
    """
    thread_id = question.conversation_id
    members: list[GraphMember] = MembersUtil().get_members(question)
    lang_graph = LangSupervisorPattern(
        members, question.graph, None, memory=memory, store=store, full_question=question
    )
    return lang_graph, thread_id


def get_subgraph_name(metadata) -> str:
    """
    Extracts the subgraph name from the metadata.
    Args:
        metadata: dict: The metadata containing information about the checkpoint namespace.

    Returns:
        str: The extracted subgraph name.
    """
    try:
        if "checkpoint_ns" in metadata:
            subgraph_name = metadata["checkpoint_ns"]
        elif "langgraph_checkpoint_ns" in metadata:
            subgraph_name = metadata["langgraph_checkpoint_ns"]
        else:
            subgraph_name = " : "

        subgraph_name = subgraph_name.split(":")[0]
        if subgraph_name == "model" or subgraph_name == "tools":
            subgraph_name = "The agent"
        return subgraph_name
    except Exception as e:
        copilot_debug(f"Error extracting subgraph name: {str(e)}")
        return " "


async def _handle_on_chain_end(event, thread_id):
    """
    Handles the 'on_chain_end' event.

    Args:
        event (dict): The event data containing information about the chain end.
        thread_id (str): The ID of the thread associated with the event.

    Returns:
        AssistantResponse or None: The response generated from the event, or None if no response is generated.
    """
    response = None
    output = event["data"]["output"]
    messages = output.get("messages", [])
    usage_data = read_accum_usage_data_from_msg_arr(messages)
    if len(event["parent_ids"]) != 0:
        return None
    if messages:
        message = messages[-1]
        if isinstance(message, (HumanMessage, AIMessage)):
            response = AssistantResponse(
                response=message.content, conversation_id=thread_id, metadata=build_metadata(usage_data)
            )
    if output.get("structured_response"):
        response = AssistantResponse(
            response=output["structured_response"],
            conversation_id=thread_id,
            metadata=build_metadata(usage_data),
        )
    return response


async def _handle_on_tool_start(event, thread_id):
    """
    Handles the 'on_tool_start' event.

    Args:
        event (dict): The event data containing information about the tool start.
        thread_id (str): The ID of the thread associated with the event.

    Returns:
        AssistantResponse or None: The response generated from the event, or None if no response is generated.
    """
    if len(event["parent_ids"]) == 1:
        return AssistantResponse(response=event["name"], conversation_id=thread_id, role="tool")
    return None


async def _handle_on_chain_start(event, thread_id):
    """
    Handles the 'on_chain_start' event.

    Args:
        event (dict): The event data containing information about the chain start.
        thread_id (str): The ID of the thread associated with the event.

    Returns:
        AssistantResponse or None: The response generated from the event, or None if no response is generated.
    """
    response = None
    metadata = event.get("metadata")
    if metadata and metadata.get("langgraph_node") and event.get("tags"):
        graph_step = any(tag.startswith("graph:step") and tag != "graph:step:0" for tag in event["tags"])
        if graph_step:
            node = metadata["langgraph_node"]
            subgraph_name = get_subgraph_name(metadata)
            message = get_message(event, node, subgraph_name)
            response = AssistantResponse(response=message, conversation_id=thread_id, role="node")
    return response


def get_message(event, node, subgraph_name):
    if node.startswith("__start__"):
        message = "Starting..."
    elif node.startswith("agent") or node.startswith("model"):
        message = f"{subgraph_name} is thinking..."
    elif node.startswith("tool"):
        try:
            tool_name = event["data"]["input"]["tool_call"]["name"]
            message = f"{subgraph_name} is using the tool '{tool_name}'..."
        except Exception:
            message = f"{subgraph_name} is using his tools..."
    elif node == "output":
        message = "Got it! Writing the answer ..."
    else:
        message = f"Asking for this to the agent '{subgraph_name}/{node}'"
    return message


async def handle_events(copilot_stream_debug, event, thread_id):
    """
    Handles various types of events and delegates them to specific handlers.

    Args:
        copilot_stream_debug (bool): Indicates whether debug mode is enabled.
        event (dict): The event data containing information about the event.
        thread_id (str): The ID of the thread associated with the event.

    Returns:
        AssistantResponse or None: The response generated from the event, or None if no response is generated.
    """
    response = None
    try:
        if copilot_stream_debug:
            response = AssistantResponse(response=str(event), conversation_id=thread_id, role="debug")
        copilot_debug_event(f"Event: {str(event)}")
        kind = event["event"]

        if kind == "on_chain_start":
            response = await _handle_on_chain_start(event, thread_id)
        elif kind == "on_tool_start":
            response = await _handle_on_tool_start(event, thread_id)
        elif kind == "on_chain_end":
            response = await _handle_on_chain_end(event, thread_id)
        else:
            copilot_debug_event(f"Event kind not recognized: {kind}")
    except Exception as e:
        copilot_debug(f"Error in event processing: {str(e)}")
    return response


def build_msg_input(full_question, image_payloads=None, other_file_paths=None):
    if image_payloads or other_file_paths:
        content = [{"type": "text", "text": full_question}]
        if image_payloads:
            content.extend(image_payloads)
        if other_file_paths:
            # Attach non-image files as a text block with file paths
            content.append({"type": "text", "text": "Attached files:\n" + "\n".join(other_file_paths)})
        new_human_message = HumanMessage(content=content)
        return {"messages": [new_human_message]}
    return {"messages": [HumanMessage(content=full_question)]}


def build_config(thread_id):
    config = {
        "configurable": {},
        "recursion_limit": read_optional_env_var_int("LANGGRAPH_RECURSION_LIMIT", 500),
        "max_iterations": 100,
    }
    if thread_id is not None:
        config["configurable"]["thread_id"] = thread_id
    else:
        config["configurable"]["thread_id"] = str(uuid.uuid4())
    return config


class LanggraphAgent(CopilotAgent):
    _memory: MemoryHandler = None

    def __init__(self):
        """Initializes the LanggraphAgent instance and sets up the memory handler."""
        super().__init__()
        self._memory = MemoryHandler()

    # The agent state is the input to each node in the graph

    def execute(self, question: GraphQuestionSchema) -> AgentResponse:
        """
        Executes the agent synchronously to process the given question.

        Args:
            question (GraphQuestionSchema): The question schema containing the details of the question.

        Returns:
            AgentResponse: The response generated by the agent.
        """
        _tools = self._configured_tools
        with SqliteSaver.from_conn_string(get_checkpoint_file(question.assistant_id)) as memory:
            with SqliteSaver.from_conn_string(SQLLITE_STORE_NAME) as store:
                lang_graph, thread_id = setup_graph(question, memory, store)
                local_file_ids = question.local_file_ids
                # Process local files
                image_payloads, other_file_paths = process_local_files(question.local_file_ids)

                full_question = fullfill_question(local_file_ids, question)
                cgraph: CompiledStateGraph = lang_graph._graph
                if question.generate_image:
                    import base64
                    import time

                    start_time = time.time()
                    png = cgraph.get_graph().draw_mermaid_png()
                    end_time = time.time()
                    print(f"Time taken to draw PNG: {end_time - start_time} seconds")
                    return AgentResponse(
                        input=question.model_dump_json(),
                        output=AssistantResponse(
                            response=base64.b64encode(png).decode(),
                            conversation_id=thread_id,
                        ),
                    )

                agent_response = cgraph.invoke(
                    input=build_msg_input(full_question, image_payloads, other_file_paths),
                    config=build_config(thread_id),
                )

                messages = agent_response.get("messages")
                usage_data = read_accum_usage_data_from_msg_arr(messages)
                if agent_response.get("structured_response"):
                    new_ai_message = agent_response.get("structured_response")
                else:
                    new_ai_message = messages[-1].content
                return AgentResponse(
                    input=question.model_dump_json(),
                    output=AssistantResponse(
                        response=new_ai_message,
                        conversation_id=thread_id,
                        metadata=build_metadata(usage_data),
                    ),
                )

    async def aexecute(self, question: GraphQuestionSchema) -> AsyncGenerator[AgentResponse, None]:
        """
        Executes the agent asynchronously to process the given question.

        Args:
            question (GraphQuestionSchema): The question schema containing the details of the question.

        Yields:
            AgentResponse: The response generated by the agent.
        """
        copilot_stream_debug = (
            read_optional_env_var("COPILOT_STREAM_DEBUG", "false").lower() == "true"
        )  # Debug mode
        async with AsyncSqliteSaver.from_conn_string(get_checkpoint_file(question.assistant_id)) as memory:
            async with AsyncSqliteSaver.from_conn_string(SQLLITE_STORE_NAME) as store:
                lang_graph, thread_id = setup_graph(question, memory, store)
                local_file_ids = question.local_file_ids
                full_question = fullfill_question(local_file_ids, question)
                # Process local files
                image_payloads, other_file_paths = process_local_files(question.local_file_ids)
                async for event in lang_graph._graph.astream_events(
                    build_msg_input(full_question, image_payloads, other_file_paths),
                    version="v2",
                    config=build_config(thread_id),
                ):
                    response = await handle_events(copilot_stream_debug, event, thread_id)
                    if response is not None:
                        yield response
