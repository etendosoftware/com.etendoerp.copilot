"""
Base resource handler for MCP resources.

This module provides the base class and utilities for implementing
MCP resources in the Etendo Copilot system.
"""

from abc import ABC, abstractmethod
from typing import Any, Dict, Optional

from pydantic import BaseModel


class ResourceContent(BaseModel):
    """Content of a resource."""

    uri: str
    mimeType: Optional[str] = None
    text: Optional[str] = None
    blob: Optional[bytes] = None


class BaseResource(ABC):
    """Base class for MCP resources."""

    def __init__(self, uri: str, name: str, description: str):
        """Initialize the resource.

        Args:
            uri: Resource URI
            name: Resource name
            description: Resource description
        """
        self.uri = uri
        self.name = name
        self.description = description

    @abstractmethod
    async def read(self) -> ResourceContent:
        """Read the resource content.

        Returns:
            ResourceContent: The resource content
        """
        pass

    def to_mcp_resource(self) -> Dict[str, Any]:
        """Convert resource to MCP resource format.

        Returns:
            Dict: MCP resource definition
        """
        return {"uri": self.uri, "name": self.name, "description": self.description}
