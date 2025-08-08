"""
Tests for MCP utils module.

This module tests the utility functions for MCP server integration.
"""

import atexit
import signal
import sys
from unittest.mock import MagicMock, patch

import pytest
from copilot.core.mcp.utils import (
    is_mcp_enabled,
    setup_mcp_shutdown_handlers,
    start_mcp_with_cleanup,
)


class TestSetupMcpShutdownHandlers:
    """Test cases for setup_mcp_shutdown_handlers function."""

    def test_setup_mcp_shutdown_handlers_first_call(self):
        """Test that handlers are set up correctly on first call."""
        # Reset the global flag
        import copilot.core.mcp.utils as utils_module

        utils_module._shutdown_registered = False

        with patch.object(atexit, "register") as mock_atexit, patch.object(signal, "signal") as mock_signal:
            setup_mcp_shutdown_handlers()

            # Verify atexit handler was registered
            mock_atexit.assert_called_once()

            # Verify signal handlers were set up
            assert mock_signal.call_count == 2
            mock_signal.assert_any_call(signal.SIGTERM, mock_signal.call_args_list[0][0][1])
            mock_signal.assert_any_call(signal.SIGINT, mock_signal.call_args_list[1][0][1])

            # Verify global flag is set
            assert utils_module._shutdown_registered is True

    def test_setup_mcp_shutdown_handlers_already_registered(self):
        """Test that handlers are not set up again if already registered."""
        # Set the global flag
        import copilot.core.mcp.utils as utils_module

        utils_module._shutdown_registered = True

        with patch.object(atexit, "register") as mock_atexit, patch.object(signal, "signal") as mock_signal:
            setup_mcp_shutdown_handlers()

            # Verify no handlers were registered
            mock_atexit.assert_not_called()
            mock_signal.assert_not_called()

    def test_shutdown_mcp_function(self):
        """Test the internal shutdown_mcp function."""
        import copilot.core.mcp.utils as utils_module

        utils_module._shutdown_registered = False

        mock_manager = MagicMock()
        mock_manager.is_running = True

        with patch.object(atexit, "register") as mock_atexit, patch.object(signal, "signal"), patch(
            "copilot.core.mcp.utils.get_simplified_dynamic_mcp_manager", return_value=mock_manager
        ), patch("builtins.print") as mock_print:
            setup_mcp_shutdown_handlers()

            # Get the registered shutdown function
            shutdown_func = mock_atexit.call_args[0][0]

            # Call it
            shutdown_func()

            # Verify manager.stop() was called
            mock_manager.stop.assert_called_once()
            mock_print.assert_called_once_with("\033[94m Shutting down MCP server... \033[00m")

    def test_shutdown_mcp_function_not_running(self):
        """Test the internal shutdown_mcp function when server is not running."""
        import copilot.core.mcp.utils as utils_module

        utils_module._shutdown_registered = False

        mock_manager = MagicMock()
        mock_manager.is_running = False

        with patch.object(atexit, "register") as mock_atexit, patch.object(signal, "signal"), patch(
            "copilot.core.mcp.utils.get_simplified_dynamic_mcp_manager", return_value=mock_manager
        ), patch("builtins.print") as mock_print:
            setup_mcp_shutdown_handlers()

            # Get the registered shutdown function
            shutdown_func = mock_atexit.call_args[0][0]

            # Call it
            shutdown_func()

            # Verify manager.stop() was not called
            mock_manager.stop.assert_not_called()
            mock_print.assert_not_called()

    def test_signal_handler_calls_shutdown_and_exit(self):
        """Test that signal handler calls shutdown and exits."""
        import copilot.core.mcp.utils as utils_module

        utils_module._shutdown_registered = False

        mock_manager = MagicMock()
        mock_manager.is_running = True

        with patch.object(atexit, "register"), patch.object(signal, "signal") as mock_signal, patch(
            "copilot.core.mcp.utils.get_simplified_dynamic_mcp_manager", return_value=mock_manager
        ), patch.object(sys, "exit") as mock_exit, patch("builtins.print"):
            setup_mcp_shutdown_handlers()

            # Get the signal handler for SIGTERM
            signal_handler = None
            for call in mock_signal.call_args_list:
                if call[0][0] == signal.SIGTERM:
                    signal_handler = call[0][1]
                    break

            assert signal_handler is not None

            # Call the signal handler
            signal_handler(signal.SIGTERM, None)

            # Verify shutdown was called and sys.exit was called
            mock_manager.stop.assert_called_once()
            mock_exit.assert_called_once_with(0)


