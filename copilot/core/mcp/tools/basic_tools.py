"""
Basic utility tools for MCP server.

This module contains basic utility tools for MCP server functionality
including connectivity testing and server information.
"""

import logging
from typing import Optional

import httpx
from copilot.core.utils import read_optional_env_var

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

    @app.tool
    def hello_world() -> str:
        """Say hello with instance information."""
        identifier = get_identifier()
        if identifier:
            return f"Hello! You are connected to Etendo Copilot MCP Server (Instance: {identifier})!"
        return "Hello! You are connected to Etendo Copilot MCP Server!"

    def get_etendo_token():
        from fastmcp.server.dependencies import get_context

        context = get_context()
        req = context.get_http_request()
        token = req.headers.get("etendo-token", None)
        if not token or token == "":
            return None
        if not token.startswith("Bearer "):
            token = f"Bearer {token}"
        return token

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

    @app.tool
    async def ask_agent(question: str, conversation_id: Optional[str] = None) -> dict:
        """Ask a question to the Etendo Copilot agent.

        Args:
            question: The question to ask the agent
            conversation_id: Optional conversation ID to maintain context

        Returns:
            dict: Response from the Etendo agent
        """
        try:
            etendo_token = get_etendo_token()
            if not etendo_token or not etendo_token.startswith("Bearer "):
                return {
                    "success": False,
                    "error": "No valid Bearer token found in request headers. Authentication required.",
                    "status_code": 401,
                }

            # Prepare the request payload
            payload = {"question": question, "app_id": get_identifier()}

            if conversation_id:
                payload["conversation_id"] = conversation_id

            # Prepare headers with Bearer token authentication from MCP request
            headers = {
                "Content-Type": "application/json",
                "Accept": "application/json",
                "Authorization": etendo_token,  # Use the token from MCP connection
            }

            # Get Etendo host from environment variable
            etendo_host = read_optional_env_var("ETENDO_HOST_DOCKER", "http://localhost:8080/etendo")

            # Make the HTTP request to Etendo
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(
                    f"{etendo_host}/sws/copilot/question", json=payload, headers=headers
                )

                # Check if the response is successful
                return get_response(response)

        except Exception as e:
            logger.error(f"Error in ask_agent tool: {e}")
            return {"success": False, "error": f"Unexpected error: {str(e)}", "status_code": None}
