"""
Tests for agent utils module.

This module tests the utility functions for agent configuration and model initialization.
"""

import os
from unittest.mock import MagicMock, Mock, patch

from copilot.core.schemas import AssistantSchema, QuestionSchema
from copilot.core.utils.agent import (
    get_full_question,
    get_llm,
    get_model_config,
    get_structured_output,
)


class TestGetFullQuestion:
    """Test cases for get_full_question function."""

    def test_get_full_question_without_local_files(self):
        """Test get_full_question when no local file IDs are provided."""
        question = QuestionSchema(question="What is the capital of France?", local_file_ids=None)

        result = get_full_question(question)
        assert result == "What is the capital of France?"

    def test_get_full_question_with_empty_local_files(self):
        """Test get_full_question when local file IDs list is empty."""
        question = QuestionSchema(question="What is the capital of France?", local_file_ids=[])

        result = get_full_question(question)
        assert result == "What is the capital of France?"

    def test_get_full_question_with_single_local_file(self):
        """Test get_full_question with a single local file ID."""
        question = QuestionSchema(question="Analyze this file", local_file_ids=["/path/to/file.txt"])

        with (
            patch("os.getcwd", return_value="/current/dir"),
            patch("os.path.dirname", return_value="/parent"),
        ):
            result = get_full_question(question)

            expected = """Analyze this file
Local Files Ids for Context:
 - /parent/path/to/file.txt"""
            assert result == expected

    def test_get_full_question_with_multiple_local_files(self):
        """Test get_full_question with multiple local file IDs."""
        question = QuestionSchema(
            question="Compare these files", local_file_ids=["/file1.txt", "/file2.py", "/file3.json"]
        )

        with (
            patch("os.getcwd", return_value="/current/dir"),
            patch("os.path.dirname", return_value="/parent"),
        ):
            result = get_full_question(question)

            expected = """Compare these files
Local Files Ids for Context:
 - /parent/file1.txt
 - /parent/file2.py
 - /parent/file3.json"""
            assert result == expected

    def test_get_full_question_preserves_original_question_structure(self):
        """Test that the original question structure is preserved."""
        complex_question = "This is a multi-line\nquestion with\nspecial characters: !@#$%"
        question = QuestionSchema(question=complex_question, local_file_ids=["/test.txt"])

        with patch("os.getcwd", return_value="/current"), patch("os.path.dirname", return_value="/parent"):
            result = get_full_question(question)

            assert result.startswith(complex_question)
            assert "Local Files Ids for Context:" in result


