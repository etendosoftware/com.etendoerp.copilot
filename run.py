"""This module is the entry point for the application.

This module is the entry point for the application. It creates the Flask app
"""
import os

import uvicorn

from copilot import app

if __name__ == "__main__":
    # If the COPILOT_PORT_DEBUG environment variable is set, start the debugger
    # on the specified port. Print a message to indicate that the debugger is
    # listening on that port in color violet.
    if os.getenv("COPILOT_PORT_DEBUG"):
        import debugpy
        debug_port = int(os.getenv("COPILOT_PORT_DEBUG"))
        message = "Debugger enabled on port:"
        print("\033[95m {} {} \033[00m".format(message, str(debug_port)))
        debugpy.listen(('0.0.0.0', debug_port))
        if os.getenv("COPILOT_WAIT_FOR_DEBUGGER", "false").lower() == "true":
            print ("\033[95m Waiting for debugger to attach... \033[00m")
            debugpy.wait_for_client()
    uvicorn.run(app.app, host="0.0.0.0", port=int(os.getenv("COPILOT_PORT")))
