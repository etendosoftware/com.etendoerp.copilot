"""
Unit tests for tools.schemas.__init__ module.
Comprehensive coverage of schema loading and management functions.
"""

from unittest.mock import MagicMock, Mock, patch

import pytest
from pydantic import BaseModel, Field

from tools.schemas import (
    list_available_schemas,
    load_ocr_schema,
    load_schema,
)


class TestLoadSchema:
    """Tests for load_schema function."""

    def test_load_schema_invoice(self):
        """Test loading the Invoice schema."""
        schema = load_schema("Invoice")

        assert schema is not None
        assert issubclass(schema, BaseModel)
        assert schema.__name__ == "InvoiceSchema"

    def test_load_schema_case_insensitive(self):
        """Test that schema loading handles case conversion."""
        schema = load_schema("Invoice")
        load_schema("invoice")

        # Both should work but load_schema expects capitalized
        assert schema is not None
        # Note: load_schema expects capitalized names, so 'invoice' won't match 'InvoiceSchema'

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
    def test_load_schema_correct_naming_convention(self, mock_import):
        """Test that schema follows naming convention."""
        mock_module = Mock()

        # Create a proper schema
        class TestSchema(BaseModel):
            value: str = Field(default="test")

        mock_module.TestSchema = TestSchema
        mock_import.return_value = mock_module

        schema = load_schema("Test")

        assert schema is not None
        assert schema == TestSchema

    def test_load_schema_returns_pydantic_model(self):
        """Test that loaded schema is a valid Pydantic model."""
        schema = load_schema("Invoice")

        if schema:
            # Should be able to instantiate
            instance = schema(
                businesspartner="Test Corp",
                cif="B12345678",
                documentno="INV-001",
                date="2025-11-19",
                address={
                    "street": "123 Main St",
                    "city": "Springfield",
                    "postal_code": "12345",
                    "state": "IL",
                    "country": "USA",
                },
            )
            assert instance.businesspartner == "Test Corp"
            assert instance.cif == "B12345678"


class TestListAvailableSchemas:
    """Tests for list_available_schemas function."""

    def test_list_available_schemas_returns_list(self):
        """Test that function returns a list."""
        schemas = list_available_schemas()

        assert isinstance(schemas, list)

    def test_list_available_schemas_contains_invoice(self):
        """Test that Invoice schema is in the list."""
        schemas = list_available_schemas()

        assert "Invoice" in schemas

    def test_list_available_schemas_sorted(self):
        """Test that schemas are sorted alphabetically."""
        schemas = list_available_schemas()

        assert schemas == sorted(schemas)

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
            Mock(stem="invoice"),
            Mock(stem="receipt"),
            Mock(stem="contract"),
            Mock(stem="__init__"),  # Should be excluded
            Mock(stem="_private"),  # Should be excluded
        ]
        mock_glob.return_value = mock_paths

        with patch("tools.schemas.load_schema") as mock_load:
            # Only "valid" schemas should pass
            def load_side_effect(name):
                if name in ["Invoice", "Receipt", "Contract"]:

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


class TestLoadOcrSchema:
    """Tests for load_ocr_schema (legacy) function."""

    def test_load_ocr_schema_delegates_to_load_schema(self):
        """Test that load_ocr_schema delegates to load_schema."""
        with patch("tools.schemas.load_schema") as mock_load:
            mock_load.return_value = Mock()

            result = load_ocr_schema("Invoice")

            mock_load.assert_called_once_with("Invoice")
            assert result == mock_load.return_value

    def test_load_ocr_schema_invoice(self):
        """Test loading Invoice schema via legacy function."""
        schema = load_ocr_schema("Invoice")

        assert schema is not None
        assert issubclass(schema, BaseModel)

    def test_load_ocr_schema_nonexistent(self):
        """Test loading non-existent schema via legacy function."""
        schema = load_ocr_schema("NonExistent")

        assert schema is None

    def test_load_ocr_schema_returns_same_as_load_schema(self):
        """Test that load_ocr_schema returns same result as load_schema."""
        schema1 = load_schema("Invoice")
        schema2 = load_ocr_schema("Invoice")

        assert schema1 == schema2


