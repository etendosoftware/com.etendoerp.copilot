"""
MCP Server startup and management module.

This module handles the startup, configuration, and lifecycle management
of the MCP server in a way that can run alongside the main FastAPI server.
"""

import asyncio
import logging
import os
import threading
from typing import Optional

from .server import MCPServer, MCPServerConfig

logger = logging.getLogger(__name__)


class MCPServerManager:
    """Manager for the MCP server lifecycle."""

    def __init__(self):
        self.server: Optional[MCPServer] = None
        self.thread: Optional[threading.Thread] = None
        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.is_running = False

    def _run_server_in_thread(self, config: MCPServerConfig):
        """Run the MCP server in a separate thread with its own event loop."""
        # Create a new event loop for this thread
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)

        try:
            logger.info(f"Starting MCP server on {config.host}:{config.port}")
            print(f"\033[94m Starting MCP server on {config.host}:{config.port} \033[00m")

            # Create the server instance
            self.server = MCPServer(config)

            # Start the server using asyncio
            self.loop.run_until_complete(self.server.start_async())

        except Exception as e:
            logger.error(f"MCP server error: {e}")
            print(f"\033[91m MCP server error: {e} \033[00m")
        finally:
            if self.loop:
                self.loop.close()
            self.is_running = False

    def start(self, config: Optional[MCPServerConfig] = None) -> bool:
        """Start the MCP server in a separate thread.

        Args:
            config: Server configuration. If None, creates default config.

        Returns:
            bool: True if server started successfully, False otherwise.
        """
        if self.is_running:
            logger.warning("MCP server is already running")
            return False

        # Create config from environment if not provided
        if config is None:
            config = self._create_config_from_env()

        # Config is always created now, never None
        try:
            # Start server in daemon thread
            self.thread = threading.Thread(
                target=self._run_server_in_thread, args=(config,), daemon=True, name="MCP-Server"
            )
            self.thread.start()
            self.is_running = True

            print("\033[94m MCP server thread started \033[00m")
            return True

        except Exception as e:
            logger.error(f"Failed to start MCP server thread: {e}")
            print(f"\033[91m Failed to start MCP server: {e} \033[00m")
            return False

    def stop(self):
        """Stop the MCP server."""
        if not self.is_running:
            return

        logger.info("Stopping MCP server")
        print("\033[94m Stopping MCP server \033[00m")

        if self.server:
            self.server.stop()

        if self.loop and not self.loop.is_closed():
            self.loop.call_soon_threadsafe(self.loop.stop)

        self.is_running = False

    def _create_config_from_env(self) -> MCPServerConfig:
        """Create MCP server config from environment variables.

        Returns:
            MCPServerConfig: Always returns a valid config. Uses port 5007 by default.
        """
        # Always create config, use default port 5007 if COPILOT_PORT_MCP is not set
        mcp_port = os.getenv("COPILOT_PORT_MCP", "5007")

        try:
            port = int(mcp_port)
            return MCPServerConfig(host="0.0.0.0", port=port, debug=bool(os.getenv("COPILOT_PORT_DEBUG")))
        except ValueError:
            logger.warning(f"Invalid COPILOT_PORT_MCP value: {mcp_port}, using default 5007")
            return MCPServerConfig(host="0.0.0.0", port=5007, debug=bool(os.getenv("COPILOT_PORT_DEBUG")))


# Global manager instance
_mcp_manager: Optional[MCPServerManager] = None


def get_mcp_manager() -> MCPServerManager:
    """Get the global MCP server manager instance."""
    global _mcp_manager
    if _mcp_manager is None:
        _mcp_manager = MCPServerManager()
    return _mcp_manager


def start_mcp_server_from_env() -> bool:
    """Start MCP server using environment configuration.

    Always starts the MCP server. Uses port 5007 by default if COPILOT_PORT_MCP is not set.

    Returns:
        bool: True if server started successfully, False otherwise.
    """
    manager = get_mcp_manager()
    return manager.start()


def stop_mcp_server():
    """Stop the MCP server."""
    manager = get_mcp_manager()
    manager.stop()
