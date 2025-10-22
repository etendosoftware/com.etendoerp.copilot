"""
Tests for agent utils module.

This module tests the utility functions for agent configuration and model initialization.
"""

import os
from unittest.mock import MagicMock, Mock, patch

from copilot.core.schemas import QuestionSchema
from copilot.core.utils.agent import get_full_question, get_llm, get_model_config


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

        with patch("os.getcwd", return_value="/current/dir"), patch(
            "os.path.dirname", return_value="/parent"
        ):
            result = get_full_question(question)

            expected = "Analyze this file\n" "Local Files Ids for Context:\n" " - /parent/path/to/file.txt"
            assert result == expected

    def test_get_full_question_with_multiple_local_files(self):
        """Test get_full_question with multiple local file IDs."""
        question = QuestionSchema(
            question="Compare these files", local_file_ids=["/file1.txt", "/file2.py", "/file3.json"]
        )

        with patch("os.getcwd", return_value="/current/dir"), patch(
            "os.path.dirname", return_value="/parent"
        ):
            result = get_full_question(question)

            expected = (
                "Compare these files\n"
                "Local Files Ids for Context:\n"
                " - /parent/file1.txt\n"
                " - /parent/file2.py\n"
                " - /parent/file3.json"
            )
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
        mock_get_proxy_url.return_value = "https://proxy.example.com"
        mock_get_model_config.return_value = {}
        mock_llm = Mock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("gpt-4", "openai", 0.7)

        mock_init_chat_model.assert_called_once_with(
            model_provider="openai", model="gpt-4", temperature=0.7, base_url="https://proxy.example.com"
        )
        mock_get_model_config.assert_called_once_with("openai", "gpt-4")
        assert result == mock_llm

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    @patch("copilot.core.utils.agent.get_proxy_url")
    def test_get_llm_with_gpt_model_no_provider_fixed(
        self, mock_get_proxy_url, mock_get_model_config, mock_init_chat_model
    ):
        """Test get_llm with GPT model but no provider (should default to openai) - fixed version."""
        mock_get_proxy_url.return_value = "https://proxy.example.com"
        mock_get_model_config.return_value = {}
        mock_llm = MagicMock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("gpt-3.5-turbo", None, 0.5)

        mock_init_chat_model.assert_called_once_with(
            model_provider="openai",
            model="gpt-3.5-turbo",
            temperature=0.5,
            base_url="https://proxy.example.com",
        )
        mock_get_model_config.assert_called_once_with("openai", "gpt-3.5-turbo")
        assert result == mock_llm

    @patch("copilot.core.utils.agent.init_chat_model")
    @patch("copilot.core.utils.agent.get_model_config")
    @patch("copilot.core.utils.agent.get_proxy_url")
    def test_get_llm_with_none_provider_non_gpt_model(
        self, mock_get_proxy_url, mock_get_model_config, mock_init_chat_model
    ):
        """Test get_llm with None provider and non-GPT model."""
        mock_get_proxy_url.return_value = "https://proxy.example.com"
        mock_get_model_config.return_value = {}
        mock_llm = MagicMock()
        mock_init_chat_model.return_value = mock_llm

        result = get_llm("claude-3", None, 0.5)

        mock_init_chat_model.assert_called_once_with(
            model_provider=None, model="claude-3", temperature=0.5, base_url="https://proxy.example.com"
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
            model_provider="ollama", model="llama2", temperature=0.8, streaming=True, base_url="ollama:11434"
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
        mock_get_proxy_url.return_value = "https://api.openai.com/v1"
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
            model_provider="openai", model="gpt-4", temperature=0.7, base_url="https://api.openai.com/v1"
        )
        mock_get_model_config.assert_called_once_with("openai", "gpt-4")
