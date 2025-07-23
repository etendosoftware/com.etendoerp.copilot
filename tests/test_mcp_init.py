"""
Tests for MCP __init__ module.

This module tests the main MCP module initialization and exports.
"""


class TestMCPModuleImports:
    """Test cases for MCP module imports and exports."""

    def test_import_all_exports(self):
        """Test that all exported items can be imported."""
        from copilot.core.mcp import (
            BaseResource,
            BaseTool,
            ResourceContent,
            SimplifiedDynamicMCPManager,
            ToolResult,
            get_simplified_dynamic_mcp_manager,
            is_mcp_enabled,
            start_mcp_with_cleanup,
            start_simplified_dynamic_mcp_server,
            start_simplified_dynamic_mcp_with_cleanup,
            stop_simplified_dynamic_mcp_server,
        )

        # Verify all items exist and are not None
        assert BaseTool is not None
        assert ToolResult is not None
        assert BaseResource is not None
        assert ResourceContent is not None
        assert SimplifiedDynamicMCPManager is not None
        assert start_simplified_dynamic_mcp_server is not None
        assert stop_simplified_dynamic_mcp_server is not None
        assert get_simplified_dynamic_mcp_manager is not None
        assert start_simplified_dynamic_mcp_with_cleanup is not None
        assert start_mcp_with_cleanup is not None
        assert is_mcp_enabled is not None

    def test_tool_classes_importable(self):
        """Test that tool-related classes can be imported."""
        from copilot.core.mcp import BaseTool, ToolResult

        # Verify they are classes
        assert isinstance(BaseTool, type)
        assert isinstance(ToolResult, type)

    def test_resource_classes_importable(self):
        """Test that resource-related classes can be imported."""
        from copilot.core.mcp import BaseResource, ResourceContent

        # Verify they are classes
        assert isinstance(BaseResource, type)
        assert isinstance(ResourceContent, type)

    def test_manager_class_importable(self):
        """Test that manager class can be imported."""
        from copilot.core.mcp import SimplifiedDynamicMCPManager

        # Verify it's a class
        assert isinstance(SimplifiedDynamicMCPManager, type)

    def test_manager_functions_importable(self):
        """Test that manager functions can be imported."""
        from copilot.core.mcp import (
            get_simplified_dynamic_mcp_manager,
            start_simplified_dynamic_mcp_server,
            stop_simplified_dynamic_mcp_server,
        )

        # Verify they are callable
        assert callable(start_simplified_dynamic_mcp_server)
        assert callable(stop_simplified_dynamic_mcp_server)
        assert callable(get_simplified_dynamic_mcp_manager)

    def test_utility_functions_importable(self):
        """Test that utility functions can be imported."""
        from copilot.core.mcp import (
            is_mcp_enabled,
            start_mcp_with_cleanup,
            start_simplified_dynamic_mcp_with_cleanup,
        )

        # Verify they are callable
        assert callable(start_simplified_dynamic_mcp_with_cleanup)
        assert callable(start_mcp_with_cleanup)
        assert callable(is_mcp_enabled)

    def test_star_import(self):
        """Test that star import works correctly."""
        import copilot.core.mcp as mcp_module

        # Check that __all__ is defined
        assert hasattr(mcp_module, "__all__")
        assert isinstance(mcp_module.__all__, list)

        # Verify all items in __all__ exist in the module
        for item in mcp_module.__all__:
            assert hasattr(mcp_module, item), f"Item {item} in __all__ but not found in module"

    def test_version_defined(self):
        """Test that version is defined."""
        import copilot.core.mcp as mcp_module

        assert hasattr(mcp_module, "__version__")
        assert isinstance(mcp_module.__version__, str)
        assert mcp_module.__version__ == "0.1.0"

    def test_docstring_exists(self):
        """Test that module docstring exists."""
        import copilot.core.mcp as mcp_module

        assert mcp_module.__doc__ is not None
        assert len(mcp_module.__doc__) > 0
        assert "Model Context Protocol" in mcp_module.__doc__


