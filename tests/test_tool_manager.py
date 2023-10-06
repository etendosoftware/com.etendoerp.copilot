import json

import pytest
from copilot.core.bastian_tool import BastianFetcher
from copilot.core.tool_manager import load_configured_tools

from tools import HelloWorldTool


@pytest.fixture
def empty_config_file():
    temp_file = "/tmp/temp_file.txt"
    with open(temp_file, "w") as f:
        f.write("")
    yield temp_file


@pytest.fixture
def fake_json_config_file():
    json_file_path = "/tmp/temp_file.json"
    with open(json_file_path, "w") as json_file:
        json.dump({"Tools": "Enabled"}, json_file, indent=4)
    yield json_file_path


@pytest.fixture
def fake_valid_config_file():
    json_file_path = "/tmp/temp_file.json"
    with open(json_file_path, "w") as json_file:
        json.dump(
            {
                "native_tools": {"BastianFetcher": True, "XML_translation_tool": False},
                "third_party_tools": {"HelloWorldTool": True, "MyTool": False},
            },
            json_file,
            indent=4,
        )
    yield json_file_path


def test_load_configured_tools_empty_file(empty_config_file):
    with pytest.raises(Exception) as exc_info:
        load_configured_tools(config_filename=empty_config_file)
    assert str(exc_info.value) == "Unsupported tool configuration file format"


def test_load_configured_tools_no_valid_config(fake_json_config_file):
    assert load_configured_tools(config_filename=fake_json_config_file) == []


def test_load_configured_tools_with_valid_file(fake_valid_config_file):
    configured_tools = load_configured_tools(config_filename=fake_valid_config_file)
    assert len(configured_tools) == 2

    sorted_configured_tools = sorted(configured_tools, key=lambda x: x.name)
    assert isinstance(sorted_configured_tools[0], BastianFetcher)
    assert isinstance(sorted_configured_tools[1], HelloWorldTool)
