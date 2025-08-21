"""
Agent-specific tools for MCP server.

This module contains tools that are dynamically loaded from Etendo Classic
based on the agent configuration and provides tools specific to each agent.
"""

import inspect
import logging
import re
from typing import List, Optional, Type

import httpx
from copilot.baseutils.logging_envvar import copilot_debug, copilot_error, copilot_info
from copilot.core.mcp.auth_utils import extract_etendo_token_from_mcp_context
from copilot.core.schemas import AssistantSchema
from copilot.core.tool_loader import ToolLoader
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils.etendo_utils import (
    call_etendo,
    get_etendo_host,
    get_etendo_token,
    normalize_etendo_token,
    validate_etendo_token,
)
from fastmcp.server.dependencies import get_context
from fastmcp.tools.tool import Tool
from langchain_core.tools import BaseTool
from pydantic import BaseModel

logger = logging.getLogger(__name__)

# Constants
APPLICATION_JSON = "application/json"
ERROR_NO_AGENT_ID = "No agent identifier available"
ERROR_NO_TOKEN = "No authentication token available"


def get_agent_identifier() -> Optional[str]:
    """Get the agent identifier from the MCP server context."""
    try:
        context = get_context()
        return context.fastmcp._mcp_server.version
    except Exception as e:
        logger.error(f"Error getting agent identifier: {e}")
        return None


def fetch_agent_structure_from_etendo(identifier: str, etendo_token: str) -> Optional[AssistantSchema]:
    """
    Fetch agent structure from Etendo Classic endpoint.

    Args:
        identifier: The agent identifier (app_id)
        etendo_token: Bearer token for authentication

    Returns:
        AssistantSchema: Agent configuration or None if error
    """
    try:
        etendo_host = get_etendo_host()
        url = f"{etendo_host}/sws/copilot/structure"

        copilot_debug(f"Fetching agent structure from {url} with app_id={identifier}")

        response: dict = call_etendo(
            url=etendo_host,
            endpoint="/sws/copilot/structure?app_id=" + identifier,
            method="GET",
            body_params={},
            access_token=etendo_token,
        )
        return AssistantSchema(**response)

    except httpx.HTTPStatusError as e:
        copilot_error(f"HTTP error fetching agent structure: {e}")


def _get_param_type_annotation(field_info) -> str:
    """Extract type annotation from field info."""
    try:
        if hasattr(field_info, "annotation") and field_info.annotation:
            if hasattr(field_info.annotation, "__name__") and (
                field_info.annotation.__name__ not in ("Union", "Optional")
            ):
                return field_info.annotation.__name__
            else:
                return "Any"
    except (AttributeError, TypeError):
        pass
    return "str"


def _build_param_definition(field_name: str, field_info) -> str:
    """Build parameter definition string for function signature."""
    type_annotation = _get_param_type_annotation(field_info)

    if field_info.is_required():
        return f"{field_name}: {type_annotation}"

    default_val = getattr(field_info, "default", None)
    if default_val is not None:
        if isinstance(default_val, str):
            return f"{field_name}: {type_annotation} = '{default_val}'"
        else:
            return f"{field_name}: {type_annotation} = {default_val}"
    else:
        return f"{field_name}: {type_annotation} = None"


