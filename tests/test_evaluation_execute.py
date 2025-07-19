"""
Test suite for evaluation package - execute module

Tests to validate execution logic before refactoring.
"""

import argparse
import os
import sys
from unittest.mock import patch

import pytest

# Add evaluation directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "evaluation"))

from execute import DEFAULT_EXECUTIONS, exec_agent


class TestExecAgent:
    """Test cases for exec_agent function"""

    def setup_method(self):
        """Setup test arguments for each test"""
        self.base_args = argparse.Namespace()
        self.base_args.user = "test_user"
        self.base_args.password = "test_password"
        self.base_args.token = "test_token"
        self.base_args.agent_id = "test_agent_123"
        self.base_args.k = 5
        self.base_args.save = None
        self.base_args.skip_evaluators = False
        self.base_args.dataset = "/path/to/dataset"

    @patch("evaluation.execute.evaluate_agent")
    @patch("evaluation.execute.get_agent_config")
    def test_exec_agent_evaluation_mode(self, mock_get_agent_config, mock_evaluate_agent):
        """Test exec_agent in evaluation mode (no save parameter)"""
        # Mock agent config
        mock_agent_config = {"id": "test_agent_123", "name": "Test Agent", "type": "multimodel"}
        mock_get_agent_config.return_value = mock_agent_config

        # Execute
        exec_agent(self.base_args)

        # Verify agent config was retrieved
        mock_get_agent_config.assert_called_once()

        # Verify evaluation was called
        mock_evaluate_agent.assert_called_once()

    @patch("evaluation.execute.save_conversation_from_run")
    @patch("evaluation.execute.get_agent_config")
    def test_exec_agent_save_mode(self, mock_get_agent_config, mock_save_conversation):
        """Test exec_agent in save mode (with save parameter)"""
        # Set save mode
        self.base_args.save = "run_id_123"

        # Mock agent config
        mock_agent_config = {"id": "test_agent_123", "name": "Test Agent"}
        mock_get_agent_config.return_value = mock_agent_config

        # Execute
        exec_agent(self.base_args)

        # Verify save_conversation_from_run was called
        mock_save_conversation.assert_called_once_with("run_id_123")

    @patch("evaluation.execute.get_agent_config")
    def test_exec_agent_with_different_repetitions(self, mock_get_agent_config):
        """Test exec_agent with different repetition values"""
        test_cases = [1, 3, 10, 100]

        for k_value in test_cases:
            self.base_args.k = k_value

            # Mock agent config
            mock_get_agent_config.return_value = {"id": "test"}

            with patch("evaluation.execute.evaluate_agent") as mock_evaluate:
                exec_agent(self.base_args)
                # Verify the repetition value is used correctly
                mock_evaluate.assert_called_once()

    @patch("evaluation.execute.get_agent_config")
    def test_exec_agent_skip_evaluators_flag(self, mock_get_agent_config):
        """Test exec_agent with skip_evaluators flag"""
        self.base_args.skip_evaluators = True

        mock_get_agent_config.return_value = {"id": "test"}

        with patch("evaluation.execute.evaluate_agent") as mock_evaluate:
            exec_agent(self.base_args)
            mock_evaluate.assert_called_once()

    @patch("evaluation.execute.get_agent_config")
    def test_exec_agent_with_missing_agent_id(self, mock_get_agent_config):
        """Test exec_agent behavior when agent_id is missing or invalid"""
        self.base_args.agent_id = None

        # This should either raise an exception or handle gracefully
        with pytest.raises((AttributeError, ValueError, TypeError)):
            exec_agent(self.base_args)

    @patch("evaluation.execute.get_agent_config")
    def test_exec_agent_with_invalid_k_value(self, mock_get_agent_config):
        """Test exec_agent with invalid k (repetitions) value"""
        # Test with string that can't be converted to int
        self.base_args.k = "invalid"

        with pytest.raises((ValueError, TypeError)):
            exec_agent(self.base_args)

    @patch("evaluation.execute.get_agent_config")
    @patch("builtins.print")
    def test_exec_agent_prints_execution_details(self, mock_print, mock_get_agent_config):
        """Test that exec_agent prints execution details"""
        mock_get_agent_config.return_value = {"id": "test"}

        with patch("evaluation.execute.evaluate_agent"):
            exec_agent(self.base_args)

        # Verify that print was called (execution details should be printed)
        assert mock_print.called
        print_calls = [call.args[0] for call in mock_print.call_args_list]

        # Check that user info was printed
        assert any("User:" in call for call in print_calls)

    @patch("evaluation.execute.get_agent_config")
    def test_exec_agent_handles_authentication_params(self, mock_get_agent_config):
        """Test exec_agent handles different authentication parameters"""
        mock_get_agent_config.return_value = {"id": "test"}

        # Test with token only
        args_token_only = argparse.Namespace()
        args_token_only.user = None
        args_token_only.password = None
        args_token_only.token = "test_token"
        args_token_only.agent_id = "test_agent"
        args_token_only.k = 1
        args_token_only.save = None
        args_token_only.skip_evaluators = False
        args_token_only.dataset = "/path"

        with patch("evaluation.execute.evaluate_agent"):
            # Should not raise exception
            exec_agent(args_token_only)

    @patch("evaluation.execute.get_agent_config")
    def test_exec_agent_with_dataset_path(self, mock_get_agent_config):
        """Test exec_agent with different dataset paths"""
        mock_get_agent_config.return_value = {"id": "test"}

        test_paths = ["/absolute/path/to/dataset", "relative/path/to/dataset", "../../relative/path", "."]

        for path in test_paths:
            self.base_args.dataset = path

            with patch("evaluation.execute.evaluate_agent"):
                # Should handle different path formats
                exec_agent(self.base_args)


