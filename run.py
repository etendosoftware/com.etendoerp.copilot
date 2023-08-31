"""This module is the entry point for the application.

This module is the entry point for the application. It creates the Flask app
"""
import os

import uvicorn
from copilot import app

if __name__ == "__main__":
    uvicorn.run(app.app, host="localhost", port=int(os.getenv("COPILOT_PORT")))
