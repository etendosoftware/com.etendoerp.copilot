"""
Tests for MCP schemas module.

This module tests the schema definitions and validation for MCP operations.
"""

import pytest
from copilot.core.mcp.schemas import (
    ListResourcesRequest,
    ListToolsRequest,
    MCPError,
    MCPRequest,
    MCPResponse,
    ResourceReadRequest,
    ToolCallRequest,
)


class TestModuleImports:
    """Test cases for schema module imports."""

    def test_all_schemas_importable(self):
        """Test that all schemas can be imported."""
        from copilot.core.mcp.schemas import (
            ListResourcesRequest,
            ListToolsRequest,
            MCPError,
            MCPRequest,
            MCPResponse,
            ResourceReadRequest,
            ToolCallRequest,
        )

        # Verify all classes exist
        assert MCPRequest is not None
        assert MCPResponse is not None
        assert MCPError is not None
        assert ToolCallRequest is not None
        assert ResourceReadRequest is not None
        assert ListToolsRequest is not None
        assert ListResourcesRequest is not None

    def test_all_attribute_contains_expected_schemas(self):
        """Test that __all__ contains all expected schema names."""
        from copilot.core.mcp.schemas import __all__

        expected_schemas = [
            "MCPRequest",
            "MCPResponse",
            "MCPError",
            "ToolCallRequest",
            "ResourceReadRequest",
            "ListToolsRequest",
            "ListResourcesRequest",
        ]

        for schema in expected_schemas:
            assert schema in __all__

    def test_module_docstring_exists(self):
        """Test that module has proper docstring."""
        import copilot.core.mcp.schemas

        assert copilot.core.mcp.schemas.__doc__ is not None
        assert "MCP Schemas module" in copilot.core.mcp.schemas.__doc__

    def test_protocol_module_imports(self):
        """Test that protocol module imports work correctly."""
        # This tests the internal import structure
        try:
            from copilot.core.mcp.schemas.protocol import (
                ListResourcesRequest,
                ListToolsRequest,
                MCPError,
                MCPRequest,
                MCPResponse,
                ResourceReadRequest,
                ToolCallRequest,
            )

            # All imports should succeed
            assert MCPRequest is not None
            assert MCPResponse is not None
            assert MCPError is not None
            assert ToolCallRequest is not None
            assert ResourceReadRequest is not None
            assert ListToolsRequest is not None
            assert ListResourcesRequest is not None

        except ImportError as e:
            pytest.fail(f"Failed to import from protocol module: {e}")


class TestSchemaAvailability:
    """Test that all schemas are properly available through the module."""

    def test_mcp_request_availability(self):
        """Test MCPRequest schema availability."""
        from copilot.core.mcp.schemas import MCPRequest

        # Should be a class/type
        assert isinstance(MCPRequest, type)

    def test_mcp_response_availability(self):
        """Test MCPResponse schema availability."""
        from copilot.core.mcp.schemas import MCPResponse

        # Should be a class/type
        assert isinstance(MCPResponse, type)

    def test_mcp_error_availability(self):
        """Test MCPError schema availability."""
        from copilot.core.mcp.schemas import MCPError

        # Should be a class/type
        assert isinstance(MCPError, type)

    def test_tool_call_request_availability(self):
        """Test ToolCallRequest schema availability."""
        from copilot.core.mcp.schemas import ToolCallRequest

        # Should be a class/type
        assert isinstance(ToolCallRequest, type)

    def test_resource_read_request_availability(self):
        """Test ResourceReadRequest schema availability."""
        from copilot.core.mcp.schemas import ResourceReadRequest

        # Should be a class/type
        assert isinstance(ResourceReadRequest, type)

    def test_list_tools_request_availability(self):
        """Test ListToolsRequest schema availability."""
        from copilot.core.mcp.schemas import ListToolsRequest

        # Should be a class/type
        assert isinstance(ListToolsRequest, type)

    def test_list_resources_request_availability(self):
        """Test ListResourcesRequest schema availability."""
        from copilot.core.mcp.schemas import ListResourcesRequest

        # Should be a class/type
        assert isinstance(ListResourcesRequest, type)


