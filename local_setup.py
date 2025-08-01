# Local setup script for development environment
import os

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


# load the toml files in ../*Folder*/tools_deps.toml
for root, _dirs, files in os.walk(parent_dir):
    for file in files:
        if file.endswith("tools_deps.toml"):
            # read the file
            # read dependencies and install them
            with open(os.path.join(root, file)) as f:
                deps = toml.load(f)
                for tool, dep in deps.items():
                    # Example dep value = {'pycountry': '==22.3.5'}
                    # check if the value is a dictionary
                    print(f"Processing dependencies for tool: {tool}")
                    if isinstance(dep, dict):
                        # install the dependency
                        for k, v in dep.items():
                            if "|" in k:
                                k = k.split("|")[0]

                            if v == "*":
                                cmd = f"uv pip install {k}"
                            else:
                                cmd = f"uv pip install {k}{v}"
                            print(f"Installing {k} with command: {cmd}")
                            result = os.system(cmd)
                            if result != 0:
                                print(f"Warning: Failed to install {k}, trying with pip fallback...")
                                fallback_cmd = cmd.replace("uv pip", "pip")
                                os.system(fallback_cmd)

# log that script ended
print("local_setup.py ended")
