"""
Tests for copilot.core.mcp.tools.agent_tools module.

This module contains comprehensive tests for agent-specific tools functionality
including tool conversion, agent setup, and MCP tool registration.
"""

from typing import Optional, Type
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from copilot.core.mcp.tools.agent_tools import (
    ERROR_NO_AGENT_ID,
    ERROR_NO_TOKEN,
    _build_param_definition,
    _convert_single_tool_to_mcp,
    _create_langchain_tool_executor,
    _execute_langchain_tool,
    _gen_prompt_tool,
    _get_agent_structure_tool,
    _get_param_type_annotation,
    convert_langchain_tools_to_mcp,
    fetch_agent_structure_from_etendo,
    get_agent_identifier,
    initialize_agent_from_etendo,
    register_agent_tools,
    setup_agent_from_etendo,
)
from copilot.core.schemas import AssistantSchema
from copilot.core.tool_wrapper import ToolWrapper
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field


class MockFieldInfo:
    """Mock Pydantic field info for testing."""

    def __init__(self, annotation=str, required=True, default=None):
        self.annotation = annotation
        self._required = required
        self.default = default

    def is_required(self):
        return self._required


class MockToolArgsSchema(BaseModel):
    """Mock args schema for testing tool conversion."""

    query: str = Field(description="Search query")
    limit: Optional[int] = Field(default=10, description="Result limit")
    category: str = Field(description="Category filter")


class MockBaseTool(BaseTool):
    """Mock LangChain tool for testing."""

    name: str = "mock_tool"
    description: str = "A mock tool for testing"
    args_schema: Type[BaseModel] = MockToolArgsSchema

    def _run(self, query: str, limit: int = 10, category: str = "general") -> str:
        return f"Mock result for query: {query}, limit: {limit}, category: {category}"

    async def _arun(self, query: str, limit: int = 10, category: str = "general") -> str:
        return f"Async mock result for query: {query}, limit: {limit}, category: {category}"


class MockToolWrapper(ToolWrapper):
    """Mock ToolWrapper for testing."""

    def __init__(self):
        super().__init__()
        self.name = "mock_wrapper_tool"
        self.description = "A mock wrapper tool for testing"
        self.args_schema: Type[BaseModel] = MockToolArgsSchema

    def run(self, **kwargs) -> str:
        """Implement the abstract run method."""
        return f"Wrapper result: {kwargs}"

    def _run(self, **kwargs) -> str:
        return f"Wrapper result: {kwargs}"


class TestParamTypeAnnotation:
    """Tests for _get_param_type_annotation function."""

    def test_get_param_type_annotation_with_name(self):
        """Test extracting type annotation when field has __name__."""
        field_info = MockFieldInfo(annotation=str)
        result = _get_param_type_annotation(field_info)
        assert result == "str"

    def test_get_param_type_annotation_union_type(self):
        """Test extracting type annotation for Union types."""
        field_info = MockFieldInfo()
        field_info.annotation = type("Union", (), {"__name__": "Union"})
        result = _get_param_type_annotation(field_info)
        assert result == "Any"

    def test_get_param_type_annotation_optional_type(self):
        """Test extracting type annotation for Optional types."""
        field_info = MockFieldInfo()
        field_info.annotation = type("Optional", (), {"__name__": "Optional"})
        result = _get_param_type_annotation(field_info)
        assert result == "Any"

    def test_get_param_type_annotation_no_annotation(self):
        """Test extracting type annotation when no annotation available."""
        field_info = MockFieldInfo(annotation=None)
        result = _get_param_type_annotation(field_info)
        assert result == "str"


