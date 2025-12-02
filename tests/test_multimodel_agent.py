"""
Test the MultimodelAgent class
"""

import tempfile
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from copilot.core.agent.agent import AgentResponse, AssistantResponse
from copilot.core.agent.multimodel_agent import (
    MultimodelAgent,
    convert_mcp_servers_config,
    is_code_act_enabled,
)
from copilot.core.schemas import (
    AssistantSchema,
    FunctionSchema,
    QuestionSchema,
    ToolSchema,
)
from copilot.core.utils.agent import get_llm, get_model_config
from langchain_core.messages import AIMessage, HumanMessage


@pytest.fixture
def multimodel_agent():
    """Create a MultimodelAgent instance for testing."""
    with patch("copilot.core.tool_loader.ToolLoader") as mock_tool_loader:
        mock_load_tools = mock_tool_loader.return_value.load_tools
        mock_load_tools.return_value = MagicMock()
        return MultimodelAgent()


@pytest.fixture
def sample_question_schema():
    """Create a sample QuestionSchema for testing."""
    return QuestionSchema(
        question="What is the capital of France?",
        conversation_id="test_conversation_id",
        assistant_id="test_assistant_id",
        provider="openai",
        model="gpt-4.1",
        system_prompt="You are a helpful assistant",
        temperature=0.7,
        tools=[ToolSchema(type="function", function=FunctionSchema(name="test_tool"))],
        local_file_ids=[],
        mcp_servers=[],
    )


@pytest.fixture
def sample_assistant_schema():
    """Create a sample AssistantSchema for testing."""
    return AssistantSchema(
        assistant_id="test_assistant_id",
        name="Test Assistant",
        provider="openai",
        model="gpt-4.1",
        system_prompt="You are a helpful assistant",
        temperature=0.7,
        code_execution=False,
        tools=[],
        specs=[],
    )