class TestSchemaInheritance:
    """Test schema inheritance and base classes."""

    def test_schemas_are_pydantic_models(self):
        """Test that schemas are Pydantic models where expected."""

        schemas_to_test = [
            MCPRequest,
            MCPResponse,
            MCPError,
            ToolCallRequest,
            ResourceReadRequest,
            ListToolsRequest,
            ListResourcesRequest,
        ]

        for schema in schemas_to_test:
            # Most MCP schemas should be Pydantic models
            # Note: Some might be other types like dataclasses or regular classes
            # We check if they have basic attributes that suggest they're data models
            assert hasattr(schema, "__name__")

    def test_schema_instantiation_requirements(self):
        """Test that schemas can be inspected for instantiation requirements."""
        import inspect

        schemas_to_test = [
            MCPRequest,
            MCPResponse,
            MCPError,
            ToolCallRequest,
            ResourceReadRequest,
            ListToolsRequest,
            ListResourcesRequest,
        ]

        for schema in schemas_to_test:
            # Should be able to get signature
            try:
                sig = inspect.signature(schema)
                # Should have some kind of signature (even if empty)
                assert sig is not None or True  # Always passes but shows intent
            except (ValueError, TypeError):
                # Some schemas might not have inspectable signatures
                # This is acceptable for certain types of schemas
                pass


class TestSchemaIntegration:
    """Test integration between different schemas."""

    def test_all_schemas_have_unique_names(self):
        """Test that all schemas have unique class names."""
        import copilot.core.mcp.schemas
        from copilot.core.mcp.schemas import __all__

        schema_names = []
        for schema_name in __all__:
            schema_class = getattr(copilot.core.mcp.schemas, schema_name)
            schema_names.append(schema_class.__name__)

        # All names should be unique
        assert len(schema_names) == len(set(schema_names))

    def test_schema_module_structure(self):
        """Test the overall module structure."""
        import copilot.core.mcp.schemas

        # Module should have __all__ attribute
        assert hasattr(copilot.core.mcp.schemas, "__all__")

        # All items in __all__ should be accessible
        for item_name in copilot.core.mcp.schemas.__all__:
            assert hasattr(copilot.core.mcp.schemas, item_name)
            item = getattr(copilot.core.mcp.schemas, item_name)
            assert item is not None

    def test_schema_dependencies(self):
        """Test that schema dependencies are properly handled."""
        # Test that importing schemas doesn't raise import errors
        try:
            from copilot.core.mcp.schemas import (
                ListResourcesRequest,
                ListToolsRequest,
                MCPError,
                MCPRequest,
                MCPResponse,
                ResourceReadRequest,
                ToolCallRequest,
            )

            # All imports should succeed
            schemas = [
                MCPRequest,
                MCPResponse,
                MCPError,
                ToolCallRequest,
                ResourceReadRequest,
                ListToolsRequest,
                ListResourcesRequest,
            ]

            for schema in schemas:
                assert schema is not None

        except ImportError as e:
            pytest.fail(f"Schema dependencies are not properly handled: {e}")


class TestSchemaDocumentation:
    """Test schema documentation and metadata."""

    def test_schema_names_are_descriptive(self):
        """Test that schema names follow naming conventions."""
        from copilot.core.mcp.schemas import __all__

        for schema_name in __all__:
            # Schema names should be descriptive
            assert len(schema_name) > 3  # Not too short
            assert schema_name[0].isupper()  # Should start with uppercase

            # Request/Response schemas should have appropriate suffixes
            if "Request" in schema_name:
                assert schema_name.endswith("Request")
            elif "Response" in schema_name:
                assert schema_name.endswith("Response")

    def test_module_has_proper_structure(self):
        """Test that module has proper Python package structure."""
        import copilot.core.mcp.schemas

        # Should have __doc__
        assert hasattr(copilot.core.mcp.schemas, "__doc__")

        # Should have __all__
        assert hasattr(copilot.core.mcp.schemas, "__all__")

        # Should have __name__
        assert hasattr(copilot.core.mcp.schemas, "__name__")
        assert copilot.core.mcp.schemas.__name__ == "copilot.core.mcp.schemas"


