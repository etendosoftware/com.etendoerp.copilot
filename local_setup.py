# log that script started
print("local_setup.py started")
# load file tools_config.json
import json

with open('tools_config.json') as f:
    tools_config = json.load(f)
    tpt = tools_config['third_party_tools']
    # list files in tools directory
    import os

    tools = os.listdir('tools')
    #filter only .py files that not start with __
    tools = [tool for tool in tools if tool.endswith('.py') and not tool.startswith('__')]

    # remove extension from files, and add a list of filenames without extension to tools_config
    for i in range(len(tools)):
        tools[i] = tools[i].replace('.py', '')
    # check if the tool is a third party tool, if not add it to the tools_config
    for tool in tools:
        if tool not in tpt:
            tpt[tool] = True
    # update tools_config
    tools_config['third_party_tools'] = tpt
    # save tools_config, overwriting the original file
    with open('tools_config.json', 'w') as f:
        ## save the json indented
        json.dump(tools_config, f, indent=4)

# dependenci installation
# i have a set of tools_deps.toml files, one in actual directory, and one in ../*/ directory
#  and install them
import toml
import os

# get the current directory
current_dir = os.getcwd()
# get the parent directory
parent_dir = os.path.dirname(current_dir)



# load the toml files in ../*Folder*/tools_deps.toml
for root, dirs, files in os.walk(parent_dir):
    for file in files:
        if file.endswith('tools_deps.toml'):
            # read the file
            # read dependencies and install them
            with open(os.path.join(root, file)) as f:
                deps = toml.load(f)
                for tool, dep in deps.items():
                    # Example dep value = {'pycountry': '==22.3.5'}
                    # check if the value is a dictionary
                    if isinstance(dep, dict):
                        # install the dependency
                        for k, v in dep.items():
                            if '|' in k:
                                k = k.split('|')[0]
                            if v == '*':
                                os.system(f'pip install {k}')
                            else:
                                os.system(f'pip install {k}{v}')

# log that script ended
print("local_setup.py ended")
