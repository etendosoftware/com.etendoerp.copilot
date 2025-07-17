"""
Basic utility tools for MCP server.

This module contains basic utility tools for MCP server functionality
including connectivity testing and server information.
"""

import logging

logger = logging.getLogger(__name__)


def register_basic_tools(app):
    """Register basic utility tools with the MCP app.

    Args:
        app: FastMCP application instance
    """

    @app.tool()
    def ping() -> str:
        """A simple ping tool to test MCP connectivity."""
        return "pong"

    @app.tool()
    def server_info() -> dict:
        """Get basic server information."""
        return {
            "name": "etendo-copilot-mcp",
            "version": "0.1.0",
            "description": "Etendo Copilot MCP Server with HTTP streaming",
            "transport": "http-streaming",
            "status": "running",
        }
