"""Basic utility functions for MCP server."""

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


def get_etendo_token():
    """Get Etendo token from MCP request context using the generalized auth utility."""
    return extract_etendo_token_from_mcp_context()