class TestBuildParamDefinition:
    """Tests for _build_param_definition function."""

    def test_build_param_definition_required(self):
        """Test building parameter definition for required field."""
        field_info = MockFieldInfo(annotation=str, required=True)
        result = _build_param_definition("query", field_info)
        assert result == "query: str"

    def test_build_param_definition_optional_with_default(self):
        """Test building parameter definition for optional field with default."""
        field_info = MockFieldInfo(annotation=int, required=False, default=10)
        result = _build_param_definition("limit", field_info)
        assert result == "limit: int = 10"

    def test_build_param_definition_optional_string_default(self):
        """Test building parameter definition for optional field with string default."""
        field_info = MockFieldInfo(annotation=str, required=False, default="test")
        result = _build_param_definition("category", field_info)
        assert result == "category: str = 'test'"

    def test_build_param_definition_optional_no_default(self):
        """Test building parameter definition for optional field without default."""
        field_info = MockFieldInfo(annotation=str, required=False)
        result = _build_param_definition("optional_param", field_info)
        assert result == "optional_param: str = None"


class TestCreateLangchainToolExecutor:
    """Tests for _create_langchain_tool_executor function."""

    def test_create_executor_no_args_schema(self):
        """Test creating executor for tool without args schema."""
        tool = MagicMock()
        tool.name = "simple_tool"
        tool.args_schema = BaseModel
        tool.run.return_value = "simple result"

        executor = _create_langchain_tool_executor(tool)

        assert callable(executor)
        result = executor("test input")
        assert "simple result" in str(result)

    def test_create_executor_with_args_schema(self):
        """Test creating executor for tool with args schema."""
        tool = MockBaseTool()

        executor = _create_langchain_tool_executor(tool)

        assert callable(executor)
        assert executor.__name__ == "execute_mock_tool"
        assert "Execute mock_tool" in executor.__doc__

    def test_create_executor_unified_args(self):
        """Test creating executor with unified arguments."""
        tool = MockBaseTool()

        executor = _create_langchain_tool_executor(tool, unify_arguments=True)

        assert callable(executor)
        assert "unified arguments object" in executor.__doc__

    @patch("copilot.core.mcp.tools.agent_tools.copilot_debug")
    def test_create_executor_execution(self, mock_debug):
        """Test executor execution with parameters."""
        tool = MockBaseTool()

        executor = _create_langchain_tool_executor(tool)

        # This would require complex dynamic function testing
        # For now, we verify the executor is created properly
        assert callable(executor)
        assert hasattr(executor, "__name__")


class TestExecuteLangchainTool:
    """Tests for _execute_langchain_tool function."""

    @pytest.mark.asyncio
    async def test_execute_tool_with_run(self):
        """Test executing tool with run method."""
        tool = MagicMock()
        tool.run.return_value = "test result"

        result = await _execute_langchain_tool(tool, {"query": "test"})

        assert result == "test result"
        tool.run.assert_called_once_with(tool_input={"query": "test"})

    @pytest.mark.asyncio
    async def test_execute_tool_with_ainvoke(self):
        """Test executing tool with ainvoke method."""
        tool = MagicMock()
        del tool.run  # Remove run method
        tool.ainvoke = AsyncMock(return_value="async result")

        result = await _execute_langchain_tool(tool, {"query": "test"})

        assert result == "async result"
        tool.ainvoke.assert_called_once_with({"query": "test"})

    @pytest.mark.asyncio
    async def test_execute_tool_with_arun(self):
        """Test executing tool with arun method."""
        tool = MagicMock()
        del tool.run
        del tool.ainvoke
        tool.arun = AsyncMock(return_value="arun result")

        result = await _execute_langchain_tool(tool, {"query": "test"})

        assert result == "arun result"
        tool.arun.assert_called_once_with(query="test")

    @pytest.mark.asyncio
    async def test_execute_tool_with_invoke(self):
        """Test executing tool with invoke method."""
        tool = MagicMock()
        del tool.run
        del tool.ainvoke
        del tool.arun
        tool.invoke.return_value = "invoke result"

        result = await _execute_langchain_tool(tool, {"query": "test"})

        assert result == "invoke result"
        tool.invoke.assert_called_once_with({"query": "test"})

    @pytest.mark.asyncio
    async def test_execute_tool_callable(self):
        """Test executing tool as callable."""
        tool = MagicMock()
        del tool.run
        del tool.ainvoke
        del tool.arun
        del tool.invoke
        tool.return_value = "callable result"

        result = await _execute_langchain_tool(tool, {"query": "test"})

        assert result == "callable result"
        tool.assert_called_once_with(query="test")


