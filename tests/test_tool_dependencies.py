from unittest.mock import Mock, patch

from copilot.core.tool_dependencies import Dependency


def test_dependency_fullname_includes_version_when_present():
    assert Dependency(name="requests", version="==2.32.0").fullname() == "requests==2.32.0"


def test_dependency_fullname_omits_missing_version_for_latest():
    assert Dependency(name="requests").fullname() == "requests"


def test_required_version_extracts_numeric_parts_from_specifier():
    assert Dependency(name="requests", version=">=2.32.0,<3").required_version() == "2.32.0.3"


def test_required_version_uses_installed_distribution_when_version_is_missing():
    distribution = Mock(version="9.8.7")

    with patch("copilot.core.tool_dependencies.pkg_resources.get_distribution", return_value=distribution):
        assert Dependency(name="installed-package").required_version() == "9.8.7"


def test_get_import_name_defaults_to_dependency_name():
    assert Dependency(name="python-dotenv").get_import_name() == "python-dotenv"


def test_get_import_name_uses_explicit_import_name():
    assert Dependency(name="python-dotenv", import_name="dotenv").get_import_name() == "dotenv"