class TestMCPModuleContent:
    """Test the content and structure of the MCP module."""

    def test_all_list_content(self):
        """Test that __all__ contains expected items."""
        from copilot.core.mcp import __all__

        expected_items = [
            "BaseTool",
            "ToolResult",
            "BaseResource",
            "ResourceContent",
            "SimplifiedDynamicMCPManager",
            "start_simplified_dynamic_mcp_server",
            "stop_simplified_dynamic_mcp_server",
            "get_simplified_dynamic_mcp_manager",
            "start_simplified_dynamic_mcp_with_cleanup",
            "start_mcp_with_cleanup",
            "is_mcp_enabled",
        ]

        # Check that all expected items are in __all__
        for item in expected_items:
            assert item in __all__, f"Expected item {item} not found in __all__"

        # Check that __all__ doesn't contain unexpected items
        assert len(__all__) == len(
            expected_items
        ), f"__all__ contains unexpected items: {set(__all__) - set(expected_items)}"

    def test_no_private_exports(self):
        """Test that private items are not exported."""
        from copilot.core.mcp import __all__

        # Check that no private items (starting with _) are exported
        for item in __all__:
            assert not item.startswith("_"), f"Private item {item} should not be in __all__"

    def test_module_level_constants(self):
        """Test module-level constants and metadata."""
        import copilot.core.mcp as mcp_module

        # Check version format
        version = mcp_module.__version__
        assert isinstance(version, str)
        assert len(version.split(".")) >= 2, "Version should have at least major.minor format"

    def test_import_sources(self):
        """Test that imports come from correct sources."""
        # This test verifies the import structure without actually importing
        # to avoid circular dependencies during testing

        # Read the __init__.py file to verify import sources
        import inspect

        import copilot.core.mcp

        # Get the module file path
        module_file = inspect.getfile(copilot.core.mcp)

        # Verify the file exists and is readable
        assert module_file.endswith("__init__.py")

        with open(module_file, "r") as f:
            content = f.read()

        # Check that imports are from expected modules
        assert "from .resources import" in content
        assert "from .simplified_dynamic_manager import" in content
        assert "from .simplified_dynamic_utils import" in content
        assert "from .tools import" in content
        assert "from .utils import" in content


class TestMCPModuleCompatibility:
    """Test compatibility and integration aspects of the MCP module."""

    def test_classes_inheritance(self):
        """Test that imported classes maintain their inheritance."""
        from abc import ABC

        from copilot.core.mcp import BaseResource, BaseTool, ResourceContent, ToolResult
        from pydantic import BaseModel

        # Check ABC inheritance
        assert issubclass(BaseTool, ABC)
        assert issubclass(BaseResource, ABC)

        # Check Pydantic inheritance
        assert issubclass(ToolResult, BaseModel)
        assert issubclass(ResourceContent, BaseModel)

    def test_function_signatures_preserved(self):
        """Test that function signatures are preserved through imports."""
        import inspect

        from copilot.core.mcp import (
            is_mcp_enabled,
            start_mcp_with_cleanup,
            start_simplified_dynamic_mcp_server,
        )

        # Check that functions have proper signatures
        sig1 = inspect.signature(start_simplified_dynamic_mcp_server)
        sig2 = inspect.signature(is_mcp_enabled)
        sig3 = inspect.signature(start_mcp_with_cleanup)

        # Should not raise exceptions and should have parameter info
        assert hasattr(sig1.parameters, "__iter__")  # May have host/port parameters
        assert len(sig2.parameters) == 0  # Should have no parameters
        assert len(sig3.parameters) == 0  # Should have no parameters

    def test_class_instantiation(self):
        """Test that classes can be instantiated through module imports."""
        from copilot.core.mcp import (
            ResourceContent,
            SimplifiedDynamicMCPManager,
            ToolResult,
        )

        # Test class instantiation
        manager = SimplifiedDynamicMCPManager()
        assert manager is not None

        result = ToolResult(success=True)
        assert result.success is True

        content = ResourceContent(uri="test://uri")
        assert content.uri == "test://uri"

    def test_function_execution(self):
        """Test that functions can be executed through module imports."""
        from copilot.core.mcp import get_simplified_dynamic_mcp_manager, is_mcp_enabled

        # Test function execution
        enabled = is_mcp_enabled()
        assert isinstance(enabled, bool)

        manager = get_simplified_dynamic_mcp_manager()
        assert manager is not None


