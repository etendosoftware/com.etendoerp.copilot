#!/bin/bash

if [ $# -eq 0 ]; then
    echo "Provide the value of NAME_MODEL as an argument."
    exit 1
fi

NAME_MODEL=$1

source_values=$(python3 conextion_assistant.py "$NAME_MODEL")

if [ -z "$source_values" ]; then
    echo "No data found."
    exit 1
fi


python3 test.py "$source_values"
