import json

import pytest


@pytest.fixture
def empty_config_file():
    temp_file = "/tmp/temp_file.txt"
    with open(temp_file, "w") as f:
        f.write("")
    yield temp_file
    import os

    os.remove(temp_file)


@pytest.fixture
def fake_json_config_file():
    json_file_path = "/tmp/temp_file.json"
    with open(json_file_path, "w") as json_file:
        json.dump({"Tools": "Enabled"}, json_file, indent=4)
    yield json_file_path
    import os

    os.remove(json_file_path)


def test_load_configured_tools_empty_file(empty_config_file, set_fake_openai_api_key):
    from copilot.core.tool_manager import load_configured_tools

    with pytest.raises(Exception) as exc_info:
        load_configured_tools(empty_config_file)

    assert str(exc_info.value) == "Unsupported tool configuration file format"


def test_load_configured_tools_no_valid_config(fake_json_config_file, set_fake_openai_api_key):
    from copilot.core.tool_manager import load_configured_tools

    assert load_configured_tools(fake_json_config_file) == []


def test_load_configured_tools_with_valid_file(fake_valid_config_file, set_fake_openai_api_key):
    from copilot.core.bastian_tool import BastianFetcher
    from copilot.core.tool_manager import load_configured_tools

    from tools import HelloWorldTool

    configured_tools = load_configured_tools(config_filename=fake_valid_config_file)
    assert len(configured_tools) == 2

    sorted_configured_tools = sorted(configured_tools, key=lambda x: x.name)
    assert isinstance(sorted_configured_tools[0], BastianFetcher)
    assert isinstance(sorted_configured_tools[1], HelloWorldTool)


def test_tools_config_file_not_found(monkeypatch, set_fake_openai_api_key):
    with monkeypatch.context() as patch_context:
        patch_context.setenv("CONFIGURED_TOOLS_FILENAME", "")

        with pytest.raises(Exception) as exc_info:
            from copilot.core.exceptions import ToolConfigFileNotFound

            assert isinstance(exc_info, ToolConfigFileNotFound)
            assert str(exc_info.value) == ToolConfigFileNotFound.message
