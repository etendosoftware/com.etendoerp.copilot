"""
MCP Resources module.

This module contains resource providers for the MCP server including
file system resources, database resources, and external API resources.
"""

from .base import BaseResource, ResourceContent

__all__ = [
    "BaseResource",
    "ResourceContent",
]