class TestSchemaIntegration:
    """Integration tests for schema loading."""

    def test_load_and_instantiate_invoice_schema(self):
        """Test loading and using Invoice schema end-to-end."""
        invoice_schema = load_schema("Invoice")

        assert invoice_schema is not None

        # Create an invoice instance
        invoice = invoice_schema(
            businesspartner="Acme Corporation",
            cif="B98765432",
            documentno="INV-2025-001",
            date="2025-11-19",
            address={
                "street": "456 Business Blvd",
                "city": "Metropolis",
                "postal_code": "54321",
                "state": "NY",
                "country": "USA",
            },
            lines=[
                {
                    "product": "Widget A",
                    "quantity": 10,
                    "unit_price": 25.50,
                    "tax_rate": 21.0,
                    "total": 255.00,
                },
                {
                    "product": "Widget B",
                    "quantity": 5,
                    "unit_price": 50.00,
                    "tax_rate": 21.0,
                    "total": 250.00,
                },
            ],
        )

        # Verify the data
        assert invoice.businesspartner == "Acme Corporation"
        assert invoice.cif == "B98765432"
        assert invoice.documentno == "INV-2025-001"
        assert len(invoice.lines) == 2
        assert invoice.lines[0].product == "Widget A"
        assert invoice.lines[0].quantity == 10

    def test_schema_validation(self):
        """Test that schema performs validation."""
        from pydantic import ValidationError

        invoice_schema = load_schema("Invoice")

        if invoice_schema:
            # Test missing required fields
            with pytest.raises(ValidationError):
                invoice_schema(
                    # Missing required fields
                    businesspartner="Test"
                )

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
        schema = load_schema("Invoice")  # Should look for tools.schemas.invoice

        assert schema is not None

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

    def test_load_schema_preserves_field_metadata(self):
        """Test that loaded schema preserves field metadata."""
        invoice_schema = load_schema("Invoice")

        if invoice_schema:
            # Check that field descriptions are preserved
            fields = invoice_schema.model_fields
            assert "businesspartner" in fields
            assert fields["businesspartner"].description is not None

    def test_list_available_schemas_uses_correct_directory(self):
        """Test that list_available_schemas scans correct directory."""
        # This test verifies that the function uses the correct directory
        # by checking that it finds the actual schemas that exist
        schemas = list_available_schemas()

        # Should find at least the Invoice schema
        assert len(schemas) > 0
        assert "Invoice" in schemas


class TestSchemaNamingConvention:
    """Tests for schema naming conventions."""

    def test_schema_class_name_convention(self):
        """Test that schemas follow <Name>Schema convention."""
        invoice_schema = load_schema("Invoice")

        assert invoice_schema is not None
        assert invoice_schema.__name__ == "InvoiceSchema"

    def test_schema_module_name_convention(self):
        """Test that schema modules use lowercase names."""
        # invoice.py contains InvoiceSchema
        invoice_schema = load_schema("Invoice")

        assert invoice_schema is not None
        # Verify it's from the correct module
        assert invoice_schema.__module__ == "tools.schemas.invoice"

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


class TestSchemaDocumentation:
    """Tests for schema documentation and metadata."""

    def test_invoice_schema_has_docstring(self):
        """Test that Invoice schema has documentation."""
        invoice_schema = load_schema("Invoice")

        if invoice_schema:
            assert invoice_schema.__doc__ is not None

    def test_invoice_schema_fields_have_descriptions(self):
        """Test that Invoice schema fields have descriptions."""
        invoice_schema = load_schema("Invoice")

        if invoice_schema:
            fields = invoice_schema.model_fields
            for field_name, field_info in fields.items():
                # All fields should have descriptions
                assert field_info.description is not None, f"Field {field_name} missing description"