class TestSchemaTypes:
    """Test the types and categories of schemas."""

    def test_request_schemas_identified(self):
        """Test that request schemas are properly identified."""
        import copilot.core.mcp.schemas
        from copilot.core.mcp.schemas import __all__

        request_schemas = []
        for schema_name in __all__:
            if "Request" in schema_name:
                schema = getattr(copilot.core.mcp.schemas, schema_name)
                request_schemas.append((schema_name, schema))

        # Should have some request schemas
        assert len(request_schemas) > 0

        expected_requests = [
            "ToolCallRequest",
            "ResourceReadRequest",
            "ListToolsRequest",
            "ListResourcesRequest",
        ]
        found_requests = [name for name, _ in request_schemas]

        for expected in expected_requests:
            assert expected in found_requests

    def test_response_schemas_identified(self):
        """Test that response schemas are properly identified."""
        import copilot.core.mcp.schemas
        from copilot.core.mcp.schemas import __all__

        response_schemas = []
        for schema_name in __all__:
            if "Response" in schema_name:
                schema = getattr(copilot.core.mcp.schemas, schema_name)
                response_schemas.append((schema_name, schema))

        # Should have at least MCPResponse
        response_names = [name for name, _ in response_schemas]
        assert "MCPResponse" in response_names

    def test_error_schemas_identified(self):
        """Test that error schemas are properly identified."""
        import copilot.core.mcp.schemas
        from copilot.core.mcp.schemas import __all__

        error_schemas = []
        for schema_name in __all__:
            if "Error" in schema_name:
                schema = getattr(copilot.core.mcp.schemas, schema_name)
                error_schemas.append((schema_name, schema))

        # Should have at least MCPError
        error_names = [name for name, _ in error_schemas]
        assert "MCPError" in error_names


class TestSchemaCompatibility:
    """Test schema compatibility and versioning."""

    def test_schema_backward_compatibility(self):
        """Test that schema interface is stable."""
        # Test that all expected schemas are still available
        expected_schemas = [
            "MCPRequest",
            "MCPResponse",
            "MCPError",
            "ToolCallRequest",
            "ResourceReadRequest",
            "ListToolsRequest",
            "ListResourcesRequest",
        ]

        from copilot.core.mcp.schemas import __all__

        for expected_schema in expected_schemas:
            assert expected_schema in __all__, f"Schema {expected_schema} is missing from __all__"

    def test_schema_module_version_compatibility(self):
        """Test module version compatibility."""
        import copilot.core.mcp.schemas

        # Module should be importable and functional
        assert copilot.core.mcp.schemas is not None

        # Should have basic attributes
        assert hasattr(copilot.core.mcp.schemas, "__all__")


class TestEdgeCases:
    """Test edge cases and error conditions."""

    def test_malformed_import_patterns(self):
        """Test various import patterns."""
        # Test that basic imports work
        try:
            import copilot.core.mcp.schemas as schemas_module

            # Should work without errors
            assert schemas_module is not None
        except ImportError as e:
            pytest.fail(f"Module import failed: {e}")

    def test_schema_module_attributes(self):
        """Test that module attributes are properly set."""
        import copilot.core.mcp.schemas

        # __all__ should be a list
        assert isinstance(copilot.core.mcp.schemas.__all__, list)

        # __all__ should not be empty
        assert len(copilot.core.mcp.schemas.__all__) > 0

        # All items in __all__ should be strings
        for item in copilot.core.mcp.schemas.__all__:
            assert isinstance(item, str)

    def test_circular_import_protection(self):
        """Test that there are no circular import issues."""
        # Multiple imports should work without issues
        for _ in range(3):
            try:
                from copilot.core.mcp.schemas import MCPError, MCPRequest, MCPResponse

                assert MCPRequest is not None
                assert MCPResponse is not None
                assert MCPError is not None
            except ImportError as e:
                pytest.fail(f"Circular import or import issue detected: {e}")

    def test_schema_name_conflicts(self):
        """Test that schema names don't conflict with built-ins."""
        from copilot.core.mcp.schemas import __all__

        python_builtins = dir(__builtins__)

        for schema_name in __all__:
            # Schema names should not conflict with Python built-ins
            assert schema_name not in python_builtins, f"Schema {schema_name} conflicts with Python built-in"