class TestGetLlm:
    """Test cases for get_llm function."""

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    @patch("copilot.core.utils.agent.get_proxy_url")
    def test_get_llm_with_openai_provider(
        self, mock_get_proxy_url, mock_get_model_config, mock_init_chat_model
    ):
        """Test get_llm with OpenAI provider."""
        mock_get_proxy_url.return_value = None
        mock_get_model_config.return_value = {}
        mock_llm = Mock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("gpt-4", "openai", 0.7)

        mock_init_chat_model.assert_called_once_with(
            model_provider="openai",
            model="gpt-4",
            temperature=0.7,
            base_url=None,
            model_kwargs={"stream_options": {"include_usage": True}},
            streaming=True,
        )
        mock_get_model_config.assert_called_once_with("openai", "gpt-4")
        assert result == mock_llm

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    @patch("copilot.core.utils.agent.get_proxy_url")
    def test_get_llm_with_none_provider_non_gpt_model(
        self, mock_get_proxy_url, mock_get_model_config, mock_init_chat_model
    ):
        """Test get_llm with None provider and non-GPT model."""
        mock_get_proxy_url.return_value = None
        mock_get_model_config.return_value = {}
        mock_llm = MagicMock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("claude-3", None, 0.5)

        mock_init_chat_model.assert_called_once_with(
            model_provider=None,
            model="claude-3",
            temperature=0.5,
            base_url=None,
            model_kwargs={"stream_options": {"include_usage": True}},
            streaming=True,
        )
        mock_get_model_config.assert_called_once_with(None, "claude-3")
        assert result == mock_llm

    @patch.dict(os.environ, {"COPILOT_OLLAMA_HOST": "localhost", "COPILOT_OLLAMA_PORT": "11434"})
    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    def test_get_llm_with_ollama_provider(self, mock_get_model_config, mock_init_chat_model):
        """Test get_llm with Ollama provider."""
        mock_get_model_config.return_value = {}
        mock_llm = Mock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("llama2", "ollama", 0.3)

        mock_init_chat_model.assert_called_once_with(
            model_provider="ollama",
            model="llama2",
            temperature=0.3,
            streaming=True,
            base_url="localhost:11434",
            model_kwargs={"stream_options": {"include_usage": True}},
        )
        mock_get_model_config.assert_called_once_with("ollama", "llama2")
        assert result == mock_llm

    @patch.dict(os.environ, {}, clear=True)
    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    def test_get_llm_with_ollama_default_host_port(self, mock_get_model_config, mock_init_chat_model):
        """Test get_llm with Ollama provider using default host and port."""
        mock_get_model_config.return_value = {}
        mock_llm = Mock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("llama2", "ollama", 0.8)

        mock_init_chat_model.assert_called_once_with(
            model_provider="ollama",
            model="llama2",
            temperature=0.8,
            streaming=True,
            base_url="ollama:11434",
            model_kwargs={"stream_options": {"include_usage": True}},
        )
        assert result == mock_llm

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    def test_get_llm_with_max_tokens_config(self, mock_get_model_config, mock_init_chat_model):
        """Test get_llm with model config that includes max_tokens."""
        mock_get_model_config.return_value = {"max_tokens": "2048"}
        mock_llm = MagicMock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("claude-3", "anthropic", 0.6)

        assert result.max_tokens == 2048
        mock_get_model_config.assert_called_once_with("anthropic", "claude-3")

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    def test_get_llm_with_no_model_config(self, mock_get_model_config, mock_init_chat_model):
        """Test get_llm when no model config is found."""
        mock_get_model_config.return_value = None
        mock_llm = MagicMock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("unknown-model", "unknown-provider", 0.1)

        assert result == mock_llm
        # Verify max_tokens was not accessed/set since no config was returned
        assert "max_tokens" not in mock_llm.__dict__

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    def test_get_llm_with_empty_model_config(self, mock_get_model_config, mock_init_chat_model):
        """Test get_llm with empty model config."""
        mock_get_model_config.return_value = {}
        mock_llm = MagicMock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("test-model", "test-provider", 0.9)

        assert result == mock_llm
        # Verify max_tokens was not accessed/set since config is empty
        assert "max_tokens" not in mock_llm.__dict__

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    def test_get_llm_with_model_config_without_max_tokens(self, mock_get_model_config, mock_init_chat_model):
        """Test get_llm with model config that doesn't include max_tokens."""
        mock_get_model_config.return_value = {"some_other_param": "value"}
        mock_llm = MagicMock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("test-model", "test-provider", 0.4)

        assert result == mock_llm
        # Verify max_tokens was not accessed/set since it's not in config
        assert "max_tokens" not in mock_llm.__dict__


class TestGetModelConfig:
    """Test cases for get_model_config function."""

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_with_valid_config(self, mock_get_extra_info):
        """Test get_model_config with valid configuration."""
        mock_extra_info = {"model_config": {"openai": {"gpt-4": {"max_tokens": 4096, "temperature": 0.7}}}}
        mock_get_extra_info.return_value = mock_extra_info

        result = get_model_config("openai", "gpt-4")

        expected = {"max_tokens": 4096, "temperature": 0.7}
        assert result == expected
        mock_get_extra_info.assert_called_once()

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_with_null_provider(self, mock_get_extra_info):
        """Test get_model_config with None provider (should use 'null' as key)."""
        mock_extra_info = {"model_config": {"null": {"local-model": {"max_tokens": 2048}}}}
        mock_get_extra_info.return_value = mock_extra_info

        result = get_model_config(None, "local-model")

        expected = {"max_tokens": 2048}
        assert result == expected

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_no_extra_info(self, mock_get_extra_info):
        """Test get_model_config when extra_info is None."""
        mock_get_extra_info.return_value = None

        result = get_model_config("openai", "gpt-4")

        assert result == {}
        mock_get_extra_info.assert_called_once()

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_no_model_config_key(self, mock_get_extra_info):
        """Test get_model_config when model_config key is missing."""
        mock_extra_info = {"other_key": "other_value"}
        mock_get_extra_info.return_value = mock_extra_info

        result = get_model_config("openai", "gpt-4")

        assert result == {}

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_model_config_is_none(self, mock_get_extra_info):
        """Test get_model_config when model_config is None."""
        mock_extra_info = {"model_config": None}
        mock_get_extra_info.return_value = mock_extra_info

        result = get_model_config("openai", "gpt-4")

        assert result == {}

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_provider_not_found(self, mock_get_extra_info):
        """Test get_model_config when provider is not found in config."""
        mock_extra_info = {"model_config": {"anthropic": {"claude-3": {"max_tokens": 8192}}}}
        mock_get_extra_info.return_value = mock_extra_info

        result = get_model_config("openai", "gpt-4")

        assert result == {}

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_model_not_found(self, mock_get_extra_info):
        """Test get_model_config when model is not found for provider."""
        mock_extra_info = {"model_config": {"openai": {"gpt-3.5-turbo": {"max_tokens": 4096}}}}
        mock_get_extra_info.return_value = mock_extra_info

        result = get_model_config("openai", "gpt-4")

        assert result == {}

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_empty_provider_configs(self, mock_get_extra_info):
        """Test get_model_config when provider config is empty."""
        mock_extra_info = {"model_config": {"openai": {}}}
        mock_get_extra_info.return_value = mock_extra_info

        result = get_model_config("openai", "gpt-4")

        assert result == {}

    @patch("copilot.core.utils.agent.etendo_utils.get_extra_info")
    def test_get_model_config_multiple_providers_and_models(self, mock_get_extra_info):
        """Test get_model_config with multiple providers and models."""
        mock_extra_info = {
            "model_config": {
                "openai": {
                    "gpt-4": {"max_tokens": 8192, "temperature": 0.7},
                    "gpt-3.5-turbo": {"max_tokens": 4096, "temperature": 0.9},
                },
                "anthropic": {"claude-3": {"max_tokens": 8192}},
                "ollama": {"llama2": {"context_length": 4096}},
            }
        }
        mock_get_extra_info.return_value = mock_extra_info

        # Test OpenAI GPT-4
        result1 = get_model_config("openai", "gpt-4")
        assert result1 == {"max_tokens": 8192, "temperature": 0.7}

        # Test OpenAI GPT-3.5-turbo
        result2 = get_model_config("openai", "gpt-3.5-turbo")
        assert result2 == {"max_tokens": 4096, "temperature": 0.9}

        # Test Anthropic Claude-3
        result3 = get_model_config("anthropic", "claude-3")
        assert result3 == {"max_tokens": 8192}

        # Test Ollama Llama2
        result4 = get_model_config("ollama", "llama2")
        assert result4 == {"context_length": 4096}


