"""This module is the entry point for the application.

This module is the entry point for the application. It creates the Flask app
"""

import multiprocessing
import os

from copilot.app import Copilot
from dotenv import load_dotenv


def run_modular():
    """Run the app in modular mode.

    This function runs the app in modular mode. This means that the app will
    be run using the Copilot class.

    Args:
        None

    Returns:
        None
    """
    app = Copilot()
    processes = [
        multiprocessing.Process(target=app.run),
    ]
    # Start all processes
    for process in processes:
        process.start()

    # Wait for all processes to finish
    for process in processes:
        process.join()


if __name__ == "__main__":
    load_dotenv(".env")
    run_mode = os.getenv("RUN_MODE")

    if run_mode == "MODULAR":
        run_modular()
    else:
        print("Environment variable RUN_MODE is required. Please set your .env file")
