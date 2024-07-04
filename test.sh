#!/bin/bash

# Patrones de expresiones regulares
# hotfix/AB-1234
pattern1='^hotfix/[a-zA-Z]{2,4}-[0-9]{1,4}$'
# hotfix/#1234-AB-122
pattern2='^hotfix/#[0-9]+-[a-zA-Z]{2}-[0-9]{2}$'
# feature/AB-1234
pattern3='^feature/[a-zA-Z]{2,4}-[0-9]{1,4}$'

# Ejemplo de entradas para verificar
inputs=(
  "feature/EML-562"
  "hotfix/#1234-BUG-123"
  "hotfix/BUG-12"
  "feature/BUG-1"
  "feature/XYZ-9999"
  "hotfix/#1234-AB-12"
  #add wrong inputs
  "feature/XYZ-9999-123"
  "hotfix/XYZaa-9999"
  "hotfix/#1234-A1B-123"
  "hotfix/AB-2221123"
  "feature/AB2-123"
  "feature/#AB-1234"
  "hotfix/AB-1s234"
)

# Iterar sobre las entradas y verificar los patrones
for input in "${inputs[@]}"; do
  if [[ $input =~ $pattern1 ]]; then
    echo "Coincide con el patrón 1: $input"
  elif [[ $input =~ $pattern2 ]]; then
    echo "Coincide con el patrón 2: $input"
  elif [[ $input =~ $pattern3 ]]; then
    echo "Coincide con el patrón 3: $input"
  else
    echo "No coincide: $input"
  fi
done
