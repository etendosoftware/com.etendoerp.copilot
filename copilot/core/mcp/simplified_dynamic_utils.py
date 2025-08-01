"""
Simplified Dynamic MCP utility functions for Etendo Copilot.

This module provides utility functions for integrating the simplified dynamic MCP server
with the main application lifecycle.
"""

import atexit
import logging
import signal
import sys

from .simplified_dynamic_manager import (
    get_simplified_dynamic_mcp_manager,
    start_simplified_dynamic_mcp_server,
)

logger = logging.getLogger(__name__)
_shutdown_registered = False


def setup_simplified_dynamic_mcp_shutdown_handlers():
    """Set up shutdown handlers for graceful simplified dynamic MCP server shutdown."""
    global _shutdown_registered

    if _shutdown_registered:
        return

    def shutdown_simplified_dynamic_mcp():
        """Shutdown the simplified dynamic MCP server gracefully."""
        manager = get_simplified_dynamic_mcp_manager()
        if manager.is_running:
            print("\033[94m 🧹 Shutting down Simplified Dynamic MCP server... \033[00m")
            manager.stop()

    # Register shutdown on normal exit
    atexit.register(shutdown_simplified_dynamic_mcp)

    # Handle SIGTERM and SIGINT
    def signal_handler(signum, frame):
        shutdown_simplified_dynamic_mcp()
        sys.exit(0)

    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)

    _shutdown_registered = True


def start_simplified_dynamic_mcp_with_cleanup() -> bool:
    """Start simplified dynamic MCP server and set up cleanup handlers.

    Always starts the simplified dynamic MCP server with cleanup handlers.

    Returns:
        bool: True if server started successfully.
    """
    try:
        # Set up cleanup handlers first (continue even if this fails)
        try:
            setup_simplified_dynamic_mcp_shutdown_handlers()
        except Exception as e:
            logger.warning(f"Failed to setup shutdown handlers: {e}")

        logger.info("Simplified Dynamic MCP server setup with cleanup handlers")

        # Always start the server
        return start_simplified_dynamic_mcp_server()

    except Exception as e:
        logger.error(f"Failed to start simplified dynamic MCP server with cleanup: {e}")
        return False
