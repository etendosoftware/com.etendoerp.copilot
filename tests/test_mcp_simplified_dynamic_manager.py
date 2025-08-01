"""
Tests for MCP simplified dynamic manager module.

This module tests the SimplifiedDynamicMCPManager class and related functions.
"""

import os
import threading
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from copilot.core.mcp.simplified_dynamic_manager import (
    SimplifiedDynamicMCPManager,
    get_simplified_dynamic_mcp_manager,
    start_simplified_dynamic_mcp_server,
    stop_simplified_dynamic_mcp_server,
)


class TestSimplifiedDynamicMCPManager:
    """Test cases for SimplifiedDynamicMCPManager class."""

    def test_manager_initialization(self):
        """Test that manager initializes correctly."""
        manager = SimplifiedDynamicMCPManager()

        assert manager.server is None
        assert manager.thread is None
        assert manager.loop is None
        assert manager.is_running is False

    def test_start_success(self):
        """Test successful server start."""
        manager = SimplifiedDynamicMCPManager()

        with patch.object(threading, "Thread") as mock_thread_class, patch.dict(
            os.environ, {"COPILOT_PORT_MCP": "5006"}
        ), patch("builtins.print"):
            mock_thread = MagicMock()
            mock_thread_class.return_value = mock_thread

            result = manager.start()

            assert result is True
            assert manager.is_running is True
            assert manager.thread == mock_thread

            # Verify thread was created with correct parameters
            mock_thread_class.assert_called_once_with(
                target=manager._run_server_in_thread,
                args=("0.0.0.0", 5006),
                daemon=True,
                name="Simplified-Dynamic-MCP-Server",
            )
            mock_thread.start.assert_called_once()

    def test_start_custom_host_port(self):
        """Test server start with custom host and port."""
        manager = SimplifiedDynamicMCPManager()

        with patch.object(threading, "Thread") as mock_thread_class, patch("builtins.print"):
            mock_thread = MagicMock()
            mock_thread_class.return_value = mock_thread

            result = manager.start(host="127.0.0.1", port=8080)

            assert result is True
            assert manager.is_running is True

            # Verify thread was created with custom parameters
            mock_thread_class.assert_called_once_with(
                target=manager._run_server_in_thread,
                args=("127.0.0.1", 8080),
                daemon=True,
                name="Simplified-Dynamic-MCP-Server",
            )

    def test_start_already_running(self):
        """Test starting server when already running."""
        manager = SimplifiedDynamicMCPManager()
        manager.is_running = True

        with patch.object(threading, "Thread") as mock_thread_class, patch(
            "copilot.core.mcp.simplified_dynamic_manager.logger"
        ) as mock_logger:
            result = manager.start()

            assert result is False
            mock_thread_class.assert_not_called()
            mock_logger.warning.assert_called_once()

    def test_start_thread_creation_error(self):
        """Test handling of thread creation error."""
        manager = SimplifiedDynamicMCPManager()

        with patch.object(threading, "Thread", side_effect=Exception("Thread error")), patch(
            "copilot.core.mcp.simplified_dynamic_manager.logger"
        ) as mock_logger, patch("builtins.print"):
            result = manager.start()

            assert result is False
            assert manager.is_running is False
            mock_logger.error.assert_called_once()

    def test_start_port_from_environment(self):
        """Test that port is correctly read from environment variable."""
        manager = SimplifiedDynamicMCPManager()

        with patch.object(threading, "Thread") as mock_thread_class, patch.dict(
            os.environ, {"COPILOT_PORT_MCP": "9999"}
        ), patch("builtins.print"):
            mock_thread = MagicMock()
            mock_thread_class.return_value = mock_thread

            manager.start()

            # Verify thread was created with environment port
            args = mock_thread_class.call_args[1]["args"]
            assert args == ("0.0.0.0", 9999)

    def test_start_default_port(self):
        """Test that default port is used when environment variable is not set."""
        manager = SimplifiedDynamicMCPManager()

        with patch.object(threading, "Thread") as mock_thread_class, patch.dict(
            os.environ, {}, clear=True
        ), patch("builtins.print"):
            mock_thread = MagicMock()
            mock_thread_class.return_value = mock_thread

            manager.start()

            # Verify thread was created with default port
            args = mock_thread_class.call_args[1]["args"]
            assert args == ("0.0.0.0", 5006)

    def test_stop_running_server(self):
        """Test stopping a running server."""
        manager = SimplifiedDynamicMCPManager()
        manager.is_running = True

        mock_loop = MagicMock()
        mock_loop.is_closed.return_value = False
        manager.loop = mock_loop

        with patch("copilot.core.mcp.simplified_dynamic_manager.logger") as mock_logger, patch(
            "builtins.print"
        ):
            manager.stop()

            mock_loop.call_soon_threadsafe.assert_called_once_with(mock_loop.stop)
            mock_logger.info.assert_called_once()

    def test_stop_not_running(self):
        """Test stopping when server is not running."""
        manager = SimplifiedDynamicMCPManager()
        manager.is_running = False

        with patch("copilot.core.mcp.simplified_dynamic_manager.logger") as mock_logger, patch(
            "builtins.print"
        ):
            manager.stop()

            # Should return early without logging
            mock_logger.info.assert_not_called()

    def test_stop_closed_loop(self):
        """Test stopping with a closed event loop."""
        manager = SimplifiedDynamicMCPManager()
        manager.is_running = True

        mock_loop = MagicMock()
        mock_loop.is_closed.return_value = True
        manager.loop = mock_loop

        with patch("copilot.core.mcp.simplified_dynamic_manager.logger") as mock_logger, patch(
            "builtins.print"
        ):
            manager.stop()

            # Should not call call_soon_threadsafe on closed loop
            mock_loop.call_soon_threadsafe.assert_not_called()
            mock_logger.info.assert_called_once()

    def test_stop_no_loop(self):
        """Test stopping when no loop exists."""
        manager = SimplifiedDynamicMCPManager()
        manager.is_running = True
        manager.loop = None

        with patch("copilot.core.mcp.simplified_dynamic_manager.logger") as mock_logger, patch(
            "builtins.print"
        ):
            manager.stop()

            # Should complete without errors
            mock_logger.info.assert_called_once()

    def test_run_server_in_thread_success(self):
        """Test successful server execution in thread."""
        manager = SimplifiedDynamicMCPManager()

        mock_server = AsyncMock()
        mock_loop = AsyncMock()

        with patch("asyncio.new_event_loop", return_value=mock_loop), patch("asyncio.set_event_loop"), patch(
            "copilot.core.mcp.simplified_dynamic_manager.get_simplified_dynamic_mcp_server",
            return_value=mock_server,
        ), patch("copilot.core.mcp.simplified_dynamic_manager.logger") as mock_logger, patch(
            "builtins.print"
        ):
            manager._run_server_in_thread("localhost", 8080)

            # Verify loop setup - check that run_until_complete was called with start_async
            mock_loop.run_until_complete.assert_called_once()
            # Check that start_async was called with correct arguments
            mock_server.start_async.assert_called_once_with("localhost", 8080)
            mock_loop.close.assert_called_once()
            # Check that logger.info was called (with any arguments)
            assert mock_logger.info.called
            assert manager.server == mock_server

    def test_run_server_in_thread_exception(self):
        """Test exception handling in server thread."""
        manager = SimplifiedDynamicMCPManager()

        mock_loop = MagicMock()
        test_error = Exception("Server error")

        with patch("asyncio.new_event_loop", return_value=mock_loop), patch("asyncio.set_event_loop"), patch(
            "copilot.core.mcp.simplified_dynamic_manager.get_simplified_dynamic_mcp_server",
            side_effect=test_error,
        ), patch("copilot.core.mcp.simplified_dynamic_manager.logger") as mock_logger, patch(
            "builtins.print"
        ):
            manager._run_server_in_thread("localhost", 8080)

            # Verify error handling
            mock_loop.close.assert_called_once()
            mock_logger.error.assert_called_once()
            assert manager.is_running is False


