"""
Utility functions for MCP server integration.

This module provides utility functions for integrating the MCP server
with the main application lifecycle.
"""

import atexit
import signal
import sys

from .simplified_dynamic_manager import get_simplified_dynamic_mcp_manager

_shutdown_registered = False


def setup_mcp_shutdown_handlers():
    """Set up shutdown handlers for graceful MCP server shutdown."""
    global _shutdown_registered

    if _shutdown_registered:
        return

    def shutdown_mcp():
        """Shutdown the MCP server gracefully."""
        manager = get_simplified_dynamic_mcp_manager()
        if manager.is_running:
            print("\033[94m Shutting down MCP server... \033[00m")
            manager.stop()

    # Register shutdown on normal exit
    atexit.register(shutdown_mcp)

    # Handle SIGTERM and SIGINT
    def signal_handler(signum, frame):
        shutdown_mcp()
        sys.exit(0)

    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)

    _shutdown_registered = True


def is_mcp_enabled() -> bool:
    """Check if MCP server is enabled.

    Returns:
        bool: Always True - MCP server is always enabled.
    """
    return True


def start_mcp_with_cleanup() -> bool:
    """Start MCP server and set up cleanup handlers.

    Always starts the MCP server with cleanup handlers.

    Returns:
        bool: True if server started successfully.
    """
    from . import start_simplified_dynamic_mcp_server

    # Set up cleanup handlers
    setup_mcp_shutdown_handlers()

    # Always start the server
    return start_simplified_dynamic_mcp_server()
