"""
Tests for MCP simplified dynamic utilities module.

This module tests the simplified dynamic utilities for MCP server.
"""

from unittest.mock import patch

from copilot.core.mcp.simplified_dynamic_utils import (
    start_simplified_dynamic_mcp_with_cleanup,
)


class TestStartSimplifiedDynamicMcpWithCleanup:
    """Test cases for start_simplified_dynamic_mcp_with_cleanup function."""

    @patch("copilot.core.mcp.simplified_dynamic_utils.setup_simplified_dynamic_mcp_shutdown_handlers")
    @patch("copilot.core.mcp.simplified_dynamic_utils.start_simplified_dynamic_mcp_server")
    @patch("copilot.core.mcp.simplified_dynamic_utils.logger")
    def test_start_simplified_dynamic_mcp_with_cleanup_success(
        self, mock_logger, mock_start_server, mock_setup_handlers
    ):
        """Test successful start with cleanup setup."""
        mock_start_server.return_value = True

        result = start_simplified_dynamic_mcp_with_cleanup()

        # Verify server start was called
        mock_start_server.assert_called_once()

        # Verify shutdown handlers were set up
        mock_setup_handlers.assert_called_once()

        # Verify success is returned
        assert result is True

        # Verify info logging
        mock_logger.info.assert_called_once_with("Simplified Dynamic MCP server setup with cleanup handlers")

    @patch("copilot.core.mcp.simplified_dynamic_utils.setup_simplified_dynamic_mcp_shutdown_handlers")
    @patch("copilot.core.mcp.simplified_dynamic_utils.start_simplified_dynamic_mcp_server")
    @patch("copilot.core.mcp.simplified_dynamic_utils.logger")
    def test_start_simplified_dynamic_mcp_with_cleanup_server_fail(
        self, mock_logger, mock_start_server, mock_setup_handlers
    ):
        """Test behavior when server fails to start."""
        mock_start_server.return_value = False

        result = start_simplified_dynamic_mcp_with_cleanup()

        # Verify server start was called
        mock_start_server.assert_called_once()

        # Verify shutdown handlers were still set up
        mock_setup_handlers.assert_called_once()

        # Verify failure is returned
        assert result is False

        # Verify info logging still happens
        mock_logger.info.assert_called_once_with("Simplified Dynamic MCP server setup with cleanup handlers")

    @patch("copilot.core.mcp.simplified_dynamic_utils.setup_simplified_dynamic_mcp_shutdown_handlers")
    @patch("copilot.core.mcp.simplified_dynamic_utils.start_simplified_dynamic_mcp_server")
    @patch("copilot.core.mcp.simplified_dynamic_utils.logger")
    def test_start_simplified_dynamic_mcp_with_cleanup_exception(
        self, mock_logger, mock_start_server, mock_setup_handlers
    ):
        """Test exception handling in start function."""
        mock_start_server.side_effect = Exception("Server error")

        result = start_simplified_dynamic_mcp_with_cleanup()

        # Verify server start was called
        mock_start_server.assert_called_once()

        # Verify shutdown handlers were set up
        mock_setup_handlers.assert_called_once()

        # Verify failure is returned due to exception
        assert result is False

        # Verify error logging
        mock_logger.error.assert_called_once()

    @patch("copilot.core.mcp.simplified_dynamic_utils.setup_simplified_dynamic_mcp_shutdown_handlers")
    @patch("copilot.core.mcp.simplified_dynamic_utils.start_simplified_dynamic_mcp_server")
    def test_start_simplified_dynamic_mcp_with_cleanup_handlers_exception(
        self, mock_start_server, mock_setup_handlers
    ):
        """Test behavior when shutdown handlers setup fails."""
        mock_start_server.return_value = True
        mock_setup_handlers.side_effect = Exception("Handler error")

        # Should not raise exception, just continue
        result = start_simplified_dynamic_mcp_with_cleanup()

        # Verify both functions were called despite handler error
        mock_start_server.assert_called_once()
        mock_setup_handlers.assert_called_once()

        # Result should still reflect server start status
        assert result is True