class TestModuleFunctions:
    """Test cases for module-level functions."""

    def test_get_simplified_dynamic_mcp_manager_singleton(self):
        """Test that get_simplified_dynamic_mcp_manager returns singleton."""
        # Clear any existing instance
        import copilot.core.mcp.simplified_dynamic_manager as manager_module

        manager_module._manager_instance = None

        manager1 = get_simplified_dynamic_mcp_manager()
        manager2 = get_simplified_dynamic_mcp_manager()

        assert manager1 is manager2
        assert isinstance(manager1, SimplifiedDynamicMCPManager)

    def test_start_simplified_dynamic_mcp_server_success(self):
        """Test successful start via module function."""
        mock_manager = MagicMock()
        mock_manager.start.return_value = True

        with patch(
            "copilot.core.mcp.simplified_dynamic_manager.get_simplified_dynamic_mcp_manager",
            return_value=mock_manager,
        ):
            result = start_simplified_dynamic_mcp_server()

            assert result is True
            mock_manager.start.assert_called_once()

    def test_start_simplified_dynamic_mcp_server_failure(self):
        """Test failed start via module function."""
        mock_manager = MagicMock()
        mock_manager.start.return_value = False

        with patch(
            "copilot.core.mcp.simplified_dynamic_manager.get_simplified_dynamic_mcp_manager",
            return_value=mock_manager,
        ):
            result = start_simplified_dynamic_mcp_server()

            assert result is False
            mock_manager.start.assert_called_once()

    def test_start_simplified_dynamic_mcp_server_with_params(self):
        """Test start with custom parameters via module function."""
        mock_manager = MagicMock()
        mock_manager.start.return_value = True

        with patch(
            "copilot.core.mcp.simplified_dynamic_manager.get_simplified_dynamic_mcp_manager",
            return_value=mock_manager,
        ):
            result = start_simplified_dynamic_mcp_server(host="192.168.1.1", port=9090)

            assert result is True
            mock_manager.start.assert_called_once_with(host="192.168.1.1", port=9090)

    def test_stop_simplified_dynamic_mcp_server(self):
        """Test stop via module function."""
        mock_manager = MagicMock()

        with patch(
            "copilot.core.mcp.simplified_dynamic_manager.get_simplified_dynamic_mcp_manager",
            return_value=mock_manager,
        ):
            stop_simplified_dynamic_mcp_server()

            mock_manager.stop.assert_called_once()

    def test_module_imports(self):
        """Test that all expected classes and functions can be imported."""
        from copilot.core.mcp.simplified_dynamic_manager import (
            SimplifiedDynamicMCPManager,
            get_simplified_dynamic_mcp_manager,
            start_simplified_dynamic_mcp_server,
            stop_simplified_dynamic_mcp_server,
        )

        # Verify classes and functions exist and are callable
        assert callable(SimplifiedDynamicMCPManager)
        assert callable(get_simplified_dynamic_mcp_manager)
        assert callable(start_simplified_dynamic_mcp_server)
        assert callable(stop_simplified_dynamic_mcp_server)


