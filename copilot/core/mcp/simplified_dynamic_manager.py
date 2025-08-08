"""
Simplified Dynamic MCP Server Manager for Etendo Copilot.

This module manages the lifecycle of the simplified dynamic MCP server.
"""

import asyncio
import logging
import os
import threading
from typing import Optional

from .simplified_dynamic_server import (
    SimplifiedDynamicMCPServer,
    get_simplified_dynamic_mcp_server,
)

logger = logging.getLogger(__name__)


class SimplifiedDynamicMCPManager:
    """Manager for the simplified dynamic MCP server lifecycle."""

    def __init__(self):
        self.server: Optional[SimplifiedDynamicMCPServer] = None
        self.thread: Optional[threading.Thread] = None
        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.is_running = False

    def _run_server_in_thread(self, host: str, port: int):
        """Run the simplified dynamic MCP server in a separate thread with its own event loop."""
        self.loop = None
        try:
            # Create a new event loop for this thread
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)

            logger.info(f"Starting Simplified Dynamic MCP server on {host}:{port}")
            print(f"\033[94m 🚀 Starting Simplified Dynamic MCP server on {host}:{port} \033[00m")

            # Get the server instance
            self.server = get_simplified_dynamic_mcp_server()

            # Start the server using asyncio
            self.loop.run_until_complete(self.server.start_async(host, port))

        except Exception as e:
            logger.error(f"Simplified Dynamic MCP server error: {e}")
            print(f"\033[91m ❌ Simplified Dynamic MCP server error: {e} \033[00m")
        finally:
            if self.loop:
                self.loop.close()
            self.is_running = False

    def start(self, host: str = "0.0.0.0", port: Optional[int] = None) -> bool:
        """Start the simplified dynamic MCP server in a separate thread.

        Args:
            host: Host to bind to
            port: Port to listen on. If None, uses environment variable or default.

        Returns:
            bool: True if server started successfully, False otherwise.
        """
        if self.is_running:
            logger.warning("Simplified Dynamic MCP server is already running")
            return False

        # Get port from environment if not provided
        if port is None:
            port = int(os.getenv("COPILOT_PORT_MCP", "5006"))

        try:
            # Start server in daemon thread
            self.thread = threading.Thread(
                target=self._run_server_in_thread,
                args=(host, port),
                daemon=True,
                name="Simplified-Dynamic-MCP-Server",
            )
            self.thread.start()
            self.is_running = True

            print("\033[94m 🧵 Simplified Dynamic MCP server thread started \033[00m")
            return True

        except Exception as e:
            logger.error(f"Failed to start simplified dynamic MCP server thread: {e}")
            print(f"\033[91m Failed to start simplified dynamic MCP server: {e} \033[00m")
            return False

    def stop(self):
        """Stop the simplified dynamic MCP server."""
        if not self.is_running:
            return

        logger.info("Stopping Simplified Dynamic MCP server")
        print("\033[94m 🛑 Stopping Simplified Dynamic MCP server \033[00m")

        if self.loop and not self.loop.is_closed():
            self.loop.call_soon_threadsafe(self.loop.stop)

        self.is_running = False


# Global manager instance
_simplified_dynamic_mcp_manager: Optional[SimplifiedDynamicMCPManager] = None


def get_simplified_dynamic_mcp_manager() -> SimplifiedDynamicMCPManager:
    """Get the global simplified dynamic MCP manager instance."""
    global _simplified_dynamic_mcp_manager
    if _simplified_dynamic_mcp_manager is None:
        _simplified_dynamic_mcp_manager = SimplifiedDynamicMCPManager()
    return _simplified_dynamic_mcp_manager


def start_simplified_dynamic_mcp_server(host: str = "0.0.0.0", port: Optional[int] = None) -> bool:
    """Start simplified dynamic MCP server using environment configuration.

    Args:
        host: Host to bind the server to (default: "0.0.0.0")
        port: Port to bind the server to (default: None, uses environment config)

    Returns:
        bool: True if server started successfully, False otherwise.
    """
    manager = get_simplified_dynamic_mcp_manager()
    return manager.start(host=host, port=port)


def stop_simplified_dynamic_mcp_server():
    """Stop the simplified dynamic MCP server."""
    manager = get_simplified_dynamic_mcp_manager()
    manager.stop()
