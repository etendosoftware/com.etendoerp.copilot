#!/bin/bash

# Name of the original script
original_script="./doToolSymLinks.sh"

# Parse arguments for --no-local-setup
no_local_setup=false

for arg in "$@"; do
    case $arg in
        --no-local-setup)
            no_local_setup=true
            shift
            ;;
    esac
done

# Build the command to execute the original script
cmd="$original_script --skip-symlinks"

# Add the --no-local-setup flag if it was passed as an argument
if [ "$no_local_setup" = true ]; then
    cmd="$cmd --no-local-setup"
fi

# Execute the original script with the corresponding flags
echo "Ejecutando: $cmd"
bash -c "$cmd"