class TestIsMcpEnabled:
    """Test cases for is_mcp_enabled function."""

    def test_is_mcp_enabled_always_returns_true(self):
        """Test that is_mcp_enabled always returns True."""
        result = is_mcp_enabled()
        assert result is True


class TestStartMcpWithCleanup:
    """Test cases for start_mcp_with_cleanup function."""

    def test_start_mcp_with_cleanup_success(self):
        """Test successful start of MCP server with cleanup."""
        with patch("copilot.core.mcp.utils.setup_mcp_shutdown_handlers") as mock_setup, patch(
            "copilot.core.mcp.start_simplified_dynamic_mcp_server", return_value=True
        ) as mock_start:
            result = start_mcp_with_cleanup()

            # Verify setup and start were called
            mock_setup.assert_called_once()
            mock_start.assert_called_once()

            # Verify return value
            assert result is True

    def test_start_mcp_with_cleanup_failure(self):
        """Test failed start of MCP server with cleanup."""
        with patch("copilot.core.mcp.utils.setup_mcp_shutdown_handlers") as mock_setup, patch(
            "copilot.core.mcp.start_simplified_dynamic_mcp_server", return_value=False
        ) as mock_start:
            result = start_mcp_with_cleanup()

            # Verify setup and start were called
            mock_setup.assert_called_once()
            mock_start.assert_called_once()

            # Verify return value
            assert result is False

    def test_start_mcp_with_cleanup_exception_in_start(self):
        """Test exception handling during MCP server start."""
        with patch("copilot.core.mcp.utils.setup_mcp_shutdown_handlers") as mock_setup, patch(
            "copilot.core.mcp.start_simplified_dynamic_mcp_server", side_effect=Exception("Test error")
        ) as mock_start:
            with pytest.raises(Exception, match="Test error"):
                start_mcp_with_cleanup()

            # Verify setup was called even if start failed
            mock_setup.assert_called_once()
            mock_start.assert_called_once()

    def test_start_mcp_with_cleanup_exception_in_setup(self):
        """Test exception handling during setup."""
        with patch(
            "copilot.core.mcp.utils.setup_mcp_shutdown_handlers", side_effect=Exception("Setup error")
        ) as mock_setup, patch("copilot.core.mcp.start_simplified_dynamic_mcp_server") as mock_start:
            with pytest.raises(Exception, match="Setup error"):
                start_mcp_with_cleanup()

            # Verify setup was called but start was not
            mock_setup.assert_called_once()
            mock_start.assert_not_called()


class TestModuleIntegration:
    """Integration tests for the utils module."""

    def test_module_imports(self):
        """Test that all expected functions can be imported."""
        from copilot.core.mcp.utils import (
            is_mcp_enabled,
            setup_mcp_shutdown_handlers,
            start_mcp_with_cleanup,
        )

        # Verify functions exist and are callable
        assert callable(is_mcp_enabled)
        assert callable(setup_mcp_shutdown_handlers)
        assert callable(start_mcp_with_cleanup)

    def test_global_state_isolation(self):
        """Test that global state can be reset between tests."""
        import copilot.core.mcp.utils as utils_module

        # Reset state
        utils_module._shutdown_registered = False

        # Verify initial state
        assert utils_module._shutdown_registered is False

        with patch.object(atexit, "register"), patch.object(signal, "signal"):
            setup_mcp_shutdown_handlers()

            # Verify state changed
            assert utils_module._shutdown_registered is True

            # Reset for next test
            utils_module._shutdown_registered = False
