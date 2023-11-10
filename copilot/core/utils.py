from typing import Final

SUCCESS_CODE: Final[str] = u'\u2713'


def print_red(message):
    print("\033[91m {}\033[00m" .format(message))


def print_green(message):
    print("\033[92m {}\033[00m" .format(message))


def print_yellow(message):
    print("\033[93m {}\033[00m" .format(message))
