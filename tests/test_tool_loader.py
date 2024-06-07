import json
import os
from contextlib import contextmanager
from typing import Dict
from unittest.mock import Mock, patch

import pytest
from copilot.core.exceptions import (
    ApplicationError,
    ToolConfigFileNotFound,
    ToolDependenciesFileNotFound,
)
from copilot.core.tool_loader import ToolLoader


@pytest.fixture
def unsupported_config_file():
    temp_file = "/tmp/temp_file.txt"
    with open(temp_file, "w") as f:
        f.write("")
    yield temp_file
    os.remove(temp_file)


@pytest.fixture
def tool_loader(set_fake_openai_api_key) -> ToolLoader:
    return ToolLoader()


@contextmanager
def create_json_config_file(content: Dict):
    json_file_path = "/tmp/temp_file.json"
    with open(json_file_path, "w") as json_file:
        json.dump(content, json_file, indent=4)
    yield json_file_path
    os.remove(json_file_path)


def test_load_configured_tools_empty_file(unsupported_config_file, set_fake_openai_api_key):
    with pytest.raises(Exception, match="Unsupported tool configuration file format"):
        ToolLoader(config_filename=unsupported_config_file)


def test_load_configured_tools_no_valid_config(set_fake_openai_api_key):
    with create_json_config_file({"Tools": "Enabled"}) as json_config_file:
        tool_loader = ToolLoader(config_filename=json_config_file)
        assert tool_loader.native_tool_config == {}
        assert tool_loader.third_party_tool_config == {}
        assert tool_loader.load_configured_tools() == []


@patch("copilot.core.tool_loader.tool_installer", return_value=Mock())
def test_load_configured_tools_with_valid_file(fake_valid_config_file, set_fake_openai_api_key):
    from tools import HelloWorldTool

    json_config = {"native_tools": {"BastianFetcher": True}, "third_party_tools": {"HelloWorldTool": True}}
    with create_json_config_file(json_config) as json_config_file:
        tool_loader = ToolLoader(config_filename=json_config_file)
        tool_loader.install_dependencies = Mock()
        assert tool_loader.third_party_tool_config == {"HelloWorldTool": True}

        configured_tools = tool_loader.load_configured_tools()
        assert len(configured_tools) == 1

        sorted_configured_tools = sorted(configured_tools, key=lambda x: x.name)
        assert isinstance(sorted_configured_tools[0], HelloWorldTool)


def test_tools_config_file_not_found(monkeypatch, set_fake_openai_api_key):
    with monkeypatch.context() as patch_context:
        patch_context.setenv("CONFIGURED_TOOLS_FILENAME", "")

        with pytest.raises(Exception) as exc_info:
            from copilot.core.exceptions import ToolConfigFileNotFound

            assert isinstance(exc_info, ToolConfigFileNotFound)
            assert str(exc_info.value) == ToolConfigFileNotFound.message


def test_get_tool_config_raise_exc(tool_loader):
    with pytest.raises(ToolConfigFileNotFound, match=ToolConfigFileNotFound.message):
        tool_loader._get_tool_config(filepath=None)

    with pytest.raises(ToolConfigFileNotFound, match=ToolConfigFileNotFound.message):
        tool_loader._get_tool_config(filepath="wrong_path")


def test_get_tool_dependencies_raise_exc(tool_loader):
    with pytest.raises(ToolDependenciesFileNotFound, match=ToolDependenciesFileNotFound.message):
        tool_loader._get_tool_dependencies(filepath=None)

    with pytest.raises(ToolDependenciesFileNotFound, match=ToolDependenciesFileNotFound.message):
        tool_loader._get_tool_dependencies(filepath="wrong_path")


def test_get_tool_dependencies_wrong_format(unsupported_config_file):
    with pytest.raises(ApplicationError, match="Unsupported tool dependencies file format"):
        with create_json_config_file({"Tools": "Enabled"}) as json_config_file:
            ToolLoader()._get_tool_dependencies(json_config_file)


def test_is_tool_implemented_raise_false(tool_loader):
    assert tool_loader._is_tool_implemented(tool_name="sarasa") is False
    assert tool_loader._is_tool_implemented(tool_name="HelloWorldTool")
