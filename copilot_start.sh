#!/bin/bash

# Constants
TRUE='true'
DEFAULT_TAG='master'
DEFAULT_NAME='copilot-default'
DEFAULT_PULL_IMAGE='false'
# List of files/directories to exclude
EXCLUDES=(
  "bitbucket-pipelines.yml" # specific file
  "build.gradle"            # specific file
  "sonar-project.properties" # specific file
  "tasks.gradle"            # specific file
  "doToolSymLinks.sh"       # specific file
  "README.md"               # specific file
  "etendo-resources"        # specific directory
  "src"                     # specific directory
  "src-db"                  # specific directory
  "tests"                   # specific directory
  "web"                     # specific directory
  ".*"                      # exclude hidden files and directories (dot files)
)

#!/bin/bash

# Function to check if a command exists
check_command() {
  local cmd="$1"
  local install_instructions="$2"

  echo "Checking if $cmd is installed..."
  if ! command -v "$cmd" &> /dev/null; then
    echo "Error: $cmd is not installed."
    echo "Please install it following these instructions: $install_instructions"
    exit 1
  else
    echo "$cmd is installed."
  fi
}

# Function to check if Python is the correct version
check_python_version() {
  local required_version="$1"
  local python_version

  echo "Checking if Python $required_version or higher is installed..."
  if command -v python3 &> /dev/null; then
    python_version=$(python3 -c "import sys; print('.'.join(map(str, sys.version_info[:2])))")
    if [[ $(echo "$python_version >= $required_version" | bc -l) -eq 0 ]]; then
      echo "Error: Python version $required_version or higher is required. You have $python_version."
      exit 1
    else
      echo "Python $python_version is installed and meets the requirement."
    fi
  else
    echo "Error: Python3 is not installed."
    echo "Please install Python3 version $required_version or higher."
    exit 1
  fi
}

# Log start of dependency check
echo "Starting dependency check..."

# Check if Docker is installed
check_command "docker" "https://docs.docker.com/get-docker/"

# Check if Python 3.10 or higher is installed
check_python_version "3.10"

# Log end of dependency check
echo "Dependency check completed successfully. All required dependencies are installed."