class TestMCPModuleDocumentation:
    """Test documentation aspects of the MCP module."""

    def test_module_docstring_content(self):
        """Test that module docstring contains relevant information."""
        import copilot.core.mcp as mcp_module

        docstring = mcp_module.__doc__
        assert docstring is not None

        # Check for key terms in docstring
        key_terms = [
            "Model Context Protocol",
            "MCP",
            "Etendo Copilot",
            "server",
        ]

        docstring_lower = docstring.lower()
        for term in key_terms:
            assert term.lower() in docstring_lower, f"Key term '{term}' not found in module docstring"

    def test_requirements_mentioned(self):
        """Test that requirements are mentioned in documentation."""
        import copilot.core.mcp as mcp_module

        docstring = mcp_module.__doc__
        assert docstring is not None

        # Check for requirements information
        assert "Requirements:" in docstring or "requirements" in docstring.lower()
        assert "Python" in docstring
        assert "FastMCP" in docstring

    def test_features_mentioned(self):
        """Test that key features are mentioned in documentation."""
        import copilot.core.mcp as mcp_module

        docstring = mcp_module.__doc__
        assert docstring is not None

        # Check for feature mentions
        features = [
            "server",
            "tool",
            "resource",
        ]

        docstring_lower = docstring.lower()
        for feature in features:
            assert feature in docstring_lower, f"Feature '{feature}' not mentioned in docstring"


class TestMCPModuleStructure:
    """Test the overall structure and organization of the MCP module."""

    def test_import_order(self):
        """Test that imports are organized logically."""
        import inspect

        import copilot.core.mcp

        # Get the source file
        module_file = inspect.getfile(copilot.core.mcp)

        with open(module_file, "r") as f:
            lines = f.readlines()

        # Find import lines
        import_lines = [line.strip() for line in lines if line.strip().startswith("from .")]

        # Should have imports from multiple submodules
        assert len(import_lines) > 0

        # Check that resources and tools are imported (core components)
        import_text = "\n".join(import_lines)
        assert "resources" in import_text
        assert "tools" in import_text

    def test_no_direct_implementation(self):
        """Test that __init__.py doesn't contain direct implementations."""
        import inspect

        import copilot.core.mcp

        # Get the source file
        module_file = inspect.getfile(copilot.core.mcp)

        with open(module_file, "r") as f:
            content = f.read()

        # Should not contain class definitions (only imports)
        assert "class " not in content, "__init__.py should not contain class definitions"

        # Should not contain function definitions (only imports)
        assert "def " not in content, "__init__.py should not contain function definitions"

    def test_clean_namespace(self):
        """Test that module namespace is clean."""
        import copilot.core.mcp as mcp_module

        # Get all public attributes
        public_attrs = [attr for attr in dir(mcp_module) if not attr.startswith("_")]

        # All items in __all__ should be public attributes
        for item in mcp_module.__all__:
            assert item in public_attrs, f"Item {item} in __all__ but not in public attributes"

        # Check that main functionality is properly exposed
        expected_items = [
            "BaseTool",
            "ToolResult",
            "BaseResource",
            "ResourceContent",
            "SimplifiedDynamicMCPManager",
            "start_simplified_dynamic_mcp_server",
            "stop_simplified_dynamic_mcp_server",
            "get_simplified_dynamic_mcp_manager",
            "start_simplified_dynamic_mcp_with_cleanup",
            "start_mcp_with_cleanup",
            "is_mcp_enabled",
        ]

        for item in expected_items:
            assert item in public_attrs, f"Expected item {item} not found in public attributes"
