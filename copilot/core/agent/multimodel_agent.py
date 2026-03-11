import asyncio
import os
from typing import AsyncGenerator, Final, Union

import langchain_core.tools
from copilot.baseutils.logging_envvar import (
    read_optional_env_var,
)
from copilot.core.agent.agent import (
    AgentResponse,
    AssistantResponse,
    CopilotAgent,
)
from copilot.core.agent.agent_utils import (
    build_metadata,
    get_checkpoint_file,
    normalize_content,
    process_local_files,
)
from copilot.core.agent.codeact import create_default_prompt
from copilot.core.agent.langgraph_agent import build_config, handle_events
from copilot.core.memory.memory_handler import MemoryHandler
from copilot.core.schemas import AssistantSchema, QuestionSchema, ToolSchema
from copilot.core.threadcontextutils import (
    read_accum_usage_data_from_msg_arr,
)
from copilot.core.utils.agent import get_full_question, get_llm, get_structured_output
from langchain.agents import create_agent
from langchain_classic.agents import AgentOutputParser
from langchain_core.agents import AgentAction, AgentFinish
from langchain_core.messages import HumanMessage
from langchain_core.prompts.chat import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import StructuredTool
from langchain_mcp_adapters.client import MultiServerMCPClient
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver

SYSTEM_PROMPT_PLACEHOLDER = "{system_prompt}"
tools_loaded = {}


def convert_mcp_servers_config(mcp_servers_list: list) -> dict:
    """
    Convert MCP servers list from QuestionSchema to the format expected by MultiServerMCPClient.

    Args:
        mcp_servers_list (list): List of MCP server configurations from the question

    Returns:
        dict: Dictionary in the format expected by MultiServerMCPClient
    """
    if not mcp_servers_list:
        return {}

    mcp_config = {}
    for server_config in mcp_servers_list:
        server_name = server_config.get("name", f"server_{len(mcp_config)}")

        if server_config.get("disabled", False):
            continue

        # Create a copy of the server config without the 'name' field
        config_copy = {k: v for k, v in server_config.items() if k != "name"}

        # Set default transport to stdio if not provided
        if "transport" not in config_copy:
            config_copy["transport"] = "stdio"

        mcp_config[server_name] = config_copy

    return mcp_config


class CustomOutputParser(AgentOutputParser):
    def parse(self, output) -> Union[AgentAction, AgentFinish]:
        final_answer = output
        agent_finish = AgentFinish(
            return_values={"output": final_answer},
            log=output,
        )
        return agent_finish


def is_code_act_enabled(agent_configuration: AssistantSchema) -> bool:
    """
    Determines if the CodeAct feature is enabled for the given agent configuration.

    Args:
        agent_configuration (AssistantSchema): The configuration object for the agent,
            which includes various settings and features.

    Returns:
        bool: True if the `code_execution` attribute exists in the `agent_configuration`
        and is set to True, otherwise False.
    """
    return agent_configuration.code_execution


def _resolve_ref(obj, defs):
    """Inline $ref references from $defs."""
    if not isinstance(obj, dict) or "$ref" not in obj or not defs:
        return obj
    ref_name = obj["$ref"].rsplit("/", 1)[-1]
    if ref_name not in defs:
        return obj
    resolved = defs[ref_name].copy()
    for k, v in obj.items():
        if k != "$ref":
            resolved.setdefault(k, v)
    return _resolve_ref(resolved, defs)


def _resolve_anyof(schema, defs):
    """Resolve anyOf by picking the first non-null alternative (Gemini doesn't support anyOf)."""
    if "anyOf" not in schema:
        return schema
    alternatives = schema.pop("anyOf")
    chosen = next(
        (_resolve_ref(alt, defs) for alt in alternatives if _resolve_ref(alt, defs).get("type") != "null"),
        None,
    )
    if not chosen:
        return schema
    parent_desc = schema.get("description")
    parent_title = schema.get("title")
    schema.update(chosen)
    if parent_desc and "description" not in chosen:
        schema["description"] = parent_desc
    if parent_title and "title" not in chosen:
        schema["title"] = parent_title
    return schema


