"""This module is the entry point for the application.

This module is the entry point for the application. It creates the Flask app
"""

import logging

from copilot import create_app

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    app = create_app()
    app.run(debug=True, host="0.0.0.0", port=5000)