# Function to load variables from gradle.properties
load_properties() {
  local file=$1
  if [[ -f "$file" ]]; then
    while IFS='=' read -r key value; do
      if [[ ! $key =~ ^# ]]; then
        # Replace periods with underscores in the variable name
        local new_key="${key//./_}"
        export "$new_key=$value"
        echo "Loaded variable: $new_key=$value"
      fi
    done < "$file"
  else
    echo "Error: $file not found."
    exit 1
  fi
}

# Function to clear the content of the copilot directory
clear_copilot_directory() {
  local dest_dir="$pwd/build/copilot"

  if [[ -d "$dest_dir" ]]; then
    echo "Clearing content of $dest_dir"
    rm -rf "$dest_dir/*" || { echo "Error: Unable to clear the content of $dest_dir."; exit 1; }
    echo "Content of $dest_dir cleared."
  fi
}

# Function to create directory if it doesn't exist
create_directory_if_not_exists() {
  local dir="$1"

  if [[ ! -d "$dir" ]]; then
    echo "Creating directory $dir"
    mkdir -p "$dir" || { echo "Error: Unable to create the directory $dir."; exit 1; }
  fi
}

# Function to append content from one file to another
append_file_content() {
  local src_file="$1"
  local dest_file="$2"

  echo "Appending content of $src_file to $dest_file"
  cat "$src_file" >> "$dest_file"
}

# Function to find files by name and append their content
find_and_append_tools_deps() {
  local search_dir="$1"
  local dest_file="$2"
  local exclude="$3"

  find "$search_dir" -name "tools_deps.toml" -not -path "$exclude" | while read -r file; do
    append_file_content "$file" "$dest_file"
  done
}

# Function to copy directory content to another directory
copy_tools_directories() {
  local search_dir="$1"
  local tools_dest_dir="$2"

  find "$search_dir" -maxdepth 1 -type d ! -name "com.etendoerp.copilot" | while read -r module_dir; do
    if [[ -d "$module_dir/tools" ]]; then
      echo "Copying files from $module_dir/tools/ to $tools_dest_dir"
      rsync -a "$module_dir/tools/" "$tools_dest_dir/" || { echo "Error: Failed to copy content from $module_dir/tools to $tools_dest_dir."; exit 1; }
    fi
  done
  echo "Copied content from all tools directories in the first level of modules except com.etendoerp.copilot."
}

# Function to prepare the copilot directory
prepare_copilot_directory() {
  local src_dir="$pwd/modules/com.etendoerp.copilot"
  local dest_dir="$pwd/build/copilot"

  echo "Current working directory: $pwd"

  # Create the destination directory if it does not exist
  create_directory_if_not_exists "$dest_dir"

  # Check if the source directory exists
  if [[ ! -d "$src_dir" ]]; then
    echo "Error: The directory $src_dir does not exist."
    exit 1
  fi

  # Create rsync exclude pattern from the EXCLUDES array
  local rsync_excludes=()
  for exclude in "${EXCLUDES[@]}"; do
    rsync_excludes+=(--exclude="$exclude")
  done

  # Copy the content from com.etendoerp.copilot to build/copilot, excluding specified files and directories
  echo "Copying files from $src_dir to $dest_dir with exclusions"
  rsync -a "${rsync_excludes[@]}" "$src_dir/" "$dest_dir/" || { echo "Error: Failed to copy content from $src_dir to $dest_dir."; exit 1; }
  echo "Copied content from $src_dir to $dest_dir with exclusions"

  # Get all tools_deps.toml files from the subdirectories of modules and append their content to the copied tools_deps.toml
  local src_tools_deps_toml="$dest_dir/tools_deps.toml"
  find_and_append_tools_deps "$pwd/modules" "$src_tools_deps_toml" "$pwd/modules/com.etendoerp.copilot/tools_deps.toml"
  echo "Content appended from all tools_deps.toml files."

  # Copy the tools directory content from each module to build/copilot/tools
  local tools_dest_dir="$dest_dir/tools"
  create_directory_if_not_exists "$tools_dest_dir"
  copy_tools_directories "$pwd/modules" "$tools_dest_dir"
}

# Load environment variables from gradle.properties
load_properties "gradle.properties"

# Set the working directory
pwd=$(pwd)

# Check if pwd has a value
if [[ -z "$pwd" ]]; then
  echo "Error: Unable to get the current working directory."
  exit 1
fi

# Clear the content of the copilot directory
clear_copilot_directory

# Prepare the copilot directory
prepare_copilot_directory

# Functions to get environment variables
get_copilot_image_tag() {
  if [ -z "$COPILOT_IMAGE_TAG" ]; then
    echo $DEFAULT_TAG
  else
    echo $COPILOT_IMAGE_TAG
  fi
}

get_container_name() {
  if [ -z "$COPILOT_DOCKER_CONTAINER_NAME" ]; then
    echo $DEFAULT_NAME
  else
    echo $COPILOT_DOCKER_CONTAINER_NAME
  fi
}

# Function to get whether to pull the Docker image
get_pull_docker_image() {
  if [[ -z "$COPILOT_PULL_IMAGE" ]]; then
    echo "$DEFAULT_PULL_IMAGE"  # Ensure quotes are included around the value
  else
    if [[ "$COPILOT_PULL_IMAGE" == "$TRUE" ]]; then
      echo "$TRUE"
    else
      echo "false"
    fi
  fi
}

# Function to pull the Docker image
pull_docker_image() {
  local tag=$1
  echo "Docker image tag: etendo/$COPILOT_DOCKER_REPO:$tag"
  docker pull etendo/$COPILOT_DOCKER_REPO:$tag
  if [[ $? -eq 0 && ! $(docker images -q etendo/$COPILOT_DOCKER_REPO:$tag) ]]; then
    echo "Docker image updated successfully."
    return 0
  fi
  return 1
}

# Function to modify properties file
modify_properties_file() {
  local prop_file="$1"
  local new_prop_file="$2"

  # Create the directory for new_prop_file if it doesn't exist
  create_directory_if_not_exists "$(dirname "$new_prop_file")"

  # Remove any existing new_prop_file and create a new one
  rm -f "$new_prop_file"
  touch "$new_prop_file"

  if [[ ! -f "$prop_file" ]]; then
    echo "Error: $prop_file not found."
    exit 1
  fi

  # Copy the content of gradle.properties to copilot.properties
  cp "$prop_file" "$new_prop_file"

  # Check if the copilot.properties file was created successfully
  if [[ -f "$new_prop_file" ]]; then
    echo "File $new_prop_file created successfully."
  else
    echo "Error: Unable to create $new_prop_file."
    exit 1
  fi
}

# Function to run the Docker container
run_docker_container() {
  local container_name="$1"
  local port="$2"
  local tag="$3"
  local new_prop_file="$4"

  docker run -d --env-file="$new_prop_file" --name "$container_name" --add-host=host.docker.internal:host-gateway -p "$port:$port" -v "$pwd/build/copilot/:/app/" -v "$pwd/modules:/modules/" --restart unless-stopped etendo/$COPILOT_DOCKER_REPO:$tag
}

run_new_container() {
  local container_name=$1
  local port=$2
  local tag=$3

  echo "Working directory: $pwd"

  # Modify gradle.properties
  local prop_file="$pwd/gradle.properties"
  local new_prop_file="$pwd/build/copilot/copilot.properties"

  modify_properties_file "$prop_file" "$new_prop_file"

  # Show critical variables
  echo "Container name: $container_name"
  echo "Port: $port"
  echo "Docker image: etendo/$COPILOT_DOCKER_REPO:$tag"
  echo "Env file: $new_prop_file"

  # Run the Docker container
  run_docker_container "$container_name" "$port" "$tag" "$new_prop_file"

  # Remove the temporary file
  #rm -f "$new_prop_file"
}

delete_container() {
  local container_name=$1
  docker rm "$container_name" || echo "Failed to remove Docker container."
}

# Main script
main() {
  echo "*****************************************************"
  echo "* Performing copilot start task."
  echo "*****************************************************"

  COPILOT_PORT="${COPILOT_PORT:-5050}"  # Define a default port if not set
  COPILOT_IMAGE_TAG=$(get_copilot_image_tag)
  echo "COPILOT_IMAGE_TAG=$COPILOT_IMAGE_TAG"  # Print the image tag variable

  COPILOT_PULL_IMAGE=$(get_pull_docker_image)
  echo "COPILOT_PULL_IMAGE=$COPILOT_PULL_IMAGE"  # Print the image pull variable

  COPILOT_DOCKER_CONTAINER_NAME=$(get_container_name)
  echo "COPILOT_DOCKER_CONTAINER_NAME=$COPILOT_DOCKER_CONTAINER_NAME"  # Print the container name

  echo "OPENAI_API_KEY=$OPENAI_API_KEY"
  # Check if critical variables are defined
  if [[ -z "$OPENAI_API_KEY" ]]; then
    echo "Error: OPEN_API_KEY is not defined."
    exit 1
  fi

  # Check if critical variables are defined
  if [[ -z "$COPILOT_DOCKER_REPO" ]]; then
    echo "Error: COPILOT_DOCKER_REPO is not defined."
    exit 1
  fi

  # If the image should be pulled, call the corresponding function
  if [[ "$COPILOT_PULL_IMAGE" == "$TRUE" ]]; then
    pull_docker_image "$COPILOT_IMAGE_TAG"
  fi

  echo 'Deleting existing container...'
  delete_container "$COPILOT_DOCKER_CONTAINER_NAME"

  echo 'Creating new container...'
  run_new_container "$COPILOT_DOCKER_CONTAINER_NAME" "$COPILOT_PORT" "$COPILOT_IMAGE_TAG"
}

main "$@"