class TestEdgeCases:
    """Test edge cases and error conditions."""

    def test_manager_start_multiple_times(self):
        """Test calling start multiple times."""
        manager = SimplifiedDynamicMCPManager()

        with patch.object(threading, "Thread") as mock_thread_class, patch("builtins.print"):
            mock_thread = MagicMock()
            mock_thread_class.return_value = mock_thread

            # First start should succeed
            result1 = manager.start()
            assert result1 is True
            assert manager.is_running is True

            # Second start should fail
            with patch("copilot.core.mcp.simplified_dynamic_manager.logger") as mock_logger:
                result2 = manager.start()
                assert result2 is False
                mock_logger.warning.assert_called_once()

    def test_manager_stop_multiple_times(self):
        """Test calling stop multiple times."""
        manager = SimplifiedDynamicMCPManager()
        manager.is_running = True

        mock_loop = MagicMock()
        mock_loop.is_closed.return_value = False
        manager.loop = mock_loop

        with patch("copilot.core.mcp.simplified_dynamic_manager.logger") as mock_logger, patch(
            "builtins.print"
        ):
            # First stop
            manager.stop()
            mock_logger.info.assert_called_once()

            # Second stop (is_running would be False in real scenario)
            manager.is_running = False
            mock_logger.reset_mock()
            manager.stop()
            mock_logger.info.assert_not_called()

    def test_invalid_port_environment_variable(self):
        """Test handling of invalid port in environment variable."""
        manager = SimplifiedDynamicMCPManager()

        with patch.dict(os.environ, {"COPILOT_PORT_MCP": "invalid"}), patch("builtins.print"):
            with pytest.raises(ValueError):
                manager.start()

    def test_manager_thread_exception_handling(self):
        """Test that thread exceptions don't crash the manager."""
        manager = SimplifiedDynamicMCPManager()
        # Set is_running to True to simulate what happens in the start() method
        manager.is_running = True

        with patch("asyncio.new_event_loop", side_effect=Exception("Loop creation failed")), patch(
            "copilot.core.mcp.simplified_dynamic_manager.logger"
        ) as mock_logger, patch("builtins.print"):
            # The exception should be caught and handled gracefully
            manager._run_server_in_thread("localhost", 8080)

            # Should handle exception gracefully
            mock_logger.error.assert_called_once()
            assert manager.is_running is False