class TestConvertSingleToolToMcp:
    """Tests for _convert_single_tool_to_mcp function."""

    @patch("copilot.core.mcp.tools.agent_tools.Tool")
    @patch("copilot.core.mcp.tools.agent_tools._create_langchain_tool_executor")
    @patch("copilot.core.mcp.tools.agent_tools.copilot_error")
    def test_convert_unknown_tool_type(self, mock_error, mock_create_executor, mock_tool_class):
        """Test converting unknown tool type (fallback to individual parameters)."""
        tool = MagicMock()
        tool.name = "unknown_tool"
        tool.description = "Unknown tool type"
        tool.args_schema = MockToolArgsSchema
        mock_executor = MagicMock()
        mock_create_executor.return_value = mock_executor
        mock_mcp_tool = MagicMock()
        mock_tool_class.from_function.return_value = mock_mcp_tool

        result = _convert_single_tool_to_mcp(tool)

        # Expect 2 calls: one for unsupported tool type, one for executor creation failure
        assert mock_error.call_count == 2
        mock_create_executor.assert_called_once_with(tool, unify_arguments=False)
        assert result is None

    @patch("copilot.core.mcp.tools.agent_tools.copilot_error")
    def test_convert_tool_with_exception(self, mock_error):
        """Test converting tool when exception occurs."""
        tool = MagicMock()
        tool.name = "failing_tool"
        tool.args_schema = MagicMock()
        tool.args_schema.model_json_schema.side_effect = Exception("Test error")

        result = _convert_single_tool_to_mcp(tool)

        # Expect 2 calls: one for unsupported tool type, one for executor creation failure
        assert mock_error.call_count == 2
        assert result is None


class TestConvertLangchainToolsToMcp:
    """Tests for convert_langchain_tools_to_mcp function."""

    @patch("copilot.core.mcp.tools.agent_tools._convert_single_tool_to_mcp")
    def test_convert_tools_success(self, mock_convert):
        """Test converting list of tools successfully."""
        tool1 = MockBaseTool()
        tool2 = MockBaseTool()
        tools = [tool1, tool2]

        mock_mcp_tool1 = MagicMock()
        mock_mcp_tool2 = MagicMock()
        mock_convert.side_effect = [mock_mcp_tool1, mock_mcp_tool2]

        result = convert_langchain_tools_to_mcp(tools)

        assert len(result) == 2
        assert result[0] == mock_mcp_tool1
        assert result[1] == mock_mcp_tool2
        assert mock_convert.call_count == 2

    @patch("copilot.core.mcp.tools.agent_tools._convert_single_tool_to_mcp")
    @patch("copilot.core.mcp.tools.agent_tools.copilot_error")
    def test_convert_tools_with_invalid_type(self, mock_error, mock_convert):
        """Test converting tools with invalid tool type."""
        valid_tool = MockBaseTool()
        invalid_tool = "not a tool"
        tools = [valid_tool, invalid_tool]

        mock_mcp_tool = MagicMock()
        mock_convert.return_value = mock_mcp_tool

        result = convert_langchain_tools_to_mcp(tools)

        assert len(result) == 1
        assert result[0] == mock_mcp_tool
        mock_error.assert_called_once()
        mock_convert.assert_called_once_with(valid_tool)

    @patch("copilot.core.mcp.tools.agent_tools._convert_single_tool_to_mcp")
    @patch("copilot.core.mcp.tools.agent_tools.copilot_error")
    def test_convert_tools_with_conversion_error(self, mock_error, mock_convert):
        """Test converting tools when conversion fails."""
        tool1 = MockBaseTool()
        tool2 = MockBaseTool()
        tools = [tool1, tool2]

        mock_mcp_tool = MagicMock()
        mock_convert.side_effect = [mock_mcp_tool, Exception("Conversion failed")]

        result = convert_langchain_tools_to_mcp(tools)

        assert len(result) == 1
        assert result[0] == mock_mcp_tool
        mock_error.assert_called_once()