class TestDefaultExecutions:
    """Test cases for DEFAULT_EXECUTIONS constant"""

    def test_default_executions_value(self):
        """Test that DEFAULT_EXECUTIONS has expected value"""
        assert DEFAULT_EXECUTIONS == 5
        assert isinstance(DEFAULT_EXECUTIONS, int)
        assert DEFAULT_EXECUTIONS > 0


class TestExecuteModuleIntegration:
    """Integration tests for execute module"""

    def test_exec_agent_end_to_end_simulation(self):
        """Test complete exec_agent workflow simulation"""
        # Create mock arguments
        args = argparse.Namespace()
        args.user = "admin"
        args.password = "password"
        args.token = None
        args.agent_id = "AGENT123"
        args.k = 3
        args.save = None
        args.skip_evaluators = False
        args.dataset = "/test/dataset"

        # Mock all dependencies
        with patch("evaluation.execute.get_agent_config") as mock_config, patch(
            "evaluation.execute.evaluate_agent"
        ) as mock_evaluate, patch("builtins.print") as mock_print:
            # Setup mocks
            mock_config.return_value = {"id": "AGENT123", "name": "Test Agent", "type": "multimodel"}

            # Execute
            exec_agent(args)

            # Verify workflow
            mock_config.assert_called_once()
            mock_evaluate.assert_called_once()
            assert mock_print.called

    def test_exec_agent_save_workflow_simulation(self):
        """Test complete save workflow simulation"""
        args = argparse.Namespace()
        args.user = "admin"
        args.password = "password"
        args.token = None
        args.agent_id = "AGENT123"
        args.k = 1
        args.save = "run_xyz_789"
        args.skip_evaluators = True
        args.dataset = "/test/dataset"

        with patch("evaluation.execute.get_agent_config") as mock_config, patch(
            "evaluation.execute.save_conversation_from_run"
        ) as mock_save, patch("builtins.print"):
            mock_config.return_value = {"id": "AGENT123"}

            exec_agent(args)

            mock_config.assert_called_once()
            mock_save.assert_called_once_with("run_xyz_789")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
