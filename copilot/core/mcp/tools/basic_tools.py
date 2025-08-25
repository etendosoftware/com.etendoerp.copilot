"""
Basic utility tools for MCP server.

This module contains basic utility tools for MCP server functionality
including connectivity testing and server information.
"""

import logging

from copilot.core.mcp.auth_utils import extract_etendo_token_from_mcp_context

logger = logging.getLogger(__name__)


def get_identifier():
    """Get the identifier for the MCP server from the request path."""
    try:
        from fastmcp.server.dependencies import get_context

        context = get_context()
        instance_id = context.fastmcp._mcp_server.version
        return instance_id

    except Exception as e:
        logger.error(f"Error getting identifier: {e}")
        return None


def get_response(response):
    """Process the HTTP response from the Etendo agent."""
    if response.status_code == 200:
        return {"success": True, "answer": response.json(), "status_code": response.status_code}
    else:
        return {
            "success": False,
            "error": f"HTTP {response.status_code}: {response.text}",
            "status_code": response.status_code,
        }


def register_basic_tools(app):
    """Register basic utility tools with the MCP app.

    Args:
        app: FastMCP application instance
        identifier: MCP instance identifier (not used, kept for compatibility)
    """

    @app.tool
    def ping() -> str:
        """A simple ping tool to test MCP connectivity."""
        return "pong"

    def get_etendo_token():
        """Get Etendo token from MCP request context using the generalized auth utility."""
        return extract_etendo_token_from_mcp_context()

    @app.tool
    def server_info() -> dict:
        """Get basic server information."""
        return {
            "name": "etendo-copilot-mcp",
            "version": "0.1.0",
            "description": "Etendo Copilot MCP Server with HTTP streaming",
            "transport": "http-streaming",
            "status": "running",
        }


def register_basic_tools_direct(app):
    """Register basic utility tools with the MCP app (direct mode without ask_agent).

    Args:
        app: FastMCP application instance
    """

    @app.tool
    def ping() -> str:
        """A simple ping tool to test MCP connectivity."""
        return "pong"

    @app.tool
    def server_info() -> dict:
        """Get basic server information."""
        return {
            "name": "etendo-copilot-mcp",
            "version": "0.1.0",
            "description": "Etendo Copilot MCP Server with HTTP streaming - Direct Mode",
            "transport": "http-streaming",
            "status": "running",
            "mode": "direct",
        }
