"""
Model Context Protocol (MCP) module for Etendo Copilot.

This module provides MCP server implementation with FastMCP, enabling
integration with Claude Desktop and other MCP clients. It includes:

- MCP Server implementation
- Tool providers for Etendo operations
- Resource handlers for file system and database access
- Schema definitions for request/response validation

Requirements:
- Python 3.10+
- FastMCP 2.1.2+
"""

from .manager import MCPServerManager, start_mcp_server_from_env, stop_mcp_server
from .resources import BaseResource, ResourceContent
from .server import MCPServer, MCPServerConfig
from .tools import BaseTool, ToolResult
from .utils import is_mcp_enabled, start_mcp_with_cleanup

__all__ = [
    "MCPServer",
    "MCPServerConfig",
    "BaseTool",
    "ToolResult",
    "BaseResource",
    "ResourceContent",
    "MCPServerManager",
    "start_mcp_server_from_env",
    "stop_mcp_server",
    "start_mcp_with_cleanup",
    "is_mcp_enabled",
]

__version__ = "0.1.0"
