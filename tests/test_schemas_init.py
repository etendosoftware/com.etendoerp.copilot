"""
Unit tests for tools.schemas.__init__ module.
Tests the schema loading and management functions without using Invoice schema.
Uses mocks and TestDocument schema for coverage.
"""

from unittest.mock import MagicMock, Mock, patch

from pydantic import BaseModel, Field

from tools.schemas import (
    list_available_schemas,
    load_ocr_schema,
    load_schema,
)


class TestLoadSchemaWithMocks:
    """Tests for load_schema function using mocks."""

    def test_load_schema_nonexistent(self):
        """Test loading a non-existent schema."""
        schema = load_schema("NonExistentSchema")

        assert schema is None

    def test_load_schema_empty_name(self):
        """Test loading schema with empty name."""
        schema = load_schema("")

        assert schema is None

    def test_load_schema_none_name(self):
        """Test loading schema with None name."""
        schema = load_schema(None)

        assert schema is None

    @patch("tools.schemas.importlib.import_module")
    def test_load_schema_import_error(self, mock_import):
        """Test handling of import errors."""
        mock_import.side_effect = ImportError("Module not found")

        schema = load_schema("FailingSchema")

        assert schema is None

    @patch("tools.schemas.importlib.import_module")
    def test_load_schema_attribute_error(self, mock_import):
        """Test handling of attribute errors."""
        mock_module = MagicMock()
        mock_import.return_value = mock_module
        # Simulate the module not having the expected schema class
        del mock_module.MissingClassSchema

        schema = load_schema("MissingClass")

        assert schema is None

    @patch("tools.schemas.importlib.import_module")
    def test_load_schema_not_basemodel(self, mock_import):
        """Test rejection of non-BaseModel classes."""
        mock_module = Mock()

        # Create a regular class (not BaseModel)
        class NotASchema:
            pass

        mock_module.NotASchemaSchema = NotASchema
        mock_import.return_value = mock_module

        schema = load_schema("NotA")

        assert schema is None

    @patch("tools.schemas.importlib.import_module")
    def test_load_schema_converts_name_to_lowercase(self, mock_import):
        """Test that schema name is converted to lowercase for module."""
        mock_module = Mock()

        class TestSchema(BaseModel):
            pass

        mock_module.TestSchema = TestSchema
        mock_import.return_value = mock_module

        load_schema("Test")

        # Should import tools.schemas.test (lowercase)
        mock_import.assert_called_once_with("tools.schemas.test")

    @patch("tools.schemas.importlib.import_module")
    def test_multiple_schemas_independent(self, mock_import):
        """Test that loading different schemas are independent operations."""
        call_count = 0

        def import_side_effect(module_name):
            nonlocal call_count
            call_count += 1

            mock_module = Mock()
            schema_name = module_name.split(".")[-1].capitalize()

            class DynamicSchema(BaseModel):
                name: str = Field(default=f"Schema{call_count}")

            setattr(mock_module, f"{schema_name}Schema", DynamicSchema)
            return mock_module

        mock_import.side_effect = import_side_effect

        schema1 = load_schema("First")
        schema2 = load_schema("Second")

        assert schema1 != schema2
        assert call_count == 2

    @patch("tools.schemas.importlib.import_module")
    def test_load_schema_module_has_multiple_classes(self, mock_import):
        """Test loading from module with multiple classes."""
        mock_module = Mock()

        class CorrectSchema(BaseModel):
            value: str = Field(default="correct")

        class OtherClass(BaseModel):
            value: str = Field(default="other")

        # Module has multiple classes but only CorrectSchema follows convention
        mock_module.CorrectSchema = CorrectSchema
        mock_module.OtherClass = OtherClass
        mock_import.return_value = mock_module

        schema = load_schema("Correct")

        assert schema == CorrectSchema


