"""This module is the entry point for the application.

This module is the entry point for the application. It creates the Flask app
and runs both the main FastAPI server and the dynamic MCP server in parallel.
"""

import os

import uvicorn
from copilot import app
from copilot.baseutils.logging_envvar import (
    is_docker,
    read_optional_env_var,
    read_optional_env_var_bool,
    read_optional_env_var_int,
)
from copilot.core.mcp.simplified_dynamic_utils import (
    start_simplified_dynamic_mcp_with_cleanup,
)

COPILOT_PORT = "copilot.port"

COPILOT_WAIT_FOR_DEBUGGER = "copilot.wait.for.debugger"

COPILOT_PORT_DEBUG = "copilot.port.debug"

COPILOT_PORT_MCP = "copilot.port.mcp"

HOST = "0.0.0.0"

if __name__ == "__main__":
    # If the COPILOT_PORT_DEBUG environment variable is set, start the debugger
    # on the specified port. Print a message to indicate that the debugger is
    # listening on that port in color violet.
    # Establecer variables de entorno antes de crear el agente
    os.environ["LANGSMITH_TRACING"] = str(read_optional_env_var_bool("langsmith.tracing", False)).lower()
    os.environ["LANGCHAIN_API_KEY"] = str(read_optional_env_var("langchain.api.key", "None"))
    port = read_optional_env_var_int(COPILOT_PORT, 5005)
    debug_port_set = read_optional_env_var(COPILOT_PORT_DEBUG, None) is not None
    debug_port = read_optional_env_var_int(COPILOT_PORT_DEBUG, 5100)
    wait_for_debugger = read_optional_env_var_bool(COPILOT_WAIT_FOR_DEBUGGER, False)
    if debug_port_set and is_docker():
        import debugpy

        message = "Debugger enabled on port:"
        print("\033[95m {} {} \033[00m".format(message, str(debug_port)))
        debugpy.listen((HOST, debug_port))
        if wait_for_debugger:
            print("\033[95m Waiting for debugger to attach... \033[00m")
            debugpy.wait_for_client()

    # Start Simplified Dynamic MCP server (creates instances on-demand)
    start_simplified_dynamic_mcp_with_cleanup()

    # Start the main FastAPI server
    print(f"\033[92m Starting main server on {HOST}:{port} \033[00m")
    uvicorn.run(app.app, host=HOST, port=port)
