#!/bin/bash

# Main module
main_module="com.etendoerp.copilot"

# Get the list of submodules, which are at the same level as the main module
for module in ../*; do # for example ../com.etendoerp.copilot.erp
    # Check if the submodule has a "tools" folder
    echo "Checking submodule: $module"
    if [ -d "$module/tools" ]; then # if the folder com.etendoerp.copilot.erp/tools exists
        echo "Submodule with tools found: $module"
        
        # Iterate over the files within the "tools" folder of the submodule
        for tool in "$module/tools"/*; do # for example ../com.etendoerp.copilot.erp/tools/Ejemplo.py
            # Get the filename without the path
            tool_name=$(basename "$tool") # tool_name = Ejemplo.py
            echo "Checking tool: $tool_name"
            echo "tool: $tool"
            # Check if the file already exists in the main module
            if [ ! -e "tools/$tool_name" ]; then # if the file com.etendoerp.copilot/tools/Ejemplo.py does not exist
                # Create the symbolic link, com.etendoerp.copilot/tools/Ejemplo.py -> com.etendoerp.copilot.erp/tools/Ejemplo.py
                echo "Creating symbolic link for $tool_name, command is: ln -s $tool tools/$tool_name"
                
                
                
                ln -s "../$tool" "tools/$tool_name" 
                echo "Symbolic link created for $tool_name"
            elif [ -L "tools/$tool_name" ]; then
                # Replace the existing symbolic link,  com.etendoerp.copilot/tools/Ejemplo.py -> com.etendoerp.copilot.erp/tools/Ejemplo.py
                ln -sf "../$tool" "tools/$tool_name" 
                echo "Symbolic link updated for $tool_name"
            else
                echo "The file $tool_name already exists in the main module."
            fi
        done
    fi
done

# come back to the main module
cd ../$main_module
#the tools_config file is created in the main module, the tools_config file is used to store the tools that are going to be used in the main module
#exec python3 local_setup.py
exec python3 local_setup.py