class TestModuleIntegration:
    """Integration tests for the agent utils module."""

    def test_module_imports(self):
        """Test that all expected functions can be imported."""
        from copilot.core.utils.agent import (
            get_full_question,
            get_llm,
            get_model_config,
        )

        # Verify functions exist and are callable
        assert callable(get_full_question)
        assert callable(get_llm)
        assert callable(get_model_config)

    def test_question_schema_compatibility(self):
        """Test compatibility with QuestionSchema."""
        from copilot.core.schemas import QuestionSchema

        # Test that QuestionSchema can be instantiated as expected
        question = QuestionSchema(question="Test question", local_file_ids=None)

        assert question.question == "Test question"
        assert question.local_file_ids is None

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    @patch("copilot.core.utils.agent.get_proxy_url")
    def test_real_workflow_simulation(self, mock_get_proxy_url, mock_get_model_config, mock_init_chat_model):
        """Test a realistic workflow combining multiple functions."""
        # Setup mocks
        mock_get_proxy_url.return_value = None
        mock_get_model_config.return_value = {"max_tokens": 4096}
        mock_llm = MagicMock()
        mock_init_chat_model.return_value = mock_llm

        # Create a question with local files
        question = QuestionSchema(
            question="Analyze the code structure", local_file_ids=["/src/main.py", "/tests/test_main.py"]
        )

        # Get full question
        with patch("os.getcwd", return_value="/workspace"), patch("os.path.dirname", return_value="/parent"):
            full_question = get_full_question(question)

        # Initialize LLM
        llm = get_llm("gpt-4", "openai", 0.7)

        # Verify results
        assert "Analyze the code structure" in full_question
        assert "Local Files Ids for Context:" in full_question
        assert "/parent/src/main.py" in full_question
        assert "/parent/tests/test_main.py" in full_question

        assert llm == mock_llm
        assert llm.max_tokens == 4096

        # Verify function calls
        mock_init_chat_model.assert_called_once_with(
            model_provider="openai",
            model="gpt-4",
            temperature=0.7,
            base_url=None,
            model_kwargs={"stream_options": {"include_usage": True}},
            streaming=True,
        )
        mock_get_model_config.assert_called_once_with("openai", "gpt-4")