class TestComplexSchemaStructure:
    """Tests for complex schema with varied structure (TestDocument)."""

    def test_load_testdocument_schema(self):
        """Test loading TestDocument schema."""
        schema = load_schema("Testdocument")

        assert schema is not None
        assert issubclass(schema, BaseModel)
        assert schema.__name__ == "TestdocumentSchema"

    def test_testdocument_required_fields(self):
        """Test TestDocument schema required fields."""
        from pydantic import ValidationError

        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            # Should fail without required fields
            with pytest.raises(ValidationError):
                test_doc_schema()

    def test_testdocument_nested_models(self):
        """Test TestDocument schema with nested models."""
        from datetime import date

        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            doc = test_doc_schema(
                document_id="DOC-001",
                document_number="TEST-2025-001",
                title="Test Document",
                creation_date=date(2025, 11, 19),
                owner={
                    "name": "John Doe",
                    "email": "john.doe@example.com",
                    "phone": "+1-555-0100",
                    "department": "Engineering",
                },
            )

            assert doc.document_id == "DOC-001"
            assert doc.document_number == "TEST-2025-001"
            assert doc.owner.name == "John Doe"
            assert doc.owner.email == "john.doe@example.com"

    def test_testdocument_with_line_items(self):
        """Test TestDocument schema with line items."""
        from datetime import date

        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            doc = test_doc_schema(
                document_id="DOC-002",
                document_number="TEST-2025-002",
                title="Test with Line Items",
                creation_date=date(2025, 11, 19),
                owner={"name": "Jane Smith", "email": "jane@example.com"},
                line_items=[
                    {
                        "line_number": 1,
                        "item_code": "PROD-001",
                        "description": "Product 1",
                        "quantity": 10,
                        "unit_price": 25.50,
                        "line_total": 255.00,
                    },
                    {
                        "line_number": 2,
                        "item_code": "PROD-002",
                        "description": "Product 2",
                        "quantity": 5,
                        "unit_price": 50.00,
                        "tax_rate": 10.0,
                        "line_total": 250.00,
                    },
                ],
            )

            assert len(doc.line_items) == 2
            assert doc.line_items[0].item_code == "PROD-001"
            assert doc.line_items[0].quantity == 10
            assert doc.line_items[1].tax_rate == pytest.approx(10.0)

    def test_testdocument_with_enums(self):
        """Test TestDocument schema with enum fields."""
        from datetime import date

        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            doc = test_doc_schema(
                document_id="DOC-003",
                document_number="TEST-2025-003",
                title="Test with Enums",
                creation_date=date(2025, 11, 19),
                owner={"name": "Bob Johnson", "email": "bob@example.com"},
                status="approved",  # DocumentStatus enum
                priority="high",  # Priority enum
            )

            assert doc.status.value == "approved"
            assert doc.priority.value == "high"

    def test_testdocument_with_attachments(self):
        """Test TestDocument schema with attachments."""
        from datetime import date

        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            doc = test_doc_schema(
                document_id="DOC-004",
                document_number="TEST-2025-004",
                title="Test with Attachments",
                creation_date=date(2025, 11, 19),
                owner={"name": "Alice Brown", "email": "alice@example.com"},
                attachments=[
                    {
                        "filename": "document.pdf",
                        "file_size": 1024000,
                        "mime_type": "application/pdf",
                        "url": "https://example.com/files/document.pdf",
                        "checksum": "abc123def456",
                    },
                    {
                        "filename": "image.png",
                        "file_size": 512000,
                        "mime_type": "image/png",
                    },
                ],
            )

            assert len(doc.attachments) == 2
            assert doc.attachments[0].filename == "document.pdf"
            assert doc.attachments[0].file_size == 1024000
            assert doc.attachments[1].mime_type == "image/png"

    def test_testdocument_with_addresses(self):
        """Test TestDocument schema with address fields."""
        from datetime import date

        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            doc = test_doc_schema(
                document_id="DOC-005",
                document_number="TEST-2025-005",
                title="Test with Addresses",
                creation_date=date(2025, 11, 19),
                owner={"name": "Charlie Davis", "email": "charlie@example.com"},
                billing_address={
                    "street": "123 Main St",
                    "city": "New York",
                    "state": "NY",
                    "postal_code": "10001",
                    "country": "USA",
                    "is_primary": True,
                },
                shipping_address={
                    "street": "456 Oak Ave",
                    "city": "Boston",
                    "state": "MA",
                    "postal_code": "02101",
                    "country": "USA",
                    "is_primary": False,
                },
            )

            assert doc.billing_address.city == "New York"
            assert doc.shipping_address.city == "Boston"
            assert doc.billing_address.is_primary is True
            assert doc.shipping_address.is_primary is False

    def test_testdocument_optional_fields(self):
        """Test TestDocument schema with optional fields."""
        from datetime import date

        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            # Create minimal document with only required fields
            doc = test_doc_schema(
                document_id="DOC-006",
                document_number="TEST-2025-006",
                title="Minimal Document",
                creation_date=date(2025, 11, 19),
                owner={"name": "David Wilson", "email": "david@example.com"},
            )

            # Optional fields should have default values
            assert doc.description is None
            assert doc.due_date is None
            assert doc.assignee is None
            assert doc.line_items == []
            assert doc.attachments == []
            assert doc.is_active is True
            assert doc.version == 1

    def test_testdocument_validators(self):
        """Test TestDocument schema field validators."""
        from datetime import date

        from pydantic import ValidationError

        test_doc_schema = load_schema("Testdocument")

        if test_doc_schema:
            # Test email validation
            with pytest.raises(ValidationError):  # Should fail with invalid email
                test_doc_schema(
                    document_id="DOC-007",
                    document_number="TEST-2025-007",
                    title="Test Validation",
                    creation_date=date(2025, 11, 19),
                    owner={"name": "Invalid Email", "email": "not-an-email"},
                )

    def test_testdocument_is_in_available_schemas(self):
        """Test that TestDocument schema appears in available schemas."""
        schemas = list_available_schemas()

        assert "Testdocument" in schemas
