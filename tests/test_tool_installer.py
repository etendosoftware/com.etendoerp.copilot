from unittest.mock import Mock
from langsmith import unit

import pytest
from copilot.core import tool_installer
from copilot.core.exceptions import ApplicationError, ToolDependencyMismatch
from copilot.core.tool_dependencies import Dependency

@pytest.fixture
def dependency() -> Dependency:
    return Dependency(name="pytest", version="==1.0.0")

def test_check_version_are_the_same(dependency):
    assert (
        tool_installer._check_version_mismatch(
            installed_version="1.0.0", required_version=dependency.required_version()
        )
        is False
    )

def test_check_version_mismatch(dependency):
    assert tool_installer._check_version_mismatch(
        installed_version="2.0.0", required_version=dependency.required_version()
    )

def test_check_installed_version_is_not_oldest(dependency):
    assert (
        tool_installer._check_installed_version_is_oldest(
            installed_version="1.1.1", required_version=dependency.required_version()
        )
        is False
    )

def test_check_installed_version_is_oldest(dependency):
    assert tool_installer._check_installed_version_is_oldest(
        installed_version="0.1.1", required_version=dependency.required_version()
    )

def test_package_is_not_installed():
    unnexistent_dependency = Dependency(name="sarasa", version="==1.0.0")
    assert tool_installer._is_package_installed(dependency=unnexistent_dependency) is False

def test_package_is_installed_but_is_oldest():
    tool_installer.importlib = Mock()
    tool_installer._check_version_mismatch = Mock(return_value=False)
    tool_installer._check_installed_version_is_oldest = Mock(return_value=True)
    assert tool_installer._is_package_installed(dependency=Mock())

def test_package_is_installed_mismatch():
    tool_installer.importlib = Mock()
    tool_installer._check_version_mismatch = Mock(return_value=True)
    tool_installer._check_installed_version_is_oldest = Mock(return_value=False)
    with pytest.raises(ToolDependencyMismatch):
        tool_installer._is_package_installed(dependency=Mock())

def test_pip_install_is_called_when_package_is_not_installed(dependency):
    mocked_pip_install = Mock()
    tool_installer._pip_install = mocked_pip_install

    tool_installer._is_package_installed = Mock(return_value=False)
    tool_installer._is_package_imported = Mock(side_effect=[True, True])

    tool_installer.install_dependencies(dependencies=[dependency])

    assert mocked_pip_install.called
    mocked_pip_install.assert_called_once_with(package=dependency.fullname())

def test_pip_install_is_called_when_package_is_not_imported(dependency):
    mocked_pip_install = Mock()
    tool_installer._pip_install = mocked_pip_install

    tool_installer._is_package_installed = Mock(return_value=True)
    tool_installer._is_package_imported = Mock(side_effect=[False, True])

    tool_installer.install_dependencies(dependencies=[dependency])

    assert mocked_pip_install.called
    mocked_pip_install.assert_called_once_with(package=dependency.fullname())

def test_package_needs_manual_installation(dependency):
    mocked_pip_install = Mock()
    tool_installer._pip_install = mocked_pip_install

    tool_installer._is_package_installed = Mock(return_value=True)
    tool_installer._is_package_imported = Mock(side_effect=[False, False])

    with pytest.raises(ApplicationError):
        tool_installer.install_dependencies(dependencies=[dependency])

        assert mocked_pip_install.called
        mocked_pip_install.assert_called_once_with(package=dependency.fullname())
