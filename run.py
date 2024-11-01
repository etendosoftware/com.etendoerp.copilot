"""This module is the entry point for the application.

This module is the entry point for the application. It creates the Flask app
"""
import os

import uvicorn
from copilot import app
from copilot.core.utils import is_docker

COPILOT_PORT = "COPILOT_PORT"

COPILOT_WAIT_FOR_DEBUGGER = "COPILOT_WAIT_FOR_DEBUGGER"

COPILOT_PORT_DEBUG = "COPILOT_PORT_DEBUG"

HOST = "0.0.0.0"

if __name__ == "__main__":
    # If the COPILOT_PORT_DEBUG environment variable is set, start the debugger
    # on the specified port. Print a message to indicate that the debugger is
    # listening on that port in color violet.
    port = int(os.getenv(COPILOT_PORT, "5005"))
    if os.getenv(COPILOT_PORT_DEBUG) and is_docker():
        import debugpy

        debug_port = int(os.getenv(COPILOT_PORT_DEBUG, "5100"))
        message = "Debugger enabled on port:"
        print("\033[95m {} {} \033[00m".format(message, str(debug_port)))
        debugpy.listen((HOST, debug_port))
        if os.getenv(COPILOT_WAIT_FOR_DEBUGGER, "false").lower() == "true":
            print("\033[95m Waiting for debugger to attach... \033[00m")
            debugpy.wait_for_client()
    uvicorn.run(app.app, host=HOST, port=port)
