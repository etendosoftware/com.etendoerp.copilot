"""This module is the entry point for the application.

This module is the entry point for the application. It creates the Flask app
"""

import multiprocessing
from dotenv import dotenv_values
from copilot.app import Copilot


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
    config = dotenv_values(".env")

    run_mode = config.get("RUN_MODE")
    if run_mode == "MODULAR":
        run_modular()
    else:
        print(
            "Variable de entorno RUN_MODE no configurada correctamente."
            "Debe ser 'MONOLITH' o 'MODULAR'."
        )
