import os
import shutil
import socket
from typing import Final

from colorama import Fore, Style
from copilot.core.schemas import QuestionSchema

SUCCESS_CODE: Final[str] = "\u2713"


def print_red(message):
    print(Fore.RED + Style.BRIGHT + f"{message}{Style.RESET_ALL}")


def print_orange(message):
    print(Fore.YELLOW + f"{message}{Style.RESET_ALL}")


def print_green(message):
    print(Fore.GREEN + Style.BRIGHT + f"{message}{Style.RESET_ALL}")


def print_blue(message):
    print(Fore.BLUE + Style.BRIGHT + f"{message}{Style.RESET_ALL}")


def print_yellow(message):
    print(Fore.YELLOW + Style.BRIGHT + f"{message}{Style.RESET_ALL}")


def print_violet(message):
    print(f"{Fore.MAGENTA} {message}{Style.RESET_ALL}")


def get_full_question(question: QuestionSchema) -> str:
    if question.local_file_ids is None or len(question.local_file_ids) == 0:
        return question.question
    result = question.question
    result += "\n" + "Local Files Ids for Context:"
    for file_id in question.local_file_ids:
        parent_dir_of_current_dir = os.path.dirname(os.getcwd())
        result += "\n - " + parent_dir_of_current_dir + file_id
    return result


def _handle_etendo_host_var(env_var_name, default_value):
    etendo_host_docker = os.getenv("ETENDO_HOST_DOCKER")
    if etendo_host_docker:
        copilot_debug(
            f" Reading ETENDO_HOST, existing ETENDO_HOST_DOCKER, overriding ETENDO_HOST with ETENDO_HOST_DOCKER."
            f" Value is {etendo_host_docker}"
        )
        return read_optional_env_var("ETENDO_HOST_DOCKER", default_value)
    return _read_env_var(env_var_name, default_value)


def read_optional_env_var(env_var_name: str, default_value: str) -> str:
    """Reads an optional environment variable and returns its value or the default one."""
    return _read_env_var(env_var_name, default_value)


def _read_env_var(env_var_name, default_value):
    value = os.getenv(env_var_name, default_value)
    if not value:
        copilot_debug(f"Environment variable {env_var_name} is not set, using default value {default_value}")
        return default_value
    copilot_debug(f"Reading optional environment variable {env_var_name} = {value}")
    return value


def read_optional_env_var_int(env_var_name: str, default_value: int) -> int:
    """Reads an optional environment variable and returns its value or the default one."""
    return int(read_optional_env_var(env_var_name, str(default_value)))


def copilot_debug(message: str):
    """Prints a message if COPILOT_DEBUG is set to True."""
    if is_debug_enabled():
        print_yellow(message)


def copilot_debug_custom(message: str, color: str):
    """Prints a message if COPILOT_DEBUG is set to True."""
    if is_debug_enabled():
        print(color + message)


def is_debug_enabled():
    return os.getenv("COPILOT_DEBUG", "False").lower() in "true"


def copilot_debug_event(message: str):
    """Prints a message if COPILOT_DEBUG_EVENT is set to True."""
    debug = os.getenv("COPILOT_DEBUG", "False").lower()
    debug_event = os.getenv("COPILOT_DEBUG_EVENT", "False").lower()
    if (debug in "true") and (debug_event in "true"):
        print_green(message)


def copilot_info(message: str):
    """Prints a message if COPILOT_DEBUG is set to True."""
    if (
        os.getenv("COPILOT_INFO", "False").lower() in "true"
        or os.getenv("COPILOT_DEBUG", "False").lower() in "true"
    ):
        print_violet(message)


def is_docker():
    """Check if the process is running in a Docker container."""
    # Verify if the process is running in a container
    if os.path.exists("/.dockerenv"):
        return True

    # Verify if the process is running in a container
    try:
        with open("/proc/1/cgroup", "rt") as f:
            for line in f:
                if "docker" in line or "containerd" in line:
                    return True
    except FileNotFoundError:
        pass

    # Verify if the process is running in a container
    hostname = socket.gethostname()
    if len(hostname) == 12 and hostname.isalnum():
        return True

    return False


def empty_folder(db_path):
    # Check if the provided path is a valid directory
    if not os.path.isdir(db_path):
        print(f"The path '{db_path}' is not a valid directory.")
        return

    # Iterate over the folder's contents
    for filename in os.listdir(db_path):
        file_path = os.path.join(db_path, filename)

        # If it's a directory, delete it recursively
        if os.path.isdir(file_path):
            shutil.rmtree(file_path)
        # If it's a file or a symlink, delete it
        elif os.path.isfile(file_path) or os.path.islink(file_path):
            os.unlink(file_path)

    print(f"All contents of the folder '{db_path}' have been deleted.")


def read_optional_env_var_float(env_var_name: str, default_value: float) -> float:
    """Reads an optional environment variable and returns its value or the default one."""
    return float(read_optional_env_var(env_var_name, str(default_value)))
