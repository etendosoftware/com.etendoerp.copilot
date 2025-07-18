"""
Dynamic MCP Server Manager for Etendo Copilot.

This module manages the lifecycle of the dynamic MCP server that creates
instances on-demand based on URL path identifiers.
"""

import asyncio
import logging
import os
import threading
from typing import Optional

from .dynamic_server import DynamicMCPServerManager, get_dynamic_mcp_manager

logger = logging.getLogger(__name__)


class DynamicMCPManager:
    """Manager for the dynamic MCP server lifecycle."""

    def __init__(self):
        self.server_manager: Optional[DynamicMCPServerManager] = None
        self.thread: Optional[threading.Thread] = None
        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.is_running = False

    def _run_server_in_thread(self, host: str, port: int):
        """Run the dynamic MCP server in a separate thread with its own event loop."""
        # Create a new event loop for this thread
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)

        try:
            logger.info(f"Starting Dynamic MCP server on {host}:{port}")
            print(f"\033[94m Starting Dynamic MCP server on {host}:{port} \033[00m")

            # Get the server manager instance
            self.server_manager = get_dynamic_mcp_manager()

            # Start the server using asyncio
            self.loop.run_until_complete(self.server_manager.start_async(host, port))

        except Exception as e:
            logger.error(f"Dynamic MCP server error: {e}")
            print(f"\033[91m Dynamic MCP server error: {e} \033[00m")
        finally:
            if self.loop:
                self.loop.close()
            self.is_running = False

    def start(self, host: str = "0.0.0.0", port: Optional[int] = None) -> bool:
        """Start the dynamic MCP server in a separate thread.

        Args:
            host: Host to bind to
            port: Port to listen on. If None, uses environment variable or default.

        Returns:
            bool: True if server started successfully, False otherwise.
        """
        if self.is_running:
            logger.warning("Dynamic MCP server is already running")
            return False

        # Get port from environment if not provided
        if port is None:
            port = int(os.getenv("COPILOT_PORT_MCP", "5007"))

        try:
            # Start server in daemon thread
            self.thread = threading.Thread(
                target=self._run_server_in_thread, args=(host, port), daemon=True, name="Dynamic-MCP-Server"
            )
            self.thread.start()
            self.is_running = True

            print("\033[94m Dynamic MCP server thread started \033[00m")
            return True

        except Exception as e:
            logger.error(f"Failed to start dynamic MCP server thread: {e}")
            print(f"\033[91m Failed to start dynamic MCP server: {e} \033[00m")
            return False

    def stop(self):
        """Stop the dynamic MCP server."""
        if not self.is_running:
            return

        logger.info("Stopping Dynamic MCP server")
        print("\033[94m Stopping Dynamic MCP server \033[00m")

        if self.loop and not self.loop.is_closed():
            self.loop.call_soon_threadsafe(self.loop.stop)

        self.is_running = False


# Global manager instance
_dynamic_mcp_manager: Optional[DynamicMCPManager] = None


def get_dynamic_mcp_manager() -> DynamicMCPManager:
    """Get the global dynamic MCP manager instance."""
    global _dynamic_mcp_manager
    if _dynamic_mcp_manager is None:
        _dynamic_mcp_manager = DynamicMCPManager()
    return _dynamic_mcp_manager


def start_dynamic_mcp_server() -> bool:
    """Start dynamic MCP server using environment configuration.

    Returns:
        bool: True if server started successfully, False otherwise.
    """
    manager = get_dynamic_mcp_manager()
    return manager.start()


def stop_dynamic_mcp_server():
    """Stop the dynamic MCP server."""
    manager = get_dynamic_mcp_manager()
    manager.stop()