class TestMultimodelAgent:
    """Test cases for the MultimodelAgent class."""

    def test_init(self, multimodel_agent):
        """Test MultimodelAgent initialization."""
        assert multimodel_agent is not None
        assert multimodel_agent._memory is not None
        assert multimodel_agent._configured_tools is not None

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    @patch("copilot.core.utils.agent.get_proxy_url", return_value=None)
    def test_get_llm_openai(self, mock_get_proxy_url, mock_get_model_config, mock_init_chat_model):
        """Test LLM initialization for OpenAI provider when NO proxy is configured."""
        mock_model = MagicMock()
        mock_init_chat_model.return_value = mock_model
        mock_get_model_config.return_value = {}

        get_llm("gpt-4.1", "openai", 0.7)

        # When no proxy is configured, model must be passed as-is
        mock_init_chat_model.assert_called_once_with(
            model_provider="openai",
            model="gpt-4.1",
            temperature=0.7,
            base_url=None,
            model_kwargs={"stream_options": {"include_usage": True}},
            streaming=True,
        )

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    @patch("copilot.core.utils.agent.get_proxy_url", return_value="https://llm.etendo.software")
    def test_get_llm_openai_with_proxy(self, mock_get_proxy_url, mock_get_model_config, mock_init_chat_model):
        """Test LLM initialization for OpenAI provider when a proxy is configured."""
        mock_model = MagicMock()
        mock_init_chat_model.return_value = mock_model
        mock_get_model_config.return_value = {}

        get_llm("gpt-4.1", "openai", 0.7)

        # When a proxy is configured, the implementation prefixes the model with provider
        mock_init_chat_model.assert_called_once_with(
            model_provider="openai",
            model="openai/gpt-4.1",
            temperature=0.7,
            base_url="https://llm.etendo.software",
            model_kwargs={"stream_options": {"include_usage": True}},
            streaming=True,
        )

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    @patch.dict("os.environ", {"COPILOT_OLLAMA_HOST": "localhost", "COPILOT_OLLAMA_PORT": "11434"})
    def test_get_llm_ollama(self, mock_get_model_config, mock_init_chat_model):
        """Test LLM initialization for Ollama provider."""
        mock_model = MagicMock()
        mock_init_chat_model.return_value = mock_model
        mock_get_model_config.return_value = {}

        result = get_llm("llama2", "ollama", 0.5)

        mock_init_chat_model.assert_called_once_with(
            model_provider="ollama",
            model="llama2",
            temperature=0.5,
            streaming=True,
            base_url="localhost:11434",
            model_kwargs={"stream_options": {"include_usage": True}},
        )
        assert result == mock_model

    @patch("copilot.core.utils.etendo_utils.get_extra_info")
    def test_get_model_config(self, mock_get_extra_info):
        """Test model configuration retrieval."""
        mock_get_extra_info.return_value = {
            "model_config": {"openai": {"gpt-4.1": {"max_tokens": 4096, "temperature": 0.7}}}
        }

        config = get_model_config("openai", "gpt-4.1")

        assert config["max_tokens"] == 4096
        assert config["temperature"] == 0.7

    def test_is_code_act_enabled_true(self, sample_assistant_schema):
        """Test code execution detection when enabled."""
        sample_assistant_schema.code_execution = True
        result = is_code_act_enabled(sample_assistant_schema)
        assert result is True

    def test_is_code_act_enabled_false(self, sample_assistant_schema):
        """Test code execution detection when disabled."""
        sample_assistant_schema.code_execution = False
        result = is_code_act_enabled(sample_assistant_schema)
        assert result is False

    def test_convert_mcp_servers_config_empty(self):
        """Test MCP server config conversion with empty list."""
        result = convert_mcp_servers_config([])
        assert result == {}

    def test_convert_mcp_servers_config_none(self):
        """Test MCP server config conversion with None."""
        result = convert_mcp_servers_config(None)
        assert result == {}

    def test_convert_mcp_servers_config_nested(self):
        """Test MCP server config conversion with direct server config (updated)."""
        with tempfile.TemporaryDirectory() as temp_dir:
            mcp_servers = [
                {
                    "name": "filesystem",
                    "command": "npx",
                    "args": ["-y", "@modelcontextprotocol/server-filesystem", temp_dir],
                    "disabled": False,
                }
            ]

            result = convert_mcp_servers_config(mcp_servers)

            assert "filesystem" in result
            assert result["filesystem"]["command"] == "npx"
            assert result["filesystem"]["args"][0] == "-y"
            assert result["filesystem"]["transport"] == "stdio"  # Default transport added

    def test_convert_mcp_servers_config_direct(self):
        """Test MCP server config conversion with direct server config."""
        mcp_servers = [
            {"name": "test_server", "command": "python", "args": ["-m", "test_server"], "disabled": False}
        ]

        result = convert_mcp_servers_config(mcp_servers)

        assert "test_server" in result
        assert result["test_server"]["command"] == "python"
        assert result["test_server"]["args"] == ["-m", "test_server"]
        assert result["test_server"]["transport"] == "stdio"  # Default transport added
        assert "name" not in result["test_server"]  # Name field should be removed

    def test_convert_mcp_servers_config_with_custom_transport(self):
        """Test MCP server config conversion with custom transport."""
        mcp_servers = [{"name": "custom_server", "command": "python", "transport": "sse", "disabled": False}]

        result = convert_mcp_servers_config(mcp_servers)

        assert "custom_server" in result
        assert result["custom_server"]["transport"] == "sse"  # Custom transport preserved

    def test_convert_mcp_servers_config_no_name_field(self):
        """Test MCP server config conversion without name field (auto-generated)."""
        mcp_servers = [
            {"command": "python", "args": ["-m", "test1"]},
            {"command": "node", "args": ["server.js"]},
        ]

        result = convert_mcp_servers_config(mcp_servers)

        assert "server_0" in result
        assert "server_1" in result
        assert result["server_0"]["command"] == "python"
        assert result["server_1"]["command"] == "node"

    def test_convert_mcp_servers_config_disabled(self):
        """Test MCP server config conversion with disabled server."""
        mcp_servers = [{"name": "disabled_server", "command": "python", "disabled": True}]

        result = convert_mcp_servers_config(mcp_servers)

        assert result == {}

    @patch("copilot.core.agent.multimodel_agent.get_llm")
    @patch("copilot.core.agent.multimodel_agent.create_agent")
    def test_get_agent_non_codeact(
        self, mock_create_react_agent, mock_get_llm, multimodel_agent, sample_assistant_schema
    ):
        """Test agent creation for non-CodeAct mode."""
        mock_llm = MagicMock()
        mock_get_llm.return_value = mock_llm
        mock_agent = MagicMock()
        mock_create_react_agent.return_value = mock_agent

        # Ensure code execution is disabled
        sample_assistant_schema.code_execution = False

        with patch.object(multimodel_agent, "_assert_system_prompt_is_set"):
            result = multimodel_agent.get_agent(
                provider="openai",
                model="gpt-4.1",
                agent_configuration=sample_assistant_schema,
                tools=[],
                system_prompt="Test prompt",
                temperature=0.7,
            )

        mock_get_llm.assert_called_once()
        mock_create_react_agent.assert_called_once()
        assert result == mock_agent

    @patch("copilot.core.agent.multimodel_agent.get_llm")
    @patch("copilot.core.agent.codeact.create_codeact")
    def test_get_agent_codeact(
        self, mock_create_codeact, mock_get_llm, multimodel_agent, sample_assistant_schema
    ):
        """Test agent creation for CodeAct mode."""
        mock_llm = MagicMock()
        mock_get_llm.return_value = mock_llm
        mock_agent = MagicMock()
        mock_compiled_agent = MagicMock()
        mock_agent.compile.return_value = mock_compiled_agent
        mock_create_codeact.return_value = mock_agent

        # Enable code execution
        sample_assistant_schema.code_execution = True

        with patch.object(multimodel_agent, "_assert_system_prompt_is_set"):
            result = multimodel_agent.get_agent(
                provider="openai",
                model="gpt-4.1",
                agent_configuration=sample_assistant_schema,
                tools=[],
                system_prompt="Test prompt",
                temperature=0.7,
            )

        mock_get_llm.assert_called_once()
        mock_create_codeact.assert_called_once()
        assert result == mock_compiled_agent

    @patch("copilot.core.agent.multimodel_agent.get_mcp_tools")
    @patch("copilot.core.agent.multimodel_agent.process_local_files")
    def test_execute(self, mock_process_files, mock_get_mcp_tools, multimodel_agent, sample_question_schema):
        """Test synchronous execution of agent."""
        # Mock the necessary components
        mock_get_mcp_tools.return_value = []
        mock_process_files.return_value = ([], [])

        # Mock the agent and its response
        mock_agent = MagicMock()
        mock_agent.invoke.return_value = {"messages": [AIMessage(content="Paris is the capital of France.")]}

        with patch.object(multimodel_agent, "get_agent", return_value=mock_agent):
            with patch.object(multimodel_agent._memory, "get_memory", return_value=[]):
                result = multimodel_agent.execute(sample_question_schema)

        assert isinstance(result, AgentResponse)
        assert isinstance(result.output, AssistantResponse)
        assert "Paris is the capital of France." in result.output.response
        assert result.output.conversation_id == sample_question_schema.conversation_id

    @pytest.mark.asyncio
    @patch("copilot.core.agent.multimodel_agent.process_local_files")
    async def test_aexecute_non_codeact(self, mock_process_files, multimodel_agent, sample_question_schema):
        """Test asynchronous execution for non-CodeAct agent."""
        # Setup mocks
        mock_process_files.return_value = ([], [])
        sample_question_schema.code_execution = False

        # Mock agent
        mock_agent = MagicMock()

        # Simulate agent.astream_events and patch handle_events which aexecute uses
        async def fake_astream_events(_input, config=None, version=None):
            yield {"fake": "event"}

        mock_agent.astream_events = fake_astream_events

        async def fake_handle_events(debug_flag, event, conversation_id):
            return AssistantResponse(
                response="Paris is the capital of France.",
                conversation_id=conversation_id,
            )

        with patch.object(multimodel_agent, "get_agent", return_value=mock_agent):
            with patch.object(multimodel_agent, "get_messages_array", return_value=[]):
                with patch(
                    "copilot.core.agent.multimodel_agent.handle_events", side_effect=fake_handle_events
                ):
                    responses = []
                    async for response in multimodel_agent.aexecute(sample_question_schema):
                        responses.append(response)

                    assert len(responses) > 0
                    assert isinstance(responses[0], AssistantResponse)
                    assert "Paris is the capital of France." in responses[0].response

    def test_get_messages_array_no_files(self, multimodel_agent, sample_question_schema):
        """Test message array construction without files."""
        with patch.object(multimodel_agent._memory, "get_memory", return_value=[]):
            result = multimodel_agent.get_messages_array(
                "Test question", [], [], sample_question_schema, False
            )

            assert isinstance(result, list)

    def test_get_messages_array_with_files(self, multimodel_agent, sample_question_schema):
        """Test message array construction with files."""
        image_payloads = [{"type": "image_url", "image_url": {"url": "data:image/png;base64,..."}}]
        other_files = ["/path/to/file.txt"]

        with patch.object(multimodel_agent._memory, "get_memory", return_value=[]):
            result = multimodel_agent.get_messages_array(
                "Test question", image_payloads, other_files, sample_question_schema, False
            )

            assert isinstance(result, list)
            assert len(result) == 1  # Should have one HumanMessage
            assert isinstance(result[0], HumanMessage)
            assert isinstance(result[0].content, list)

    def test_get_tools(self, multimodel_agent):
        """Test getting configured tools."""
        mock_tools = [MagicMock(), MagicMock()]
        multimodel_agent._configured_tools = mock_tools

        result = multimodel_agent.get_tools()

        assert result == mock_tools


