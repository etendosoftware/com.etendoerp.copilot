"""
Session management tools for MCP server.

This module contains utilities for managing MCP sessions.
Note: Session tools have been removed as they are no longer needed.
"""

import logging

logger = logging.getLogger(__name__)


def register_session_tools(app):
    """Register session management tools with the MCP app.

    Note: This function is kept for compatibility but no longer registers any tools.
    Session management has been simplified and removed.

    Args:
        app: FastMCP application instance
    """
    # No session tools are registered anymore
    pass