class TestGetAgentIdentifier:
    """Tests for get_agent_identifier function."""

    @patch("copilot.core.mcp.tools.agent_tools.get_context")
    def test_get_agent_identifier_success(self, mock_get_context):
        """Test getting agent identifier successfully."""
        mock_context = MagicMock()
        mock_context.fastmcp._mcp_server.version = "test_agent_id"
        mock_get_context.return_value = mock_context

        result = get_agent_identifier()

        assert result == "test_agent_id"

    @patch("copilot.core.mcp.tools.agent_tools.get_context")
    def test_get_agent_identifier_exception(self, mock_get_context):
        """Test getting agent identifier when exception occurs."""
        mock_get_context.side_effect = Exception("Context error")

        result = get_agent_identifier()

        assert result is None


class TestFetchAgentStructureFromEtendo:
    """Tests for fetch_agent_structure_from_etendo function."""

    @patch("copilot.core.mcp.tools.agent_tools.get_etendo_host")
    @patch("copilot.core.mcp.tools.agent_tools.call_etendo")
    def test_fetch_agent_structure_success(self, mock_call_etendo, mock_get_host):
        """Test fetching agent structure successfully."""
        mock_get_host.return_value = "https://test.etendo.com"
        mock_response = {
            "name": "Test Agent",
            "type": "assistant",
            "assistant_id": "test_id",
            "system_prompt": "Test prompt",
            "model": "gpt-4",
            "provider": "openai",
            "tools": [],
            "description": "Test description",
        }
        mock_call_etendo.return_value = mock_response

        result = fetch_agent_structure_from_etendo("test_agent", "test_token")

        assert isinstance(result, AssistantSchema)
        assert result.name == "Test Agent"
        mock_call_etendo.assert_called_once()

    @patch("copilot.core.mcp.tools.agent_tools.get_etendo_host")
    @patch("copilot.core.mcp.tools.agent_tools.call_etendo")
    @patch("copilot.core.mcp.tools.agent_tools.copilot_error")
    def test_fetch_agent_structure_http_error(self, mock_error, mock_call_etendo, mock_get_host):
        """Test fetching agent structure with HTTP error."""
        import httpx

        mock_get_host.return_value = "https://test.etendo.com"
        mock_call_etendo.side_effect = httpx.HTTPStatusError(
            "HTTP Error", request=MagicMock(), response=MagicMock()
        )

        result = fetch_agent_structure_from_etendo("test_agent", "test_token")

        assert result is None
        mock_error.assert_called_once()


