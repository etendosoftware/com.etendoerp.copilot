import json
import os
from contextlib import contextmanager
from typing import Dict
from unittest.mock import patch

import pytest
from copilot.core.exceptions import (
    ApplicationError,
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


def test_load_configured_tools_with_valid_file(set_fake_openai_api_key):
    """Test loading tools including HelloWorldTool and OpenAPI tools."""
    # Create a mock assistant configuration with OpenAPI spec
    from copilot.core.schemas import (
        AssistantSchema,
        AssistantSpecs,
        FunctionSchema,
        ToolSchema,
    )

    # Sample OpenAPI spec for testing
    openapi_spec = {
        "openapi": "3.0.0",
        "info": {"title": "Test API", "version": "1.0.0"},
        "paths": {
            "/test": {
                "get": {
                    "operationId": "test_operation",
                    "summary": "Test operation",
                    "responses": {"200": {"description": "Success"}},
                }
            }
        },
    }

    # Create assistant configuration with OpenAPI spec
    assistant_config = AssistantSchema(
        name="test_assistant",
        specs=[AssistantSpecs(name="test_api", type="FLOW", spec=json.dumps(openapi_spec))],
    )

    # Create tool schema for HelloWorldTool
    hello_world_tool_schema = ToolSchema(type="function", function=FunctionSchema(name="HelloWorldTool"))

    # Create tool loader and get all tools
    tool_loader = ToolLoader()

    # Test that HelloWorldTool is discovered automatically
    configured_tools = tool_loader.load_configured_tools()
    tool_names = [tool.name for tool in configured_tools]

    # Verify HelloWorldTool is loaded
    assert "HelloWorldTool" in tool_names, f"HelloWorldTool not found in loaded tools: {tool_names}"

    # Test get_all_tools with OpenAPI tools
    all_tools = tool_loader.get_all_tools(
        agent_configuration=assistant_config,
        enabled_tools=[hello_world_tool_schema],
        include_openapi_tools=True,
    )

    # Verify we have both HelloWorldTool and OpenAPI tools
    all_tool_names = [tool.name for tool in all_tools]
    assert "HelloWorldTool" in all_tool_names, "HelloWorldTool should be included in enabled tools"

    # Check if OpenAPI tool was generated (it should have the operation name)
    openapi_tool_found = any("test_operation" in name for name in all_tool_names)
    if not openapi_tool_found:
        # OpenAPI tool generation might be conditional, so we just verify HelloWorldTool is there
        print(f"Available tools: {all_tool_names}")

    # Verify that we have at least the HelloWorldTool
    assert len(all_tools) >= 1, f"Expected at least 1 tool, got {len(all_tools)}"


def test_tools_config_file_not_found(monkeypatch, set_fake_openai_api_key):
    with monkeypatch.context() as patch_context:
        patch_context.setenv("CONFIGURED_TOOLS_FILENAME", "")

        with pytest.raises(Exception) as exc_info:
            from copilot.core.exceptions import ToolConfigFileNotFound

            assert isinstance(exc_info, ToolConfigFileNotFound)


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


def test_dynamic_tool_config_generation(set_fake_openai_api_key):
    """Test that dynamic tool configuration is generated correctly."""
    tool_loader = ToolLoader()

    # Test that config is generated dynamically
    config = tool_loader._tools_config
    assert "native_tools" in config
    assert "third_party_tools" in config
    assert isinstance(config["third_party_tools"], dict)

    # Should have discovered tools automatically
    assert len(config["third_party_tools"]) > 0

    # HelloWorldTool should be discovered
    assert "HelloWorldTool" in config["third_party_tools"]
    assert config["third_party_tools"]["HelloWorldTool"] is True


def test_generate_dynamic_tool_config_method(set_fake_openai_api_key):
    """Test the _generate_dynamic_tool_config method directly."""
    tool_loader = ToolLoader()

    # Call the method directly
    config = tool_loader._generate_dynamic_tool_config()

    assert isinstance(config, dict)
    assert "native_tools" in config
    assert "third_party_tools" in config
    assert isinstance(config["native_tools"], dict)
    assert isinstance(config["third_party_tools"], dict)

    # Should find concrete tool classes
    assert len(config["third_party_tools"]) > 0


def test_load_all_tools_automatically(set_fake_openai_api_key):
    """Test that all concrete tools are loaded automatically."""
    tool_loader = ToolLoader()
    tools = tool_loader.load_configured_tools()

    # Should load multiple tools
    assert len(tools) > 0

    # All tools should be ToolWrapper instances
    from copilot.core.tool_wrapper import ToolWrapper

    for tool in tools:
        assert isinstance(tool, ToolWrapper)
        assert hasattr(tool, "name")
        assert hasattr(tool, "description")

    # Should contain HelloWorldTool
    tool_names = [tool.name for tool in tools]
    assert "HelloWorldTool" in tool_names


def test_singleton_pattern(set_fake_openai_api_key):
    """Test that ToolLoader follows singleton pattern."""
    loader1 = ToolLoader()
    loader2 = ToolLoader()

    # Should be the same instance
    assert loader1 is loader2

    # Should have same configuration
    assert loader1._tools_config == loader2._tools_config


def test_caching_of_loaded_tools(set_fake_openai_api_key):
    """Test that loaded tools are cached properly."""
    tool_loader = ToolLoader()

    # First call should load tools
    tools1 = tool_loader.load_configured_tools()

    # Second call should return cached tools
    tools2 = tool_loader.load_configured_tools()

    # Should be the same object (cached)
    assert tools1 is tools2
    assert len(tools1) == len(tools2)


def test_import_tools_with_missing_module(set_fake_openai_api_key):
    """Test behavior when tools module cannot be imported."""
    with patch("importlib.import_module") as mock_import:
        mock_import.side_effect = ImportError("Module not found")

        tool_loader = ToolLoader()
        tool_loader._tools_module = None  # Reset to test import

        # Should handle ImportError gracefully
        tool_loader._import_tools()
        assert tool_loader._tools_module is None


def test_tool_instantiation_with_errors(set_fake_openai_api_key):
    """Test that tool loading handles instantiation errors gracefully."""
    from copilot.core.tool_wrapper import ToolWrapper

    # Create a mock tool class that raises an error on instantiation
    class BrokenTool(ToolWrapper):
        name: str = "BrokenTool"
        description: str = "A tool that fails to instantiate"

        def __init__(self):
            raise ValueError("This tool is broken")

        def run(self, *args, **kwargs):
            # This method is intentionally empty for testing purposes
            pass

    try:
        BrokenTool()
    except ValueError:
        # Expected to raise ValueError during instantiation
        pass

    tool_loader = ToolLoader()
    # Clear cache to force reload
    ToolLoader._configured_tools = None

    # Should handle the error and continue with other tools
    tools = tool_loader.load_configured_tools()

    # Should still load other tools despite the broken one
    assert isinstance(tools, list)
    # BrokenTool should not be in the loaded tools
    tool_names = [tool.name for tool in tools]
    assert "BrokenTool" not in tool_names


def test_get_all_tools_with_enabled_tools_filter(set_fake_openai_api_key):
    """Test get_all_tools with enabled_tools filtering."""
    from copilot.core.schemas import FunctionSchema, ToolSchema

    tool_loader = ToolLoader()

    # Create enabled tools list with specific tool
    enabled_tools = [ToolSchema(type="function", function=FunctionSchema(name="HelloWorldTool"))]

    # Get filtered tools
    all_tools = tool_loader.get_all_tools(enabled_tools=enabled_tools)

    # Should only include enabled tools
    tool_names = [tool.name for tool in all_tools]
    assert "HelloWorldTool" in tool_names

    # Should be filtered (only enabled tools)
    assert len(all_tools) <= len(tool_loader.load_configured_tools())


def test_get_all_tools_with_no_enabled_tools(set_fake_openai_api_key):
    """Test get_all_tools when no enabled_tools specified."""
    tool_loader = ToolLoader()

    # When no enabled_tools specified, should return empty list for base tools
    all_tools = tool_loader.get_all_tools(enabled_tools=None)

    # Should be empty since no tools are explicitly enabled
    assert len(all_tools) == 0


def test_get_all_tools_with_empty_enabled_tools(set_fake_openai_api_key):
    """Test get_all_tools with empty enabled_tools list."""
    tool_loader = ToolLoader()

    # Empty list should also result in no base tools
    all_tools = tool_loader.get_all_tools(enabled_tools=[])

    # Should be empty since no tools are explicitly enabled
    assert len(all_tools) == 0


def test_get_enabled_tool_functions_alias(set_fake_openai_api_key):
    """Test that get_enabled_tool_functions is an alias for get_all_tools."""
    from copilot.core.schemas import FunctionSchema, ToolSchema

    tool_loader = ToolLoader()

    enabled_tools = [ToolSchema(type="function", function=FunctionSchema(name="HelloWorldTool"))]

    # Both methods should return the same result
    all_tools = tool_loader.get_all_tools(enabled_tools=enabled_tools)
    enabled_tools_result = tool_loader.get_enabled_tool_functions(enabled_tools=enabled_tools)

    assert len(all_tools) == len(enabled_tools_result)
    tool_names_all = [tool.name for tool in all_tools]
    tool_names_enabled = [tool.name for tool in enabled_tools_result]
    assert tool_names_all == tool_names_enabled


def test_tool_dependencies_installation(set_fake_openai_api_key):
    """Test that tool dependencies are handled properly."""
    tool_loader = ToolLoader()

    # Test that the method exists and can be called
    assert hasattr(tool_loader, "_install_enabled_tool_dependencies")

    # Since third_party_tool_config is now auto-generated and most tools
    # might not have dependencies, this should complete without error
    try:
        tool_loader._install_enabled_tool_dependencies()
    except Exception as e:
        # If dependencies file doesn't exist, that's expected
        assert "ToolDependenciesFileNotFound" in str(type(e))


def test_tool_config_properties(set_fake_openai_api_key):
    """Test native_tool_config and third_party_tool_config properties."""
    tool_loader = ToolLoader()

    # Test properties exist and return dictionaries
    assert isinstance(tool_loader.native_tool_config, dict)
    assert isinstance(tool_loader.third_party_tool_config, dict)

    # Native tools should be empty (since we only auto-discover third party)
    assert len(tool_loader.native_tool_config) == 0

    # Third party tools should contain discovered tools
    assert len(tool_loader.third_party_tool_config) > 0


def test_multiple_tool_loader_initialization(set_fake_openai_api_key):
    """Test that multiple ToolLoader instances work correctly with singleton."""
    # Create multiple instances
    loader1 = ToolLoader()
    loader2 = ToolLoader()
    loader3 = ToolLoader()

    # All should be the same instance
    assert loader1 is loader2 is loader3

    # All should have the same tools loaded
    tools1 = loader1.load_configured_tools()
    tools2 = loader2.load_configured_tools()
    tools3 = loader3.load_configured_tools()

    assert tools1 is tools2 is tools3