class TestListAvailableSchemasWithMocks:
    """Tests for list_available_schemas function using mocks."""

    def test_list_available_schemas_returns_list(self):
        """Test that function returns a list."""
        schemas = list_available_schemas()

        assert isinstance(schemas, list)

    def test_list_available_schemas_excludes_init(self):
        """Test that __init__.py is not in the list."""
        schemas = list_available_schemas()

        assert "__init__" not in schemas
        assert "_" not in [s[0] for s in schemas if s]  # No schema starts with _

    @patch("tools.schemas.Path.glob")
    def test_list_available_schemas_with_multiple_schemas(self, mock_glob):
        """Test listing multiple schemas."""
        # Create mock paths
        mock_paths = [
            Mock(stem="testdocument"),
            Mock(stem="receipt"),
            Mock(stem="contract"),
            Mock(stem="__init__"),  # Should be excluded
            Mock(stem="_private"),  # Should be excluded
        ]
        mock_glob.return_value = mock_paths

        with patch("tools.schemas.load_schema") as mock_load:
            # Only "valid" schemas should pass
            def load_side_effect(name):
                if name in ["Testdocument", "Receipt", "Contract"]:

                    class DummySchema(BaseModel):
                        pass

                    return DummySchema
                return None

            mock_load.side_effect = load_side_effect

            schemas = list_available_schemas()

            # Should exclude __init__ and _private
            assert "__init__" not in schemas
            assert "_private" not in schemas

    @patch("tools.schemas.Path.glob")
    def test_list_available_schemas_empty_directory(self, mock_glob):
        """Test with empty schemas directory."""
        mock_glob.return_value = []

        schemas = list_available_schemas()

        assert schemas == []

    @patch("tools.schemas.Path.glob")
    @patch("tools.schemas.load_schema")
    def test_list_available_schemas_filters_invalid(self, mock_load, mock_glob):
        """Test that invalid schemas are filtered out."""
        mock_paths = [
            Mock(stem="valid"),
            Mock(stem="invalid"),
        ]
        mock_glob.return_value = mock_paths

        # Only "Valid" loads successfully
        def load_side_effect(name):
            if name == "Valid":

                class ValidSchema(BaseModel):
                    pass

                return ValidSchema
            return None

        mock_load.side_effect = load_side_effect

        schemas = list_available_schemas()

        assert "Valid" in schemas
        assert "Invalid" not in schemas

    def test_list_available_schemas_uses_correct_directory(self):
        """Test that list_available_schemas scans correct directory."""
        # This test verifies that the function uses the correct directory
        # by checking that it finds the actual schemas that exist
        schemas = list_available_schemas()

        # Should find at least some schemas
        assert len(schemas) > 0


class TestLoadOcrSchemaLegacy:
    """Tests for load_ocr_schema (legacy) function."""

    def test_load_ocr_schema_delegates_to_load_schema(self):
        """Test that load_ocr_schema delegates to load_schema."""
        with patch("tools.schemas.load_schema") as mock_load:
            mock_load.return_value = Mock()

            result = load_ocr_schema("TestSchema")

            mock_load.assert_called_once_with("TestSchema")
            assert result == mock_load.return_value

    def test_load_ocr_schema_nonexistent(self):
        """Test loading non-existent schema via legacy function."""
        schema = load_ocr_schema("NonExistent")

        assert schema is None

    def test_load_ocr_schema_returns_same_as_load_schema(self):
        """Test that load_ocr_schema returns same result as load_schema."""
        schema1 = load_schema("Testdocument")
        schema2 = load_ocr_schema("Testdocument")

        assert schema1 == schema2


class TestSchemaEdgeCases:
    """Tests for edge cases in schema loading."""

    def test_load_schema_with_special_characters(self):
        """Test schema name with special characters."""
        schema = load_schema("Test-Schema")

        # Should handle gracefully (likely return None)
        assert schema is None or issubclass(schema, BaseModel)

    def test_load_schema_with_numbers(self):
        """Test schema name with numbers."""
        schema = load_schema("Schema123")

        # Should handle gracefully
        assert schema is None or issubclass(schema, BaseModel)


class TestSchemaNamingConvention:
    """Tests for schema naming conventions."""

    def test_schema_class_name_convention_testdocument(self):
        """Test that TestDocument schema follows <Name>Schema convention."""
        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            assert test_doc_schema.__name__ == "TestdocumentSchema"

    def test_schema_module_name_convention_testdocument(self):
        """Test that schema modules use lowercase names."""
        # testdocument.py contains TestdocumentSchema
        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            # Verify it's from the correct module
            assert test_doc_schema.__module__ == "tools.schemas.testdocument"


class TestSchemaIntegrationWithTestDocument:
    """Integration tests for schema loading using TestDocument."""

    def test_load_testdocument_schema(self):
        """Test loading TestDocument schema."""
        schema = load_schema("Testdocument")

        assert schema is not None
        assert issubclass(schema, BaseModel)
        assert schema.__name__ == "TestdocumentSchema"

    def test_testdocument_is_in_available_schemas(self):
        """Test that TestDocument schema appears in available schemas."""
        schemas = list_available_schemas()

        assert "Testdocument" in schemas

    def test_all_listed_schemas_are_loadable(self):
        """Test that all listed schemas can be loaded."""
        schemas = list_available_schemas()

        for schema_name in schemas:
            schema = load_schema(schema_name)
            assert schema is not None, f"Schema {schema_name} should be loadable"
            assert issubclass(schema, BaseModel), f"Schema {schema_name} should be BaseModel"

    def test_schema_module_path_resolution(self):
        """Test that module paths are resolved correctly."""
        # This tests the internal module name conversion
        schema = load_schema("Testdocument")  # Should look for tools.schemas.testdocument

        assert schema is not None


class TestSchemaDocumentation:
    """Tests for schema documentation and metadata."""

    def test_testdocument_schema_has_docstring(self):
        """Test that TestDocument schema has documentation."""
        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            assert test_doc_schema.__doc__ is not None

    def test_testdocument_schema_fields_have_descriptions(self):
        """Test that TestDocument schema fields have descriptions."""
        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            fields = test_doc_schema.model_fields
            for field_name, field_info in fields.items():
                # All fields should have descriptions
                assert field_info.description is not None, f"Field {field_name} missing description"
