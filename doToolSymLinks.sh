#!/bin/bash

# Main module
main_module="com.etendoerp.copilot"

# Parse arguments
no_local_setup=false

for arg in "$@"; do
    case $arg in
        --no-local-setup)
            no_local_setup=true
            shift
            ;;
    esac
done

echo "Starting script to create symbolic links for tools and tests"

# Navegar al main_module y limpiar los enlaces simbólicos en tools y tests
cd "$main_module"

echo "Cleaning symbolic links in tools and tests folders"
cd tools
find . -type l -delete
cd ../tests
find . -type l -delete
cd ..

# Volver al directorio de trabajo original para iterar sobre los submódulos
cd ..
echo "Going back to the original working directory"
echo "Current directory: $(pwd)"


# Iterar sobre los submódulos, excluyendo el main_module
for module in ./*; do
    # Saltar el main_module
    if [[ "$module" == "$main_module" ]]; then
        echo "Skipping main module: $module"
        continue
    fi

    # Crear enlaces simbólicos en la carpeta tools del main_module
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
    fi

    # Crear enlaces simbólicos en la carpeta tests del main_module
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

# Volver a la carpeta del main_module
echo "Going back to the main module"
cd "$main_module"

# Ejecutar el script local_setup.py si no se pasó la opción --no-local-setup
if [ "$no_local_setup" = false ]; then
    echo "Running local_setup.py"
    exec python3 local_setup.py
else
    echo "Skipping local_setup.py"
fi