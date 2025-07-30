import importlib
import subprocess

from baseutils.logging_envvar import (
    SUCCESS_CODE,
    print_green,
    print_orange,
    print_yellow,
)
from packaging import version
from packaging.specifiers import Specifier

from .exceptions import ToolDependencyMismatch
from .tool_dependencies import Dependencies, Dependency


def _pip_uninstall(package: str):
    """Uninstall the provided package via pip from code."""
    print_yellow(f"Running pip uninstall {package}")
    subprocess.check_call(["uv", "pip", "uninstall", "-y", package])


def _pip_install(package: str):
    """Install the provided package via pip from code."""
    print_yellow(f"Running pip install {package}")
    subprocess.check_call(["uv", "pip", "install", "--link-mode=copy", package])


def _check_version_mismatch(installed_version: str, required_version: str) -> bool:
    """Checks if there is a mismatch between the installed version and the required one."""
    installed = version.parse(installed_version)
    specifier = Specifier(f"=={required_version}")
    return installed not in specifier


def _check_installed_version_is_oldest(installed_version: str, required_version: str) -> bool:
    """Checks if the installed version is oldest in comparison with the required one."""
    installed = version.parse(installed_version)
    required = version.parse(required_version)
    return installed < required


def _is_package_installed(dependency: Dependency) -> bool:
    """Checks if package (a.k.a dependency) is locally installed in the required version."""
    # https://github.com/huggingface/transformers/blob/ed115b347347a1292dfd88a85d5bd9b8250c66e7/src/transformers/utils/import_utils.py#L41C5-L41C26
    package_exists = importlib.util.find_spec(dependency.name) is not None
    if not package_exists:
        return False

    # check the installed version matches with the required one
    try:
        installed_version = importlib.metadata.version(dependency.name)
    except importlib.metadata.PackageNotFoundError:
        return False

    is_version_mismatch: bool = _check_version_mismatch(
        installed_version=installed_version, required_version=dependency.required_version()
    )
    if not is_version_mismatch:
        return True

    is_installed_version_oldest: bool = _check_installed_version_is_oldest(
        installed_version=installed_version, required_version=dependency.required_version()
    )
    if is_installed_version_oldest:
        # we have an opportunity to reinstall it, so try it.
        return False

    raise ToolDependencyMismatch(dependency=dependency, installed_version=installed_version)


def _is_package_imported(dependency: Dependency, verbose: bool = True) -> bool:
    """Checks if package (a.k.a dependency) can be imported as normal module."""
    if verbose:
        print_yellow(f"Importing {dependency.name}")
    try:
        importlib.import_module(dependency.get_import_name())
    except Exception as ex:
        print_orange(
            f"Cannot import check the package {dependency.name}. This is not necessarily an error, because some "
            f"packages have different name when imported. "
            "This can be avoided by providing the import name in the tool_deps.toml file specifying both names separated"
            ' by a pipe(install_name|import_name). Example: "pillow|PIL" = "*"'
        )
        print_orange("ERROR:" + str(ex))

    if verbose:
        print_green(SUCCESS_CODE)

    return True


def install_dependencies(dependencies: Dependencies):
    """Given a list of dependencies it will try to installed one by one in order."""
    for dependency in dependencies:
        try:
            if not _is_package_installed(dependency=dependency):
                try:
                    _pip_install(package=dependency.fullname())
                except subprocess.CalledProcessError as e:
                    print_orange(f"[ERROR] Failed to install {dependency.fullname()}: {e}")
                    continue  # Skip to next dependency

            # if package is installed but can't be imported, retry
            if not _is_package_imported(dependency=dependency):
                try:
                    _pip_install(package=dependency.fullname())
                except subprocess.CalledProcessError as e:
                    print_orange(f"[ERROR] Failed to re-install {dependency.fullname()}: {e}")
                    continue

            if not _is_package_imported(dependency=dependency, verbose=False):
                print_orange(
                    f"[ERROR] {dependency.fullname()} installation fails, please try manually and rerun copilot"
                )
                continue
        except Exception as e:
            print_orange(f"[ERROR] Unexpected error installing {dependency.fullname()}: {e}")
            continue
