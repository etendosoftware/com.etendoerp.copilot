"""
MCP request and response schemas.

This module contains Pydantic models for MCP protocol messages
and data validation.
"""

from typing import Any, Dict, Optional, Union

from pydantic import BaseModel, Field


class MCPRequest(BaseModel):
    """Base MCP request model."""

    jsonrpc: str = "2.0"
    id: Union[str, int]
    method: str
    params: Optional[Dict[str, Any]] = None


class MCPResponse(BaseModel):
    """Base MCP response model."""

    jsonrpc: str = "2.0"
    id: Union[str, int]
    result: Optional[Any] = None
    error: Optional[Dict[str, Any]] = None


class MCPError(BaseModel):
    """MCP error model."""

    code: int
    message: str
    data: Optional[Any] = None


class ToolCallRequest(MCPRequest):
    """Tool call request model."""

    method: str = "tools/call"
    params: Dict[str, Any] = Field(description="Tool call parameters including name and arguments")


class ResourceReadRequest(MCPRequest):
    """Resource read request model."""

    method: str = "resources/read"
    params: Dict[str, str] = Field(description="Resource read parameters including URI")


class ListToolsRequest(MCPRequest):
    """List tools request model."""

    method: str = "tools/list"


class ListResourcesRequest(MCPRequest):
    """List resources request model."""

    method: str = "resources/list"
