"""
Base tool handler for MCP tools.

This module provides the base class and utilities for implementing
MCP tools in the Etendo Copilot system.

Example usage with FastMCP:
    @app.tool()  # Note: parentheses required
    def my_tool(param: str) -> str:
        '''Tool description'''
        return f"Result: {param}"
"""

from abc import ABC, abstractmethod
from typing import Any, Dict, Optional

from pydantic import BaseModel


class ToolResult(BaseModel):
    """Result of a tool execution."""

    success: bool
    data: Optional[Any] = None
    error: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


class BaseTool(ABC):
    """Base class for MCP tools."""

    def __init__(self, name: str, description: str):
        """Initialize the tool.

        Args:
            name: Tool name
            description: Tool description
        """
        self.name = name
        self.description = description

    @abstractmethod
    async def execute(self, **kwargs) -> ToolResult:
        """Execute the tool with given parameters.

        Args:
            **kwargs: Tool parameters

        Returns:
            ToolResult: Execution result
        """
        pass

    @abstractmethod
    def get_schema(self) -> Dict[str, Any]:
        """Get the tool's parameter schema.

        Returns:
            Dict: JSON schema for tool parameters
        """
        pass

    def to_mcp_tool(self) -> Dict[str, Any]:
        """Convert tool to MCP tool format.

        Returns:
            Dict: MCP tool definition
        """
        return {"name": self.name, "description": self.description, "inputSchema": self.get_schema()}
