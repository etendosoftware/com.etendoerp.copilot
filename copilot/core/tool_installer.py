import importlib

from pip._internal import main as pip_main

from .exceptions import ApplicationError
from .tool_dependencies import Dependencies
from .utils import print_green, print_yellow, print_red, SUCCESS_CODE


def _pip_install(package: str):
    """Install the provided package via pip install from code.
    """
    print_yellow(f"Running pip install {package}")
    pip_main(['install', package])


def install_dependencies(dependencies: Dependencies):
    """Given a list of dependencies it will try to installed one by one in order. After
    the installation it will try to import the dependency to ensure it was installed
    successfully, in case there was an error it will raise an error requiring manuall
    installation.
    """
    for dependency in dependencies:
        package_name: str = dependency.fullname()
        print_yellow(f"Importing {package_name}")
        try:
            importlib.import_module(dependency.name)
            print_green(SUCCESS_CODE)
        except Exception as ex:
            print_red(str(ex))
            try:
                _pip_install(package=package_name)
                print_yellow(f"Importing after installation {package_name}")
                try:
                    importlib.import_module(dependency.name)
                    print_green(SUCCESS_CODE)
                except Exception as ex:
                    raise ApplicationError(
                        f"{package_name} installation fails: {str(ex)}, please try manually and rerun copilot"
                    )

            except Exception as ex:
                raise ApplicationError(
                    f"{package_name} installation fails: {str(ex)}, please try manually and rerun copilot"
                )