def _create_langchain_tool_executor(langchain_tool: BaseTool, unify_arguments: bool = False):
    """
    Generates a dynamic 'tool_executor' for a LangChain BaseTool.
    The executor can accept arguments as individual parameters or as a unified object.

    Args:
        langchain_tool: An instance of LangChain's BaseTool.
        unify_arguments: If True, creates a function that takes a single 'args' object.
                        If False, creates a function with individual parameters.

    Returns:
        A function that serves as the tool's executor, receiving
        the tool's arguments either individually or as a unified object.
    """
    # The LangChain tool's argument schema is a Pydantic BaseModel
    tool_args_model: Type[BaseModel] = langchain_tool.args_schema or BaseModel

    # If the tool doesn't have an explicit args_schema, create a simple function
    if tool_args_model is BaseModel:

        def simple_tool_executor(input: str = ""):
            """Simple executor for tools without an explicit Pydantic schema."""
            copilot_debug(f"Executing tool '{langchain_tool.name}' with input: {input}")
            return langchain_tool.run(input)

        return simple_tool_executor

    # Generate function code based on unify_arguments parameter
    param_names = list(tool_args_model.model_fields.keys())

    # Create function with individual parameters (original behavior)
    param_definitions = []
    required_params = []
    optional_params = []

    # Separate required and optional parameters to ensure correct order
    for field_name, field_info in tool_args_model.model_fields.items():
        param_def = _build_param_definition(field_name, field_info)
        if field_info.is_required():
            required_params.append(param_def)
        else:
            optional_params.append(param_def)

    # Combine with required parameters first, then optional ones
    param_definitions = required_params + optional_params
    param_string = ", ".join(param_definitions)
    copilot_debug(
        f"Creating individual parameters executor for tool '{langchain_tool.name}' with params: {param_names}"
    )

    function_code = f'''
from typing import Dict, Any
def dynamic_tool_executor({param_string}):
    """Execute {langchain_tool.name} with specific parameters."""
    # Collect arguments into dictionary
    input_dict = {{
    {",".join([f"        '{param}': {param}" for param in param_names])}
    }}
    print(f"Executing tool '{langchain_tool.name}' with input: {{input_dict}}")
    # Filter out None values
    cleaned_input = {{k: v for k, v in input_dict.items() if v is not None}}

    print(f"Cleaned input for tool '{langchain_tool.name}': {{cleaned_input}}")

    # Execute the tool
    return tool_instance.run(cleaned_input)
    '''

    # Execute the function code
    namespace = {
        "tool_instance": langchain_tool,
        "copilot_debug": copilot_debug,
        "copilot_error": copilot_error,
    }

    exec(function_code, namespace)

    # Get the created function
    executor = namespace["dynamic_tool_executor"]
    executor.__name__ = f"execute_{langchain_tool.name}"

    # Update docstring based on mode
    mode_desc = "unified arguments object" if unify_arguments else "specific parameters"
    executor.__doc__ = f"Execute {langchain_tool.name} tool with {mode_desc}"

    return executor


async def _execute_langchain_tool(langchain_tool: BaseTool, kwargs: dict):
    """Execute a LangChain tool using the most appropriate method."""
    if hasattr(langchain_tool, "run"):
        return langchain_tool.run(tool_input=kwargs)
    elif hasattr(langchain_tool, "ainvoke"):
        return await langchain_tool.ainvoke(kwargs)
    elif hasattr(langchain_tool, "arun"):
        return await langchain_tool.arun(**kwargs)
    elif hasattr(langchain_tool, "invoke"):
        return langchain_tool.invoke(kwargs)
    else:
        return langchain_tool(**kwargs)


def has_kwargs(tool):
    sig = inspect.signature(tool._run)
    # Reject functions with *args or **kwargs
    for param in sig.parameters.values():
        if param.kind == inspect.Parameter.VAR_POSITIONAL:
            raise ValueError("Functions with *args are not supported as tools")
        if param.kind == inspect.Parameter.VAR_KEYWORD:
            raise ValueError("Functions with **kwargs are not supported as tools")


def _convert_single_tool_to_mcp(tool: BaseTool) -> Tool:
    """Convert a single LangChain tool to FastMCP Tool."""
    from fastmcp.tools import Tool

    try:
        copilot_debug(f"Converting LangChain tool to MCP format: {tool.name}")

        # Determine how to handle arguments based on tool type
        if isinstance(tool, ToolWrapper):
            copilot_debug(f"Tool {tool.name} is a ToolWrapper - using unified arguments")
            toolfn_conv = _create_langchain_tool_executor(tool, unify_arguments=True)
        elif isinstance(tool, BaseTool):
            copilot_debug(f"Tool {tool.name} is a BaseTool ")
            # check if has kwargs or variable arguments
            if not has_kwargs(tool):
                toolfn_conv = tool._run
            else:
                toolfn_conv = _create_langchain_tool_executor(tool, unify_arguments=False)
        else:
            copilot_error(f"Unsupported tool type: {type(tool)}. Using individual parameters as fallback.")
            toolfn_conv = _create_langchain_tool_executor(tool, unify_arguments=False)

        mcp_tool = Tool.from_function(
            fn=toolfn_conv,
            name=tool.name,
            description=(tool.description or "No description provided")
            + "\nInput Structure: \n"
            + str(tool.args_schema.model_json_schema()),
            tags={tool.__class__.__name__} if tool.__class__.__name__ else set(),
            enabled=True,
        )

        # Store the schema information
        mcp_tool.parameters = tool.args_schema.model_json_schema()

        # Store reference to original tool for debugging/metadata
        mcp_tool._original_langchain_tool = tool

        return mcp_tool

    except Exception as e:
        # Shutdown the entire application if tool conversion fails
        copilot_error(f"Failed to create executor for tool {tool.name}: {e}")


