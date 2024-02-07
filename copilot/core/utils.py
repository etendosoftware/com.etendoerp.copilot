import os
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


def get_full_question(question: QuestionSchema) -> str:
    if question.file_ids == None:
        return question.question
    result = question.question
    result += "\n" + "Local Files Ids for Context:"
    for file_id in question.file_ids:
        result += "\n - " + os.getcwd() + file_id
    return result


def read_optional_env_var(env_var_name: str, default_value: str) -> str:
    """Reads an optional environment variable and returns its value or the default one."""
    value = os.getenv(env_var_name, default_value)
    if not value:
        return default_value
    return value


def copilot_debug(message: str):
    """Prints a message if COPILOT_DEBUG is set to True."""
    if os.getenv("COPILOT_DEBUG", False):
        print_yellow(message)