def _fix_type_defaults(schema):
    """Add missing 'items' to arrays and missing 'properties' to objects."""
    if schema.get("type") == "array" and "items" not in schema:
        schema["items"] = {"type": "string"}
    if schema.get("type") == "object" and "properties" not in schema:
        schema["properties"] = {}
    return schema


def _fix_nested_schemas(schema):
    """Recursively apply _fix_array_schemas to nested properties, items, and additionalProperties."""
    if "properties" in schema:
        schema["properties"] = {k: _fix_array_schemas(v) for k, v in schema["properties"].items()}
    if "items" in schema:
        schema["items"] = _fix_array_schemas(schema["items"])
    if isinstance(schema.get("additionalProperties"), dict):
        schema["additionalProperties"] = _fix_array_schemas(schema["additionalProperties"])
    return schema


def _fix_array_schemas(schema):
    """
    Recursively fix schemas for provider compatibility (especially Gemini).
    Resolves $ref/anyOf patterns and ensures arrays have items and objects have properties.
    """
    if not isinstance(schema, dict):
        return schema
    fixed = schema.copy()
    defs = fixed.pop("$defs", None) or schema.get("$defs")
    fixed = _resolve_ref(fixed, defs)
    fixed = _resolve_anyof(fixed, defs)
    fixed = _fix_type_defaults(fixed)
    fixed = _fix_nested_schemas(fixed)
    return fixed


def fix_tool_schemas(tools):
    """Fix tool schemas for provider compatibility (especially Gemini).

    For tools with a Pydantic args_schema, overrides model_json_schema() to return
    a fixed version without anyOf/$ref/$defs that Gemini cannot handle.
    For tools with a dict args_schema, fixes the dict directly.
    """
    from pydantic import BaseModel

    for tool in tools:
        schema_cls = getattr(tool, "args_schema", None)
        if schema_cls is None:
            continue
        if isinstance(schema_cls, dict):
            tool.args_schema = _fix_array_schemas(schema_cls)
        elif isinstance(schema_cls, type) and issubclass(schema_cls, BaseModel):
            original_schema = schema_cls.model_json_schema()
            fixed_schema = _fix_array_schemas(original_schema)
            if fixed_schema != original_schema:
                # Subclass to override JSON schema generation without affecting validation
                patched_cls = type(schema_cls.__name__, (schema_cls,), {})
                patched_cls.model_json_schema = classmethod(lambda cls, _fs=fixed_schema, **kw: _fs)
                tool.args_schema = patched_cls


async def get_mcp_tools(mcp_servers_config: dict = None) -> list:
    """
    Get MCP tools from configured MCP servers.

    Args:
        mcp_servers_config (dict): MCP servers configuration

    Returns:
        list: List of MCP tools from servers
    """
    if not mcp_servers_config:
        return []

    # Create new MCP client for each request
    try:
        client = MultiServerMCPClient(mcp_servers_config)
        # Get tools with timeout
        tools = await asyncio.wait_for(client.get_tools(), timeout=45.0)

        # Fix schema for tools with validation issues
        for tool in tools:
            if hasattr(tool, "args_schema"):
                schema = tool.args_schema
                if schema is None:
                    # Set empty schema for tools without parameters
                    tool.args_schema = {"type": "object", "properties": {}}
                elif isinstance(schema, dict):
                    # Fix missing properties for object schemas
                    if schema.get("type") == "object" and "properties" not in schema:
                        schema["properties"] = {}
                    # Fix missing items for array schemas recursively
                    tool.args_schema = _fix_array_schemas(schema)

        return tools

    except Exception as e:
        print(f"Failed to create MCP client: {e}")
        print(f"Error type: {type(e).__name__}")
        return []


async def acheckpointer_has_thread(checkpointer, config):
    """
    Asynchronously checks if a thread exists in the checkpointer for the given configuration.

    Args:
        checkpointer: The checkpointer instance (e.g., AsyncSqliteSaver) to query.
        config: The configuration object for the thread.

    Returns:
        bool: True if thread data exists, False otherwise.
    """
    thread_data = await checkpointer.aget(config=config)
    return thread_data is not None  # if not none, thread exists


