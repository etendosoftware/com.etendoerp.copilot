import os
from typing import Final

SUCCESS_CODE: Final[str] = "\u2713"


def print_red(message):
    print("\033[91m {}\033[00m".format(message))


def print_green(message):
    print("\033[92m {}\033[00m".format(message))


def print_yellow(message):
    print("\033[93m {}\033[00m".format(message))

def read_optional_env_var(env_var_name: str, default_value: str) -> str:
    """Reads an optional environment variable and returns its value or the default one."""
    value = os.getenv(env_var_name, default_value)
    if not value:
        return default_value
    return value
