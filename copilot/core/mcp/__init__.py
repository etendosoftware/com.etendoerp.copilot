"""
Model Context Protocol (MCP) module for Etendo Copilot.

This module provides MCP server implementation with FastMCP, enabling
integration with Claude Desktop and other MCP clients. It includes:

- MCP Server implementation
- Dynamic MCP Server for on-demand instance creation
- Tool providers for Etendo operations
- Resource handlers for file system and database access
- Schema definitions for request/response validation

Requirements:
- Python 3.10+
- FastMCP 2.1.2+
"""

from .resources import BaseResource, ResourceContent
from .simplified_dynamic_manager import (
    SimplifiedDynamicMCPManager,
    get_simplified_dynamic_mcp_manager,
    start_simplified_dynamic_mcp_server,
    stop_simplified_dynamic_mcp_server,
)
from .simplified_dynamic_utils import start_simplified_dynamic_mcp_with_cleanup
from .tools import BaseTool, ToolResult
from .utils import is_mcp_enabled, start_mcp_with_cleanup

__all__ = [
    "BaseTool",
    "ToolResult",
    "BaseResource",
    "ResourceContent",
    "SimplifiedDynamicMCPManager",
    "start_simplified_dynamic_mcp_server",
    "stop_simplified_dynamic_mcp_server",
    "get_simplified_dynamic_mcp_manager",
    "start_simplified_dynamic_mcp_with_cleanup",
    "start_mcp_with_cleanup",
    "is_mcp_enabled",
]

__version__ = "0.1.0"
