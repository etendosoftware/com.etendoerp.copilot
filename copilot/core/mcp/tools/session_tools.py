"""
Session management tools for MCP server.

This module contains tools for managing MCP sessions including
agent initialization and session information.
"""

import logging
from contextvars import ContextVar

logger = logging.getLogger(__name__)

# Context variable to store the current session's agent_id
current_agent_id: ContextVar[str] = ContextVar("current_agent_id", default="default-agent")


def register_session_tools(app):
    """Register session management tools with the MCP app.

    Args:
        app: FastMCP application instance
    """

    @app.tool
    def init_session(agent_id: str) -> str:
        """Initialize a new session with the specified agent_id.

        Args:
            agent_id: The identifier for the agent in this session

        Returns:
            Confirmation message with the initialized agent_id
        """
        # Set the agent_id in the context for this session
        current_agent_id.set(agent_id)
        logger.info(f"Session initialized with agent_id: {agent_id}")
        return f"Sesión inicializada para el agente: {agent_id}"

    @app.tool
    def agent_greeting() -> str:
        """Get a personalized greeting from the current session's agent."""
        agent_id = current_agent_id.get()
        return f"¡El agente {agent_id} te envía saludos!"

    @app.tool
    def get_agent_info() -> str:
        """Get information about the current session's agent."""
        agent_id = current_agent_id.get()
        return f"Agente actual: {agent_id}"


def get_current_agent_id() -> str:
    """Get the current session's agent_id.

    Returns:
        The agent_id for the current session
    """
    return current_agent_id.get()


def set_current_agent_id(agent_id: str) -> None:
    """Set the current session's agent_id.

    Args:
        agent_id: The agent identifier to set for this session
    """
    current_agent_id.set(agent_id)