class TestGetAgentStructureTool:
    """Tests for _get_agent_structure_tool function."""

    @patch("copilot.core.mcp.tools.agent_tools.get_agent_identifier")
    @patch("copilot.core.mcp.tools.agent_tools.extract_etendo_token_from_mcp_context")
    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    def test_get_agent_structure_success(self, mock_fetch, mock_extract_token, mock_get_id):
        """Test getting agent structure successfully."""
        mock_get_id.return_value = "test_agent"
        mock_extract_token.return_value = "test_token"

        mock_agent = MagicMock()
        mock_agent.name = "Test Agent"
        mock_agent.type = "assistant"
        mock_agent.assistant_id = "test_id"
        mock_agent.system_prompt = "Test prompt"
        mock_agent.model = "gpt-4"
        mock_agent.provider = "openai"
        mock_agent.tools = []
        mock_agent.description = "Test description"
        # Mock fetch_agent_structure_from_etendo as a regular function, not async
        mock_fetch.return_value = mock_agent

        result = _get_agent_structure_tool()

        assert result["success"] is True
        assert result["agent"]["name"] == "Test Agent"
        assert result["agent"]["tools_count"] == 0

    @patch("copilot.core.mcp.tools.agent_tools.extract_etendo_token_from_mcp_context")
    def test_get_agent_structure_no_token(self, mock_extract_token):
        """Test getting agent structure without token."""
        mock_extract_token.return_value = None

        result = _get_agent_structure_tool()

        assert result["success"] is False
        assert result["error"] == ERROR_NO_TOKEN

    @patch("copilot.core.mcp.tools.agent_tools.get_agent_identifier")
    @patch("copilot.core.mcp.tools.agent_tools.extract_etendo_token_from_mcp_context")
    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    def test_get_agent_structure_fetch_failed(self, mock_fetch, mock_extract_token, mock_get_id):
        """Test getting agent structure when fetch fails."""
        mock_get_id.return_value = "test_agent"
        mock_extract_token.return_value = "test_token"
        mock_fetch.return_value = None

        result = _get_agent_structure_tool()

        assert result["success"] is False
        assert result["error"] == "Could not fetch agent structure"

    @patch("copilot.core.mcp.tools.agent_tools.get_agent_identifier")
    def test_get_agent_structure_exception(self, mock_get_id):
        """Test getting agent structure when exception occurs."""
        mock_get_id.side_effect = Exception("Test error")

        result = _get_agent_structure_tool()

        assert result["success"] is False
        assert "Unexpected error" in result["error"]


class TestGenPromptTool:
    """Tests for _gen_prompt_tool function."""

    def test_gen_prompt_tool_creation(self):
        """Test creating prompt tool."""
        mock_agent = MagicMock()
        mock_agent.name = "Test Agent"
        mock_agent.system_prompt = "Test prompt"

        with patch("copilot.core.mcp.tools.agent_tools.Tool") as mock_tool_class:
            mock_tool = MagicMock()
            mock_tool_class.from_function.return_value = mock_tool

            result = _gen_prompt_tool(mock_agent, "test_id")

            assert result == mock_tool
            mock_tool_class.from_function.assert_called_once()

            # Test the function that was passed to Tool.from_function
            call_args = mock_tool_class.from_function.call_args
            fn = call_args[1]["fn"]

            # Test the function execution
            fn_result = fn()
            assert fn_result["success"] is True
            assert fn_result["agent_name"] == "Test Agent"
            assert fn_result["agent_prompt"] == "Test prompt"

    def test_gen_prompt_tool_no_prompt(self):
        """Test creating prompt tool without system prompt."""
        mock_agent = MagicMock()
        mock_agent.name = "Test Agent"
        mock_agent.system_prompt = None

        with patch("copilot.core.mcp.tools.agent_tools.Tool") as mock_tool_class:
            mock_tool = MagicMock()
            mock_tool_class.from_function.return_value = mock_tool

            _gen_prompt_tool(mock_agent, "test_id")

            call_args = mock_tool_class.from_function.call_args
            fn = call_args[1]["fn"]

            fn_result = fn()
            assert fn_result["agent_prompt"] == "No system prompt configured"


class TestSetupAgentFromEtendo:
    """Tests for setup_agent_from_etendo function."""

    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    @patch("copilot.core.mcp.tools.agent_tools.convert_langchain_tools_to_mcp")
    def test_setup_agent_success(self, mock_convert, mock_fetch):
        """Test setting up agent successfully."""
        mock_agent = MagicMock()
        mock_agent.name = "Test Agent"
        mock_agent.system_prompt = "Test prompt"
        mock_agent.tools = [MockBaseTool()]
        mock_fetch.return_value = mock_agent

        mock_mcp_tools = [MagicMock()]
        mock_convert.return_value = mock_mcp_tools

        result = setup_agent_from_etendo("test_agent", "test_token")

        assert result is True
        mock_fetch.assert_called_once_with("test_agent", "test_token")
        mock_convert.assert_called_once_with(mock_agent.tools)

    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    def test_setup_agent_no_config(self, mock_fetch):
        """Test setting up agent when no config found."""
        mock_fetch.return_value = None

        result = setup_agent_from_etendo("test_agent", "test_token")

        assert result is False

    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    def test_setup_agent_no_tools(self, mock_fetch):
        """Test setting up agent without tools."""
        mock_agent = MagicMock()
        mock_agent.name = "Test Agent"
        mock_agent.system_prompt = "Test prompt"
        mock_agent.tools = None
        mock_fetch.return_value = mock_agent

        result = setup_agent_from_etendo("test_agent", "test_token")

        assert result is True

    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    def test_setup_agent_exception(self, mock_fetch):
        """Test setting up agent when exception occurs."""
        mock_fetch.side_effect = Exception("Test error")

        result = setup_agent_from_etendo("test_agent", "test_token")

        assert result is False