class TestModuleIntegration:
    """Integration tests for the simplified dynamic utils module."""

    def test_module_imports(self):
        """Test that all expected functions can be imported."""
        from copilot.core.mcp.simplified_dynamic_utils import (
            start_simplified_dynamic_mcp_with_cleanup,
        )

        # Verify function exists
        assert start_simplified_dynamic_mcp_with_cleanup is not None
        assert callable(start_simplified_dynamic_mcp_with_cleanup)

    def test_function_signature(self):
        """Test that function has expected signature."""
        import inspect

        from copilot.core.mcp.simplified_dynamic_utils import (
            start_simplified_dynamic_mcp_with_cleanup,
        )

        sig = inspect.signature(start_simplified_dynamic_mcp_with_cleanup)

        # Function should have no parameters
        assert len(sig.parameters) == 0

        # Function should return bool
        assert sig.return_annotation == bool or str(sig.return_annotation) == "bool"

    @patch("copilot.core.mcp.simplified_dynamic_utils.setup_simplified_dynamic_mcp_shutdown_handlers")
    @patch("copilot.core.mcp.simplified_dynamic_utils.start_simplified_dynamic_mcp_server")
    def test_real_flow_without_server_start(self, mock_start_server, mock_setup_handlers):
        """Test the actual function flow with mocked dependencies."""
        mock_start_server.return_value = True

        result = start_simplified_dynamic_mcp_with_cleanup()

        # Verify the actual call flow
        assert mock_setup_handlers.called
        assert mock_start_server.called
        assert result is True

    def test_import_from_utils_module(self):
        """Test importing through the utils module."""
        # This validates the import path is correct
        from copilot.core.mcp.simplified_dynamic_utils import (
            setup_simplified_dynamic_mcp_shutdown_handlers,
        )

        assert setup_simplified_dynamic_mcp_shutdown_handlers is not None
        assert callable(setup_simplified_dynamic_mcp_shutdown_handlers)


class TestErrorHandling:
    """Test error handling scenarios."""

    @patch("copilot.core.mcp.simplified_dynamic_utils.logger")
    @patch("copilot.core.mcp.simplified_dynamic_utils.setup_simplified_dynamic_mcp_shutdown_handlers")
    @patch("copilot.core.mcp.simplified_dynamic_utils.start_simplified_dynamic_mcp_server")
    def test_multiple_exception_types(self, mock_start_server, mock_setup_handlers, mock_logger):
        """Test handling of different exception types."""
        # Test with different exceptions
        exceptions = [ValueError("Value error"), RuntimeError("Runtime error"), OSError("OS error")]

        for exception in exceptions:
            mock_start_server.side_effect = exception
            mock_setup_handlers.reset_mock()
            mock_logger.reset_mock()

            result = start_simplified_dynamic_mcp_with_cleanup()

            assert result is False
            mock_logger.error.assert_called_once()

    @patch("copilot.core.mcp.simplified_dynamic_utils.setup_simplified_dynamic_mcp_shutdown_handlers")
    @patch("copilot.core.mcp.simplified_dynamic_utils.start_simplified_dynamic_mcp_server")
    @patch("copilot.core.mcp.simplified_dynamic_utils.logger")
    def test_logging_behavior(self, mock_logger, mock_start_server, mock_setup_handlers):
        """Test that logging behavior is correct."""
        mock_start_server.return_value = True

        start_simplified_dynamic_mcp_with_cleanup()

        # Should have exactly one info call
        assert mock_logger.info.call_count == 1
        # Should have no error calls when successful
        assert mock_logger.error.call_count == 0

    @patch("copilot.core.mcp.simplified_dynamic_utils.setup_simplified_dynamic_mcp_shutdown_handlers")
    @patch("copilot.core.mcp.simplified_dynamic_utils.start_simplified_dynamic_mcp_server")
    @patch("copilot.core.mcp.simplified_dynamic_utils.logger")
    def test_cleanup_on_exception(self, mock_logger, mock_start_server, mock_setup_handlers):
        """Test that cleanup still happens even when exceptions occur."""
        mock_start_server.side_effect = Exception("Test exception")

        # Function should not raise, should handle exception gracefully
        result = start_simplified_dynamic_mcp_with_cleanup()

        # Verify both setup calls happened despite exception
        mock_setup_handlers.assert_called_once()
        mock_start_server.assert_called_once()

        # Verify result is False due to exception
        assert result is False

        # Verify error was logged
        mock_logger.error.assert_called_once()