class TestMCPIntegration:
    """Test cases for MCP (Model Context Protocol) integration."""

    @pytest.mark.asyncio
    @patch("copilot.core.agent.multimodel_agent.MultiServerMCPClient")
    async def test_get_mcp_tools_success(self, mock_mcp_client_class):
        """Test successful MCP tools retrieval."""
        import tempfile

        from copilot.core.agent.multimodel_agent import get_mcp_tools

        # Mock MCP client and tools
        mock_client = MagicMock()
        mock_tool = MagicMock()
        mock_tool.name = "test_mcp_tool"
        # Make get_tools return an async result
        mock_client.get_tools = AsyncMock(return_value=[mock_tool])
        mock_mcp_client_class.return_value = mock_client

        with tempfile.TemporaryDirectory() as temp_dir:
            mcp_config = {
                "filesystem": {
                    "command": "npx",
                    "args": ["-y", "@modelcontextprotocol/server-filesystem", temp_dir],
                }
            }

            result = await get_mcp_tools(mcp_config)

            assert len(result) == 1
            assert result[0] == mock_tool
            mock_mcp_client_class.assert_called_once_with(mcp_config)

    @pytest.mark.asyncio
    @patch("copilot.core.agent.multimodel_agent.MultiServerMCPClient")
    async def test_get_mcp_tools_empty_config(self, mock_mcp_client_class):
        """Test MCP tools retrieval with empty config."""
        from copilot.core.agent.multimodel_agent import get_mcp_tools

        result = await get_mcp_tools({})

        assert result == []
        mock_mcp_client_class.assert_not_called()

    @pytest.mark.asyncio
    @patch("copilot.core.agent.multimodel_agent.MultiServerMCPClient")
    async def test_get_mcp_tools_exception(self, mock_mcp_client_class):
        """Test MCP tools retrieval with exception."""
        from copilot.core.agent.multimodel_agent import get_mcp_tools

        mock_mcp_client_class.side_effect = Exception("Connection failed")

        mcp_config = {"test_server": {"command": "test"}}
        result = await get_mcp_tools(mcp_config)

        assert result == []


