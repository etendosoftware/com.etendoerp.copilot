import os
import socket
from typing import Final

from copilot.core.schemas import QuestionSchema

SUCCESS_CODE: Final[str] = "\u2713"


def print_red(message):
    print("\033[91m {}\033[00m".format(message))


def print_green(message):
    print("\033[92m {}\033[00m".format(message))


def print_blue(message):
    print("\033[94m {}\033[00m".format(message))


def print_yellow(message):
    print("\033[93m {}\033[00m".format(message))


def print_violet(message):
    print("\033[95m {}\033[00m".format(message))


def get_full_question(question: QuestionSchema) -> str:
    if question.local_file_ids is None or len(question.local_file_ids) == 0:
        return question.question
    result = question.question
    result += "\n" + "Local Files Ids for Context:"
    for file_id in question.local_file_ids:
        parent_dir_of_current_dir = os.path.dirname(os.getcwd())
        result += "\n - " + parent_dir_of_current_dir + file_id
    return result


def read_optional_env_var(env_var_name: str, default_value: str) -> str:
    """Reads an optional environment variable and returns its value or the default one."""
    copilot_debug(f"Reading optional environment variable {env_var_name} with default value {default_value}")
    value = os.getenv(env_var_name, default_value)
    if not value:
        copilot_debug(f"Environment variable {env_var_name} is not set, using default value {default_value}")
        return default_value
    return value


def read_optional_env_var_int(env_var_name: str, default_value: int) -> int:
    """Reads an optional environment variable and returns its value or the default one."""
    return int(read_optional_env_var(env_var_name, str(default_value)))


def copilot_debug(message: str):
    """Prints a message if COPILOT_DEBUG is set to True."""
    if os.getenv("COPILOT_DEBUG", 'False').lower() in "true":
        print_yellow(message)


def copilot_info(message: str):
    """Prints a message if COPILOT_DEBUG is set to True."""
    if os.getenv("COPILOT_INFO", 'False').lower() in "true" or os.getenv("COPILOT_DEBUG", 'False').lower() in "true":
        print_violet(message)




def is_docker():
    # Verify if the process is running in a container
    if os.path.exists('/.dockerenv'):
        return True

    # Verify if the process is running in a container
    try:
        with open('/proc/1/cgroup', 'rt') as f:
            for line in f:
                if 'docker' in line or 'containerd' in line:
                    return True
    except FileNotFoundError:
        pass

    # Verify if the process is running in a container
    hostname = socket.gethostname()
    if len(hostname) == 12 and hostname.isalnum():
        return True

    return False