def method_name(tool):
    return tool.args_schema


def convert_langchain_tools_to_mcp(tools: List[BaseTool]) -> List[Tool]:
    """
    Convert LangChain tools to MCP format.

    Args:
        tools: List of LangChain BaseTool objects

    Returns:
        List[Tool]: Tools converted to FastMCP Tool format ready for registration
    """
    mcp_tools = []

    for tool in tools:
        if not isinstance(tool, BaseTool):
            copilot_error(f"Invalid tool type: {type(tool)}. Expected BaseTool.")
            continue

        try:
            mcp_tool = _convert_single_tool_to_mcp(tool)
            mcp_tools.append(mcp_tool)
            copilot_debug(f"Converted LangChain tool '{tool.name}' to FastMCP Tool")

        except Exception as e:
            copilot_error(f"Error converting LangChain tool '{tool.name}' to MCP format: {e}")
            continue

    return mcp_tools


def setup_agent_from_etendo(identifier: str, etendo_token: str) -> bool:
    """
    Setup agent configuration from Etendo Classic.

    Args:
        identifier: Agent identifier
        etendo_token: Bearer token for authentication

    Returns:
        bool: True if setup successful, False otherwise
    """
    global agent_tools

    try:
        # Fetch agent structure from Etendo
        agent_config = fetch_agent_structure_from_etendo(identifier, etendo_token)

        if not agent_config:
            copilot_error(f"Could not fetch agent configuration for {identifier}")
            return False

        copilot_info(f"Agent config loaded: {agent_config.name or identifier}")

        # Configure system prompt if available
        if agent_config.system_prompt:
            copilot_debug(f"System prompt configured for agent {identifier}: {agent_config.system_prompt}...")

        # Convert agent tools to MCP format and store in global array
        if agent_config.tools:
            copilot_info(f"Processing {len(agent_config.tools)} agent tools from Etendo")
            mcp_tools = convert_langchain_tools_to_mcp(agent_config.tools)

            # Store converted tools in global array for external access
            agent_tools = mcp_tools
            copilot_info(
                f"✅ Converted {len(mcp_tools)} agent tools to MCP format and stored in agent_tools array"
            )

            # Log the tools that were converted
            for tool in mcp_tools:
                copilot_debug(f"  - {tool.name}: {tool.description}")
        else:
            agent_tools = []
            copilot_info("No tools found in agent configuration")

        copilot_info(f"Agent {identifier} successfully configured from Etendo Classic")
        copilot_info(f"agent_tools array contains {len(agent_tools)} tools ready for MCP registration")
        return True

    except Exception as e:
        copilot_error(f"Error setting up agent from Etendo: {e}")
        return False


def _get_agent_structure_tool() -> dict:
    """Internal implementation for get_agent_structure tool."""
    try:
        identifier = get_agent_identifier()

        etendo_token = extract_etendo_token_from_mcp_context()
        if not etendo_token:
            return {"success": False, "error": ERROR_NO_TOKEN}

        agent_config = fetch_agent_structure_from_etendo(identifier, etendo_token)
        if not agent_config:
            return {"success": False, "error": "Could not fetch agent structure"}

        return {
            "success": True,
            "agent": {
                "name": agent_config.name,
                "type": agent_config.type,
                "assistant_id": agent_config.assistant_id,
                "system_prompt": agent_config.system_prompt,
                "model": agent_config.model,
                "provider": agent_config.provider,
                "tools_count": len(agent_config.tools) if agent_config.tools else 0,
                "description": agent_config.description,
            },
        }

    except Exception as e:
        copilot_error(f"Error getting agent structure: {e}")
        return {"success": False, "error": f"Unexpected error: {str(e)}"}


