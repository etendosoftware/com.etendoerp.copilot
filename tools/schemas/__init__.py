"""
Tool Schemas Package

This package contains Pydantic schemas used across the copilot tools.
Schemas can be used for:
- Tool input validation
- Structured output formats (e.g., for OCR extraction)
- Data transfer objects between tools

Structure:
- Each schema file defines one or more related Pydantic BaseModel classes
- Schemas are organized by domain/purpose (e.g., invoice, receipt, contract)
- The loader utility provides dynamic schema loading for extensibility
"""

import importlib
import inspect
from pathlib import Path
from typing import Optional, Type

from pydantic import BaseModel


def load_schema(schema_name: str) -> Optional[Type[BaseModel]]:
    """
    Dynamically loads a schema by name.

    This function allows for extensible schema loading without modifying tool code.
    Schemas can be added by simply creating new files in the schemas directory.

    Args:
        schema_name: Name of the schema (e.g., 'Invoice', 'Receipt', 'Contract')

    Returns:
        Pydantic BaseModel class if found, None otherwise

    Example:
        >>> schema = load_schema('Invoice')
        >>> if schema:
        ...     # Use schema for validation or structured output
        ...     data = schema(client="Acme Corp", date="2025-11-13")

    Notes:
        - Converts schema name to module name (e.g., 'Invoice' -> 'invoice')
        - Expects class name to match: '<SchemaName>Schema'
        - Example: invoice.py should contain InvoiceSchema class
    """
    if not schema_name:
        return None

    # Convert schema name to module name (e.g., 'Invoice' -> 'invoice')
    module_name = schema_name.lower()
    class_name = f"{schema_name}Schema"

    try:
        # Try to import the schema module
        module = importlib.import_module(f"tools.schemas.{module_name}")

        # Get the class from the module
        if hasattr(module, class_name):
            schema_class = getattr(module, class_name)

            # Verify it's a Pydantic BaseModel
            if inspect.isclass(schema_class) and issubclass(schema_class, BaseModel):
                return schema_class

        return None

    except (ImportError, AttributeError):
        # Schema not found
        return None


def list_available_schemas() -> list[str]:
    """
    Lists all available schemas in the schemas directory.

    Returns:
        List of schema names (e.g., ['Invoice', 'Receipt', 'Contract'])

    Notes:
        - Scans the schemas directory for Python files
        - Verifies each schema can be loaded successfully
        - Returns capitalized schema names for use with load_schema()
    """
    schemas = []
    schema_dir = Path(__file__).parent

    for file_path in schema_dir.glob("*.py"):
        # Skip __init__.py and other special files
        if file_path.stem.startswith("_"):
            continue

        # Extract schema name from filename
        # e.g., 'invoice.py' -> 'Invoice'
        schema_name = file_path.stem.capitalize()

        # Verify the schema can be loaded
        if load_schema(schema_name):
            schemas.append(schema_name)

    return sorted(schemas)


# Legacy compatibility: support for ocr_schemas
def load_ocr_schema(schema_name: str) -> Optional[Type[BaseModel]]:
    """
    Legacy function for OCR schema loading.

    This function provides backward compatibility for code that expects
    OCR-specific schema loading. It now simply delegates to load_schema().

    Args:
        schema_name: Name of the OCR schema (e.g., 'Invoice')

    Returns:
        Pydantic BaseModel class if found, None otherwise

    Deprecated:
        Use load_schema() directly instead.
    """
    return load_schema(schema_name)
