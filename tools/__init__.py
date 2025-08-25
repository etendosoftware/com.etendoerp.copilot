"""This package stores the third party tools implementations."""

import importlib
import os
from typing import Final

from copilot.core.tool_wrapper import CopilotTool, ToolWrapper

INIT_FILE_NAME: Final[str] = "__init__.py"
PYTHON_EXTENSION: Final[str] = ".py"

package_dir = os.path.dirname(__file__)


# loop through all Python modules in the package directory (excluding __init__.py)
# and load the tool class name so it can be imported from core and set into the agent
for filename in os.listdir(package_dir):
    if filename.endswith(PYTHON_EXTENSION) and filename != INIT_FILE_NAME:
        module_name = f"{__name__}.{filename[:-len(PYTHON_EXTENSION)]}"
        module = importlib.import_module(module_name)

        # iterates through module attributes to find classes
        for name, obj in vars(module).items():
            if (
                isinstance(obj, type)
                and issubclass(obj, (ToolWrapper, CopilotTool))
                and name not in {"ToolWrapper", "CopilotTool"}
            ):
                globals()[name] = obj  # add the class to the package's namespace
