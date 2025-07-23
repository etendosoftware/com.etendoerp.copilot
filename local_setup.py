# Local setup script for development environment
import os
import sys

import toml

# log that script started
print("local_setup.py started - Dynamic tool loading mode")
print("Skipping tools_config.json generation - using dynamic discovery instead")

# The new ToolLoader will automatically discover all tools, so we don't need to
# maintain a tools_config.json file anymore. This script now only handles
# dependency installation from modules.# dependenci installation
# i have a set of tools_deps.toml files, one in actual directory, and one in ../*/ directory
#  and install them

# get the current directory
current_dir = os.getcwd()
# get the parent directory
parent_dir = os.path.dirname(current_dir)

# Revisar si la bandera --empty-tool-deps está presente
empty_tool_deps = "--empty-tool-deps" in sys.argv

# load the toml files in ../*Folder*/tools_deps.toml
for root, _dirs, files in os.walk(parent_dir):
    for file in files:
        if file.endswith("tools_deps.toml"):
            file_path = os.path.join(root, file)
            with open(file_path) as f:
                deps = toml.load(f)
                for tool, dep in deps.items():
                    # Print which tool's dependencies are being processed
                    print(f"Processing dependencies for tool: {tool}")
                    if isinstance(dep, dict):
                        for k, _v in dep.items():
                            # Remove any extra info after '|' in the dependency name
                            if "|" in k:
                                k = k.split("|")[0]
                            cmd = f"uv pip install {k}"
                            print(f"Installing {k} with command: {cmd}")
                            result = os.system(cmd)
                            if result != 0:
                                # Fallback to pip if uv fails
                                print(f"Warning: Failed to install {k}, trying with pip fallback...")
                                fallback_cmd = cmd.replace("uv pip", "pip")
                                os.system(fallback_cmd)
            # If the flag is set, empty the tools_deps.toml file after installation
            if empty_tool_deps:
                with open(file_path, "w") as f:
                    toml.dump({}, f)

# log that script ended
print("local_setup.py ended")