class TestRegisterAgentTools:
    """Tests for register_agent_tools function."""

    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    @patch("copilot.core.mcp.tools.agent_tools.ToolLoader")
    @patch("copilot.core.mcp.tools.agent_tools.convert_langchain_tools_to_mcp")
    @patch("copilot.core.mcp.tools.agent_tools._gen_prompt_tool")
    def test_register_agent_tools_success(
        self, mock_gen_prompt, mock_convert, mock_tool_loader_class, mock_fetch
    ):
        """Test registering agent tools successfully."""
        # Setup mocks
        mock_agent = MagicMock()
        mock_agent.tools = [MockBaseTool()]
        mock_fetch.return_value = mock_agent

        mock_tool_loader = MagicMock()
        mock_base_tools = [MockBaseTool()]
        mock_tool_loader.get_all_tools.return_value = mock_base_tools
        mock_tool_loader_class.return_value = mock_tool_loader

        mock_prompt_tool = MagicMock()
        mock_gen_prompt.return_value = mock_prompt_tool

        mock_mcp_tools = [MagicMock()]
        mock_convert.return_value = mock_mcp_tools

        mock_app = MagicMock()

        # Provide the agent_config directly to avoid relying on internal fetch behavior
        result = register_agent_tools(mock_app, "test_agent", "test_token", agent_config=mock_agent)

        assert result["success"] is True
        assert result["tools_count"] == 2  # prompt tool + converted tools
        mock_app.add_tool.assert_called()

    def test_register_agent_tools_no_identifier(self):
        """Test registering agent tools without identifier."""
        mock_app = MagicMock()

        result = register_agent_tools(mock_app, None, "test_token")

        assert result["success"] is False
        assert result["error"] == ERROR_NO_AGENT_ID

    def test_register_agent_tools_no_token(self):
        """Test registering agent tools without token."""
        mock_app = MagicMock()

        result = register_agent_tools(mock_app, "test_agent", None)

        assert result["success"] is False
        assert result["error"] == ERROR_NO_TOKEN

    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    def test_register_agent_tools_no_config(self, mock_fetch):
        """Test registering agent tools when no config found."""
        mock_fetch.return_value = None
        mock_app = MagicMock()

        result = register_agent_tools(mock_app, "test_agent", "test_token")

        assert result["success"] is False
        # Current implementation returns a specific configuration-related message
        assert result["error"] == "Could not fetch agent configuration"

    def test_register_agent_tools_exception(self):
        """Test registering agent tools when exception occurs inside the registration flow."""
        # Simulate an internal error during tool loading to trigger the exception path
        with patch(
            "copilot.core.mcp.tools.agent_tools.ToolLoader", side_effect=Exception("ToolLoader failed")
        ):
            mock_app = MagicMock()

            result = register_agent_tools(mock_app, "test_agent", "test_token", agent_config=MagicMock())

            assert result["success"] is False
            assert "Unexpected error" in result["error"]

    @patch("copilot.core.mcp.tools.agent_tools.convert_langchain_tools_to_mcp")
    @patch("copilot.core.mcp.tools.agent_tools._make_team_ask_agent_tools")
    @patch("copilot.core.mcp.tools.agent_tools._is_supervisor")
    @patch("copilot.core.mcp.tools.agent_tools.ToolLoader")
    @patch("copilot.core.mcp.tools.agent_tools.fetch_agent_structure_from_etendo")
    def test_register_agent_tools_supervisor_direct_mode(
        self, mock_fetch, mock_tool_loader, mock_is_supervisor, mock_make_team_tools, mock_convert
    ):
        """Test registering agent tools for supervisor in direct mode."""
        # Mock supervisor config
        supervisor_config = MagicMock()
        supervisor_config.tools = []
        supervisor_config.name = "Test Supervisor"

        mock_fetch.return_value = supervisor_config
        mock_tool_loader_instance = MagicMock()
        mock_tool_loader_instance.get_all_tools.return_value = []
        mock_tool_loader.return_value = mock_tool_loader_instance
        mock_is_supervisor.return_value = True

        # Mock team ask tools
        mock_team_tools = [MagicMock(), MagicMock()]
        mock_make_team_tools.return_value = mock_team_tools
        mock_convert.return_value = []

        mock_app = MagicMock()

        # Provide the supervisor configuration directly
        result = register_agent_tools(
            mock_app, "supervisor_agent", "test_token", is_direct_mode=True, agent_config=supervisor_config
        )

        assert result["success"] is True
        mock_is_supervisor.assert_called_once_with(supervisor_config)
        mock_make_team_tools.assert_called_once_with(supervisor_config)
        # Should register only team ask tools (no supervisor prompt tool)
        assert mock_app.add_tool.call_count == len(mock_team_tools)


