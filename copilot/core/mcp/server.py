"""
MCP Server implementation for Etendo Copilot.

This module provides the main MCP server class using FastMCP library
for integration with Claude Desktop and other MCP clients.
"""

import asyncio
import logging
from typing import Dict, Optional

from fastmcp import FastMCP
from pydantic import BaseModel

from .tools import register_basic_tools, register_session_tools

logger = logging.getLogger(__name__)


class MCPServerConfig(BaseModel):
    """Configuration for the MCP Server with HTTP streaming transport."""

    name: str = "etendo-copilot-mcp"
    version: str = "0.1.0"
    description: str = "Etendo Copilot MCP Server with HTTP streaming"
    host: str = "localhost"
    port: int = 5007  # Default MCP port, can be overridden with COPILOT_PORT_MCP
    debug: bool = False
    transport: str = "http"  # Use HTTP streaming transport
    # HTTP streaming specific settings
    cors_enabled: bool = True  # Enable CORS for web client access
    max_connections: int = 100  # Maximum concurrent HTTP connections


class MCPServer:
    """
    MCP Server implementation for Etendo Copilot.

    This server provides tools and resources for interacting with Etendo
    through the Model Context Protocol using modern HTTP streaming
    transport.

    Transport: HTTP Streaming (modern standard)
    - Native HTTP streaming protocol
    - Bi-directional communication over HTTP
    - Modern replacement for SSE and WebSockets
    - Direct HTTP streamable transport
    """

    def __init__(self, config: Optional[MCPServerConfig] = None):
        """Initialize the MCP server.

        Args:
            config: Server configuration. If None, uses default config.
        """
        self.config = config or MCPServerConfig()
        self.app = FastMCP(self.config.name)
        # Store active sessions and their agent_ids
        self.active_sessions: Dict[str, str] = {}
        self._setup_server()

    def _setup_server(self):
        """Set up the MCP server with tools and resources."""
        logger.info(f"Setting up MCP server: {self.config.name}")

        # Register all available tools
        logger.info("Registering MCP tools...")

        # Register basic tools (ping, server_info, etc.)
        register_basic_tools(self.app)

        # Register session management tools (init_session, agent_greeting, etc.)
        register_session_tools(self.app)

        logger.info("MCP server initialized with all tools registered")

    def start(self):
        """Start the MCP server (synchronous version) with HTTP streaming transport."""
        logger.info(f"Starting MCP server with HTTP streaming on {self.config.host}:{self.config.port}")
        try:
            # Use FastMCP's native HTTP streaming transport
            self.app.run(host=self.config.host, port=self.config.port)
        except Exception as e:
            logger.error(f"Failed to start MCP server: {e}")
            raise

    async def start_async(self):
        """Start the MCP server (asynchronous version) with HTTP streaming transport."""
        logger.info(f"Starting MCP server with HTTP streaming on {self.config.host}:{self.config.port}")
        try:
            # Use FastMCP's native HTTP streaming transport (async)
            await self.app.run_async(
                host=self.config.host, port=self.config.port, transport="streamable-http"
            )

        except Exception as e:
            logger.error(f"Failed to start MCP server: {e}")
            raise

    def stop(self):
        """Stop the MCP server."""
        logger.info("Stopping MCP server")
        # FastMCP handles cleanup automatically

    def get_app(self) -> FastMCP:
        """Get the FastMCP application instance."""
        return self.app

    def get_http_app(self):
        """Get the HTTP streaming application."""
        return self.app  # FastMCP app itself is the HTTP streaming app


# Convenience function for creating and running the server
async def run_mcp_server(config: Optional[MCPServerConfig] = None):
    """Run the MCP server with HTTP streaming transport.

    Args:
        config: Server configuration. If None, uses default config.
    """
    server = MCPServer(config)
    await server.start_async()  # Use async method for HTTP streaming


if __name__ == "__main__":
    # Example usage
    asyncio.run(run_mcp_server())