class TestCodeActIntegration:
    """Test cases for CodeAct integration."""

    @patch("copilot.core.agent.codeact.create_codeact")
    @patch("copilot.core.agent.eval.code_evaluators.create_pyodide_eval_fn")
    @patch.dict("os.environ", {"COPILOT_USE_PYDOIDE": "true"})
    def test_create_code_act_agent_pyodide(self, mock_pyodide_eval, mock_create_codeact, multimodel_agent):
        """Test CodeAct agent creation with Pyodide evaluator."""
        mock_llm = MagicMock()
        mock_tools = []
        system_prompt = "Test prompt"

        mock_eval_fn = MagicMock()
        mock_pyodide_eval.return_value = mock_eval_fn
        mock_agent = MagicMock()
        mock_create_codeact.return_value = mock_agent

        with patch("copilot.core.threadcontext.ThreadContext.get_data", return_value="test_conversation"):
            result = multimodel_agent._create_code_act_agent(mock_llm, mock_tools, system_prompt)

        mock_pyodide_eval.assert_called_once()
        mock_create_codeact.assert_called_once()
        assert result == mock_agent

    @patch("copilot.core.agent.codeact.create_codeact")
    @patch("copilot.core.agent.eval.code_evaluators.CodeExecutor")
    @patch.dict("os.environ", {"COPILOT_USE_PYDOIDE": "false"})
    def test_create_code_act_agent_original(
        self, mock_code_executor_class, mock_create_codeact, multimodel_agent
    ):
        """Test CodeAct agent creation with original executor."""
        mock_llm = MagicMock()
        mock_tools = []
        system_prompt = "Test prompt"

        mock_executor = MagicMock()
        mock_executor.execute = MagicMock()
        mock_code_executor_class.return_value = mock_executor
        mock_agent = MagicMock()
        mock_create_codeact.return_value = mock_agent

        result = multimodel_agent._create_code_act_agent(mock_llm, mock_tools, system_prompt)

        mock_code_executor_class.assert_called_once_with("original")
        mock_create_codeact.assert_called_once()
        assert result == mock_agent