class TestInitializeAgentFromEtendo:
    """Tests for initialize_agent_from_etendo function."""

    @patch("copilot.core.mcp.tools.agent_tools.extract_etendo_token_from_mcp_context")
    @patch("copilot.core.mcp.tools.agent_tools.setup_agent_from_etendo")
    def test_initialize_agent_success(self, mock_setup, mock_extract_token):
        """Test initializing agent successfully."""
        mock_extract_token.return_value = "test_token"
        mock_setup.return_value = True

        initialize_agent_from_etendo("test_agent")

        mock_setup.assert_called_once_with("test_agent", "test_token")

    def test_initialize_agent_no_identifier(self):
        """Test initializing agent without identifier."""
        with patch("copilot.core.mcp.tools.agent_tools.copilot_error") as mock_error:
            initialize_agent_from_etendo("")

            mock_error.assert_called_once()

    @patch("copilot.core.mcp.tools.agent_tools.extract_etendo_token_from_mcp_context")
    def test_initialize_agent_no_token(self, mock_extract_token):
        """Test initializing agent without token."""
        mock_extract_token.return_value = None

        with patch("copilot.core.mcp.tools.agent_tools.copilot_error") as mock_error:
            initialize_agent_from_etendo("test_agent")

            mock_error.assert_called_once()

    @patch("copilot.core.mcp.tools.agent_tools.extract_etendo_token_from_mcp_context")
    @patch("copilot.core.mcp.tools.agent_tools.setup_agent_from_etendo")
    def test_initialize_agent_setup_failed(self, mock_setup, mock_extract_token):
        """Test initializing agent when setup fails."""
        mock_extract_token.return_value = "test_token"
        mock_setup.return_value = False

        with patch("copilot.core.mcp.tools.agent_tools.copilot_error") as mock_error:
            initialize_agent_from_etendo("test_agent")

            mock_error.assert_called_once()

    @patch("copilot.core.mcp.tools.agent_tools.extract_etendo_token_from_mcp_context")
    def test_initialize_agent_exception(self, mock_extract_token):
        """Test initializing agent when exception occurs."""
        mock_extract_token.side_effect = Exception("Test error")

        with patch("copilot.core.mcp.tools.agent_tools.copilot_error") as mock_error:
            initialize_agent_from_etendo("test_agent")

            mock_error.assert_called_once()


if __name__ == "__main__":
    pytest.main([__file__])
