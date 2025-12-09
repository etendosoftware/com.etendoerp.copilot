#!/bin/bash

# Main module
main_module="com.etendoerp.copilot"

# Parse arguments
no_local_setup=false
skip_symlinks=false

for arg in "$@"; do
    case $arg in
        --no-local-setup)
            no_local_setup=true
            shift
            ;;
        --skip-symlinks)
            skip_symlinks=true
            shift
            ;;
    esac
done

echo "Starting script to clean and optionally create symbolic links for tools and tests"

# Navigate to the main_module and clean the symbolic links in tools and tests
cd "$main_module"

echo "Cleaning symbolic links in tools and tests folders"
cd tools
find . -type l -delete
cd ../tests
find . -type l -delete
cd ..

# Check if we should skip the symlink creation
if [ "$skip_symlinks" = true ]; then
    echo "Skipping symbolic link creation as --skip-symlinks was passed"
else
    # Go back to the original working directory to iterate over the submodules
    cd ..
    echo "Going back to the original working directory"
    echo "Current directory: $(pwd)"

    # Iterate over the submodules, excluding the main_module
    for module in ./*; do
        # Skip the main_module
        if [[ "$module" == "$main_module" ]]; then
            echo "Skipping main module: $module"
            continue
        fi

        # Create symbolic links in the tools folder of the main_module
        if [ -d "$module/tools" ]; then
            echo "Creating symbolic links for tools from $module"
            for tool in "$module/tools"/*; do
                tool_name=$(basename "$tool")
                if [ ! -e "$main_module/tools/$tool_name" ] && [[ "$tool_name" == *.py ]]; then
                    ln -s "../../$tool" "$main_module/tools/$tool_name"
                    echo "Symbolic link created for $tool_name in tools"
                elif [ -L "$main_module/tools/$tool_name" ]; then
                    ln -sf "../../$tool" "$main_module/tools/$tool_name"
                    echo "Symbolic link updated for $tool_name in tools"
                fi
            done
            # Also handle tools/schemas directory if present
            if [ -d "$module/tools/schemas" ]; then
                echo "Creating symbolic links for tools/schemas from $module"
                # Ensure destination directory exists
                mkdir -p "$main_module/tools/schemas"
                for schema_file in "$module/tools/schemas"/*; do
                    schema_name=$(basename "$schema_file")
                    dest="$main_module/tools/schemas/$schema_name"
                    # Only link python files or json/yaml schema files
                    if [[ "$schema_name" == *.py || "$schema_name" == *.json || "$schema_name" == *.yaml || "$schema_name" == *.yml ]]; then
                        if [ ! -e "$dest" ]; then
                            ln -s "../../$schema_file" "$dest"
                            echo "Symbolic link created for schema $schema_name"
                        elif [ -L "$dest" ]; then
                            ln -sf "../../$schema_file" "$dest"
                            echo "Symbolic link updated for schema $schema_name"
                        fi
                    fi
                done
            fi
        fi

        # Create symbolic links in the tests folder of the main_module
        if [ -d "$module/tests" ]; then
            echo "Creating symbolic links for tests from $module"
            for _test in "$module/tests"/*; do
                test_name=$(basename "$_test")
                if [ ! -e "$main_module/tests/$test_name" ] && [[ "$test_name" == *.py ]]; then
                    ln -s "../../$_test" "$main_module/tests/$test_name"
                    echo "Symbolic link created for $test_name in tests"
                elif [ -L "$main_module/tests/$test_name" ]; then
                    ln -sf "../../$_test" "$main_module/tests/$test_name"
                    echo "Symbolic link updated for $test_name in tests"
                fi
            done
            if [ -d "$module/tests/resources" ]; then
                echo "Copying resources folder from $module"
                cp -r "$module/tests/resources" "$main_module/tests/"
            fi
        fi
    done
fi

# Go back to the main_module
echo "Going back to the main module"
cd "$main_module"

# Execute the local_setup.py script if the --no-local-setup option was not passed
if [ "$no_local_setup" = false ]; then
    echo "Running local_setup.py"
    exec python3 local_setup.py
else
    echo "Skipping local_setup.py"
fi