def register_agent_tools(
    app, identifier: Optional[str] = None, etendo_token: Optional[str] = None, is_direct_mode: bool = False
) -> dict:
    """
    Register agent-specific tools with the MCP app.

    This function gets the agent identifier and token, then fetches
    the agent configuration from Etendo Classic and sets up the tools.

    Args:
        etendo_token: Bearer token for authentication
        identifier:  Agent identifier (app_id) to fetch configuration
        app: FastMCP application instance
        is_direct_mode: Whether this is being called in direct mode
    """
    try:
        if not identifier:
            return {"success": False, "error": ERROR_NO_AGENT_ID}

        if not etendo_token:
            return {"success": False, "error": ERROR_NO_TOKEN}

        agent_config: AssistantSchema = fetch_agent_structure_from_etendo(identifier, etendo_token)
        if not agent_config:
            return {"success": False, "error": "Could not fetch agent structure"}

        _base_tools = ToolLoader().get_all_tools(
            agent_configuration=agent_config,
            enabled_tools=agent_config.tools,
            include_kb_tool=True,
            include_openapi_tools=True,
        )

        # In direct mode, if agent is a supervisor, register team member ask tools instead of supervisor tools
        if is_direct_mode and _is_supervisor(agent_config):
            # For supervisors in direct mode: only team member ask tools, no supervisor prompt or tools
            team_ask_tools = _make_team_ask_agent_tools(agent_config)
            mcp_tools = team_ask_tools
            copilot_info(
                f"Direct mode: Supervisor detected, registering only {len(team_ask_tools)} team member ask tools (no supervisor prompt)"
            )
        else:
            # For regular agents or supervisors in simple mode: include prompt tool and converted tools
            mcp_tools = [_gen_prompt_tool(agent_config)]
            mcp_tools.extend(convert_langchain_tools_to_mcp(_base_tools))

        # Note: agent-specific ask tools are only registered in the "simple" mode.
        # For direct mode we register only the agent prompt and converted tools.

        for tool in mcp_tools:
            # Use add_tool method which expects a Tool object
            app.add_tool(tool)
            copilot_debug(f"Registered FastMCP tool: {tool.name}")

        copilot_info(f"Successfully registered {len(mcp_tools)} agent tools")
        return {"success": True, "tools_count": len(mcp_tools)}

    except Exception as e:
        copilot_error(f"Error listing agent tools: {e}")
        return {"success": False, "error": f"Unexpected error: {str(e)}"}


def _gen_prompt_tool(agent_config: AssistantSchema, identifier: Optional[str] = None) -> Tool:
    # add a tool that retrieves the agent prompt
    def _get_prompt_tool() -> dict:
        """Tool to retrieve the agent structure."""
        try:
            return {
                "success": True,
                "agent_name": agent_config.name or identifier,
                "agent_prompt": agent_config.system_prompt or "No system prompt configured",
            }

        except Exception as e:
            copilot_error(f"Error getting agent structure: {e}")
            return {"success": False, "error": f"Unexpected error: {str(e)}"}

    get_prompt_tool = Tool.from_function(
        fn=_get_prompt_tool,
        name="get_agent_prompt",
        description="Retrieve the agent's system prompt and configuration",
        tags={"agent", "structure"},
        enabled=True,
    )
    return get_prompt_tool


def _sanitize_tool_name(name: str) -> str:
    """Sanitize agent name to be used in tool names: replace non-alphanumeric with underscore."""
    if not name:
        return "unknown_agent"
    # Replace any sequence of non-alphanumeric characters with single underscore
    sanitized = re.sub(r"[^0-9a-zA-Z]+", "_", name).strip("_")
    # Ensure it doesn't start with a digit (tool name should be a valid identifier-like string)
    if sanitized and sanitized[0].isdigit():
        sanitized = "agent_" + sanitized
    return sanitized


