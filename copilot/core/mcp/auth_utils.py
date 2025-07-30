"""
Authentication utilities for MCP server.

This module provides utilities for extracting and handling authentication tokens
from various request sources including FastAPI requests and MCP requests.
"""

import logging
from typing import Optional

from core.utils.etendo_utils import BEARER_PREFIX, normalize_etendo_token
from fastapi import Request

logger = logging.getLogger(__name__)


def extract_etendo_token_from_request(request: Request) -> Optional[str]:
    """
    Extract Etendo token from any HTTP request (FastAPI, MCP, etc.).

    This function looks for the token in various headers:
    - etendo-token
    - Authorization (Bearer format)
    - X-Etendo-Token

    Args:
        request: HTTP request object

    Returns:
        str: Bearer token if found, None otherwise
    """
    try:
        # Try etendo-token header first
        token = request.headers.get("etendo-token")
        if token and token.strip():
            return normalize_etendo_token(token)

        # Try Authorization header
        auth_header = request.headers.get("authorization") or request.headers.get("Authorization")
        if auth_header and auth_header.startswith(BEARER_PREFIX):
            return auth_header

        # Try X-Etendo-Token header
        x_token = request.headers.get("x-etendo-token") or request.headers.get("X-Etendo-Token")
        if x_token and x_token.strip():
            return normalize_etendo_token(x_token)

        return None

    except Exception as e:
        logger.error(f"Error extracting Etendo token from request: {e}")
        return None


def extract_etendo_token_from_mcp_context() -> Optional[str]:
    """
    Extract Etendo token from MCP context (FastMCP dependencies).

    Returns:
        str: Bearer token if found, None otherwise
    """
    try:
        # Try to import fastmcp dependencies - this might fail in test environments
        from fastmcp.server.dependencies import get_context

        context = get_context()
        req = context.get_http_request()
        return extract_etendo_token_from_request(req)

    except ImportError as e:
        logger.debug(f"FastMCP dependencies not available: {e}")
        return None
    except Exception as e:
        logger.error(f"Error extracting Etendo token from MCP context: {e}")
        return None