class TestGetStructuredOutput:
    """Test cases for get_structured_output function."""

    def test_get_structured_output_with_none_schema(self):
        """Test get_structured_output when structured_output_json_schema is None."""
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=None)

        result = get_structured_output(agent_config)

        assert result is None

    def test_get_structured_output_with_valid_json_schema(self):
        """Test get_structured_output with a valid JSON schema string."""
        json_schema_str = (
            '{"type": "object", "properties": {"name": {"type": "string"}, "age": {"type": "integer"}}}'
        )
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        expected = {"type": "object", "properties": {"name": {"type": "string"}, "age": {"type": "integer"}}}
        assert result == expected

    def test_get_structured_output_with_complex_nested_schema(self):
        """Test get_structured_output with a complex nested JSON schema."""
        json_schema_str = """{
            "type": "object",
            "properties": {
                "user": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "address": {
                            "type": "object",
                            "properties": {
                                "street": {"type": "string"},
                                "city": {"type": "string"}
                            }
                        }
                    }
                },
                "items": {
                    "type": "array",
                    "items": {"type": "string"}
                }
            }
        }"""
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        assert result is not None
        assert result["type"] == "object"
        assert "user" in result["properties"]
        assert "items" in result["properties"]
        assert result["properties"]["user"]["properties"]["address"]["properties"]["city"]["type"] == "string"
        assert result["properties"]["items"]["type"] == "array"

    def test_get_structured_output_with_invalid_json(self):
        """Test get_structured_output with malformed JSON string."""
        json_schema_str = '{"type": "object", "properties": {'  # Invalid JSON - missing closing braces
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        with patch("copilot.core.utils.agent.copilot_error") as mock_error:
            result = get_structured_output(agent_config)

            assert result is None
            mock_error.assert_called_once()
            call_args = mock_error.call_args[0][0]
            assert "Error parsing structured output schema" in call_args
            assert "falling back to default" in call_args

    def test_get_structured_output_with_empty_string(self):
        """Test get_structured_output with empty string."""
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema="")

        with patch("copilot.core.utils.agent.copilot_error") as mock_error:
            result = get_structured_output(agent_config)

            assert result is None
            mock_error.assert_called_once()

    def test_get_structured_output_with_json_array(self):
        """Test get_structured_output with a JSON array schema."""
        json_schema_str = '[{"type": "string"}, {"type": "number"}]'
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        expected = [{"type": "string"}, {"type": "number"}]
        assert result == expected

    def test_get_structured_output_with_json_primitive(self):
        """Test get_structured_output with a primitive JSON value."""
        json_schema_str = '"simple_string"'
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        assert result == "simple_string"

    def test_get_structured_output_with_boolean_value(self):
        """Test get_structured_output with boolean JSON value."""
        json_schema_str = "true"
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        assert result is True

    def test_get_structured_output_with_null_value(self):
        """Test get_structured_output with null JSON value."""
        json_schema_str = "null"
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        assert result is None

    def test_get_structured_output_with_numeric_value(self):
        """Test get_structured_output with numeric JSON value."""
        json_schema_str = "42"
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        assert result == 42

    def test_get_structured_output_with_unicode_characters(self):
        """Test get_structured_output with Unicode characters in JSON."""
        json_schema_str = '{"message": "Hello 世界 🌍", "emoji": "🚀"}'
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        assert result["message"] == "Hello 世界 🌍"
        assert result["emoji"] == "🚀"

    def test_get_structured_output_with_escaped_characters(self):
        """Test get_structured_output with escaped characters in JSON."""
        json_schema_str = r'{"path": "C:\\Users\\test", "quote": "She said \"Hello\""}'
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        assert result["path"] == r"C:\Users\test"
        assert result["quote"] == 'She said "Hello"'

    def test_get_structured_output_with_whitespace(self):
        """Test get_structured_output with extra whitespace in JSON."""
        json_schema_str = """
        {
            "type"  :  "object"  ,
            "properties"  :  {
                "name"  :  {  "type"  :  "string"  }
            }
        }
        """
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        result = get_structured_output(agent_config)

        assert result["type"] == "object"
        assert "properties" in result
        assert result["properties"]["name"]["type"] == "string"

    def test_get_structured_output_error_handling_preserves_error_message(self):
        """Test that error handling includes the original error message."""
        json_schema_str = '{"invalid": }'  # Invalid JSON
        agent_config = AssistantSchema(name="test_agent", structured_output_json_schema=json_schema_str)

        with patch("copilot.core.utils.agent.copilot_error") as mock_error:
            result = get_structured_output(agent_config)

            assert result is None
            mock_error.assert_called_once()
            error_message = mock_error.call_args[0][0]
            assert "Error parsing structured output schema" in error_message
            assert "Error:" in error_message

    def test_get_structured_output_with_question_schema(self):
        """Test get_structured_output with QuestionSchema (inherits from AssistantSchema)."""
        json_schema_str = '{"type": "response", "format": "text"}'
        question_config = QuestionSchema(
            question="Test question", structured_output_json_schema=json_schema_str
        )

        result = get_structured_output(question_config)

        expected = {"type": "response", "format": "text"}
        assert result == expected