def _is_supervisor(agent_config: AssistantSchema) -> bool:
    """Check if an agent is a supervisor based on the presence of assistants."""
    return agent_config.assistants is not None and len(agent_config.assistants) > 0


def _make_team_ask_agent_tools(supervisor_config: AssistantSchema) -> List[Tool]:
    """Create ask_agent tools for each team member of a supervisor.

    Args:
        supervisor_config: Supervisor configuration containing team members

    Returns:
        List[Tool]: Tools for each team member
    """
    tools = []

    if not _is_supervisor(supervisor_config):
        return tools

    for assistant in supervisor_config.assistants:
        try:
            # Create ask tool for each team member using their own identifier
            team_member_tool = _make_ask_agent_tool(assistant, assistant.assistant_id or assistant.name)
            tools.append(team_member_tool)
            copilot_debug(f"Created ask tool for team member: {team_member_tool.name}")
        except Exception as e:
            copilot_error(f"Failed to create ask tool for team member {assistant.name}: {e}")

    copilot_info(f"Created {len(tools)} ask tools for supervisor team members")
    return tools


def _make_ask_agent_tool(agent_config: AssistantSchema, identifier: str) -> Tool:
    """Create an ask_agent_<AgentName> FastMCP Tool using agent configuration.

    The tool will POST the question to Etendo's /sws/copilot/question endpoint using the
    Etendo token obtained at execution time from thread/context via get_etendo_token().
    """

    async def _ask_agent(question: str, conversation_id: Optional[str] = None) -> dict:
        try:
            # Get token from MCP request context using helper; get_etendo_token may raise if missing
            try:
                token = get_etendo_token()
            except Exception:
                return {
                    "success": False,
                    "error": "No authentication token found in request headers. Authentication required.",
                    "status_code": 401,
                }

            # Normalize then validate token
            normalized_token = normalize_etendo_token(token)
            if not validate_etendo_token(normalized_token):
                return {
                    "success": False,
                    "error": "Invalid Bearer token format. Authentication required.",
                    "status_code": 401,
                }

            payload = {"question": question, "app_id": identifier}
            if conversation_id and conversation_id != "null" and conversation_id != "":
                payload["conversation_id"] = conversation_id

            etendo_host = get_etendo_host()
            headers = {
                "Content-Type": APPLICATION_JSON,
                "Accept": APPLICATION_JSON,
                "Authorization": normalized_token,
            }

            async with httpx.AsyncClient(timeout=300.0) as client:
                resp = await client.post(f"{etendo_host}/sws/copilot/question", json=payload, headers=headers)
                try:
                    body = resp.json()
                except Exception:
                    body = resp.text

                if resp.is_error:
                    return {"success": False, "status_code": resp.status_code, "error": body}

                return {"success": True, "status_code": resp.status_code, "result": body}

        except Exception as e:
            copilot_error(f"Error in agent-specific ask tool for {identifier}: {e}")
            return {"success": False, "error": str(e)}

    agent_name = agent_config.name or identifier
    sanitized = _sanitize_tool_name(agent_name)
    tool_name = f"ask_agent_{sanitized}"
    description = agent_config.description or f"Ask agent {agent_name}"

    ask_tool = Tool.from_function(
        fn=_ask_agent,
        name=tool_name,
        description=description,
        tags={"agent", "ask"},
        enabled=True,
    )

    return ask_tool


def initialize_agent_from_etendo(identifier: str):
    """
    Initialize agent configuration from Etendo Classic during MCP startup.

    Args:
        app: FastMCP application instance
    """
    try:
        if not identifier:
            copilot_error(ERROR_NO_AGENT_ID + " for initialization")
            return

        etendo_token = extract_etendo_token_from_mcp_context()
        if not etendo_token:
            copilot_error(ERROR_NO_TOKEN + " for initialization")
            return

        copilot_info(f"Initializing agent {identifier} from Etendo Classic")
        success = setup_agent_from_etendo(identifier, etendo_token)

        if success:
            copilot_info(f"Agent {identifier} successfully initialized from Etendo Classic")
        else:
            copilot_error(f"Failed to initialize agent {identifier} from Etendo Classic")

    except Exception as e:
        copilot_error(f"Error during agent initialization: {e}")
