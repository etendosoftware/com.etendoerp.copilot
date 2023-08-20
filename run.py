"""This module is the entry point for the application.

This module is the entry point for the application. It creates the Flask app
"""
import os

from copilot import app

if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0", port=os.getenv("COPILOT_PORT"))