def checkpointer_has_thread(checkpointer, config):
    """
    Synchronously checks if a thread exists in the checkpointer for the given configuration.

    Args:
        checkpointer: The checkpointer instance (e.g., SqliteSaver) to query.
        config: The configuration object for the thread.

    Returns:
        bool: True if thread data exists, False otherwise.
    """
    thread_data = checkpointer.get(config=config)
    return thread_data is not None  # if not none, thread exists


class MultimodelAgent(CopilotAgent):
    OPENAI_MODEL: Final[str] = read_optional_env_var("OPENAI_MODEL", "gpt-4o")
    _memory: MemoryHandler = None

    def __init__(self):
        super().__init__()
        self._memory = MemoryHandler()

    def _create_code_act_agent(self, llm, enabled_tools: list, system_prompt: str):
        """Create a CodeAct agent with the specified configuration."""
        conv_tools = [
            t if isinstance(t, StructuredTool) else langchain_core.tools.convert_runnable_to_tool(t)
            for t in enabled_tools
        ]
        from copilot.core.agent.eval.code_evaluators import (
            CodeExecutor,
        )

        eval_fn = CodeExecutor("original").execute
        from copilot.core.agent.codeact import create_codeact

        return create_codeact(
            model=llm,
            tools=conv_tools,
            eval_fn=eval_fn,
            prompt=create_default_prompt(tools=conv_tools, base_prompt=system_prompt),
        )

    def get_agent(
        self,
        provider: str,
        model: str,
        agent_configuration: AssistantSchema,
        tools: list[ToolSchema] = None,
        system_prompt: str = None,
        temperature: float = 1,
        mcp_tools: list = None,
        checkpointer=None,
    ):
        """Construct and return an agent from scratch, using LangChain Expression Language.

        Raises:
            OpenAIApiKeyNotFound: raised when OPENAI_API_KEY is not configured
            SystemPromptNotFound: raised when SYSTEM_PROMPT is not configured
        """
        self._assert_system_prompt_is_set()
        llm = get_llm(model, provider, temperature)

        # Use the unified tool loader to get all tools
        from copilot.core.tool_loader import ToolLoader

        tool_loader = ToolLoader()
        _enabled_tools = tool_loader.get_all_tools(
            agent_configuration=agent_configuration,
            enabled_tools=tools,
            include_kb_tool=True,
            include_openapi_tools=True,
        )
        if mcp_tools is not None:
            _enabled_tools.extend(mcp_tools or [])

        # Fix tool schemas for provider compatibility (e.g. Gemini requires items on arrays,
        # does not support anyOf/$ref). Applied to ALL tools, not just MCP tools.
        fix_tool_schemas(_enabled_tools)

        # Replace the instance configured tools with the complete list
        self._configured_tools = _enabled_tools
        tools_loaded[agent_configuration.assistant_id] = _enabled_tools

        prompt_structure = [
            ("system", SYSTEM_PROMPT_PLACEHOLDER if system_prompt is None else system_prompt),
            MessagesPlaceholder(variable_name="messages"),
            ("placeholder", "{agent_scratchpad}"),
        ]

        ChatPromptTemplate.from_messages(prompt_structure)

        if is_code_act_enabled(agent_configuration):
            agent = self._create_code_act_agent(llm, _enabled_tools, system_prompt)
            agent = agent.compile(checkpointer=checkpointer)
            agent.get_graph().print_ascii()
        else:
            agent = create_agent(
                model=llm,
                tools=_enabled_tools,
                system_prompt=system_prompt,
                checkpointer=checkpointer,
                response_format=get_structured_output(agent_configuration),
            )
        return agent

    def execute(self, question: QuestionSchema) -> AgentResponse:
        with SqliteSaver.from_conn_string(get_checkpoint_file(question.assistant_id)) as checkpointer:
            full_question = get_full_question(question)
            agent = self.get_agent(
                provider=question.provider,
                model=question.model,
                agent_configuration=question,
                tools=question.tools,
                system_prompt=question.system_prompt,
                temperature=question.temperature,
                checkpointer=checkpointer,
            )

            # Process local files
            image_payloads, other_file_paths = process_local_files(question.local_file_ids)

            config = build_config(question.conversation_id)
            # Construct messages array using the unified method
            has_thread = checkpointer_has_thread(checkpointer, config)
            messages = self.get_messages_array(
                full_question, image_payloads, other_file_paths, question, has_thread
            )

            agent_response = agent.invoke(
                {"system_prompt": question.system_prompt, "messages": messages}, config=config
            )
            new_ai_message = agent_response.get("messages")[-1]
            usage_data = read_accum_usage_data_from_msg_arr(agent_response.get("messages"))
            return AgentResponse(
                input=full_question,
                output=AssistantResponse(
                    response=normalize_content(new_ai_message.content),
                    conversation_id=question.conversation_id,
                    metadata=build_metadata(usage_data),
                ),
            )

    def get_tools(self):
        return self._configured_tools

    async def aexecute(self, question: QuestionSchema) -> AsyncGenerator[AgentResponse, None]:
        copilot_stream_debug = os.getenv("COPILOT_STREAM_DEBUG", "false").lower() == "true"  # Debug mode
        async with AsyncSqliteSaver.from_conn_string(
            get_checkpoint_file(question.assistant_id)
        ) as checkpointer:
            mcp_servers_config = convert_mcp_servers_config(question.mcp_servers or [])
            # Get MCP tools asynchronously
            mcp_tools = await get_mcp_tools(mcp_servers_config)

            # Use async agent creation to include MCP tools
            agent = self.get_agent(
                provider=question.provider,
                model=question.model,
                agent_configuration=question,
                tools=question.tools,
                system_prompt=question.system_prompt,
                temperature=question.temperature,
                mcp_tools=mcp_tools,
                checkpointer=checkpointer,
            )

            full_question = get_full_question(question)

            # Process local files
            image_payloads, other_file_paths = process_local_files(question.local_file_ids)

            config = build_config(question.conversation_id)

            # Construct messages array. If the checkpointer has the thread history, only
            # the new question is added to the messages.
            has_thread = await acheckpointer_has_thread(checkpointer, config)
            messages = self.get_messages_array(
                full_question, image_payloads, other_file_paths, question, has_thread
            )

            _input = {
                "content": full_question,
                "messages": messages,
                "system_prompt": question.system_prompt,
                "thread_id": question.conversation_id,
            }
            try:
                async for event in agent.astream_events(_input, config=config, version="v2"):
                    response = await handle_events(copilot_stream_debug, event, question.conversation_id)
                    if response is not None:
                        yield response
                return
            except Exception as e:
                yield AssistantResponse(
                    response=str(e), conversation_id=question.conversation_id, role="error"
                )

    def get_messages_array(self, full_question, image_payloads, other_file_paths, question, has_thread):
        """
        Constructs the array of messages for the agent based on thread status and attachments.

        Args:
            full_question (str): The full text of the user's question.
            image_payloads (list): List of image payloads to include in the message.
            other_file_paths (list): List of paths to other attached files.
            question (QuestionSchema): The question object containing history and other data.
            has_thread (bool): True if a thread exists in the checkpointer, False otherwise.

        Returns:
            list: A list of message objects (HumanMessage, AIMessage) ready for
            the agent.
                 - If has_thread is True: Only includes the new question with
                 attachments.
                 - If has_thread is False: Includes conversation history + new
                 question with attachments.
        """
        if has_thread:
            messages = []
        else:
            messages = self._memory.get_memory(question.history, None)  # Only load if no thread
        content = [{"type": "text", "text": full_question}]
        if image_payloads:
            content.extend(image_payloads)
        if other_file_paths:
            content.append({"type": "text", "text": "Attached files:\n" + "\n".join(other_file_paths)})
        new_human_message = HumanMessage(content=content)
        messages.append(new_human_message)
        return messages
