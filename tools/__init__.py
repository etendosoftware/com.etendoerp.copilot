"""This package stores the third party tools implementations."""

#from .hello_world import HelloWorldTool

import os
import importlib

from copilot.core.tool_wrapper import ToolWrapper

package_dir = os.path.dirname(__file__)

# loop through all Python modules in the package directory (excluding __init__.py)
# and load the tool class name so it can be imported from core and set into the agent
for filename in os.listdir(package_dir):
    if filename.endswith(".py") and filename != "__init__.py":
        module_name = f"{__name__}.{filename[:-3]}"  # Remove '.py' extension
        module = importlib.import_module(module_name)

        # iterates through module attributes to find classes
        for name, obj in vars(module).items():
            if isinstance(obj, type) and name != ToolWrapper.__name__:
                globals()[name] = obj  # add the class to the package's namespace
