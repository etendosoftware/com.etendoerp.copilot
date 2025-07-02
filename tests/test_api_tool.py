from typing import Any, get_args, get_origin

from copilot.core.langgraph.tool_utils.ApiTool import schema_to_pydantic_type
from pydantic import BaseModel


def test_string_type():
    schema = {"type": "string"}
    result = schema_to_pydantic_type(schema)
    assert result == str


def test_integer_type():
    schema = {"type": "integer"}
    result = schema_to_pydantic_type(schema)
    assert result == int


def test_number_type():
    schema = {"type": "number"}
    result = schema_to_pydantic_type(schema)
    assert result == float


def test_boolean_type():
    schema = {"type": "boolean"}
    result = schema_to_pydantic_type(schema)
    assert result == bool


def test_array_of_strings():
    schema = {"type": "array", "items": {"type": "string"}}
    result = schema_to_pydantic_type(schema)
    assert get_origin(result) is list
    assert get_args(result)[0] == str


def test_array_of_any():
    schema = {"type": "array"}
    result = schema_to_pydantic_type(schema)
    assert get_origin(result) is list
    assert get_args(result)[0] == Any


def test_object_with_fields():
    schema = {
        "type": "object",
        "properties": {
            "name": {"type": "string", "description": "The name"},
            "age": {"type": "integer", "description": "The age", "nullable": True},
        },
        "required": ["name"],
    }
    model = schema_to_pydantic_type(schema)
    # Should be a subclass of BaseModel
    assert issubclass(model, BaseModel)
    # Required and optional fields
    fields = model.model_fields
    assert fields["name"].is_required() is True
    assert fields["name"].description == "The name"
    assert str(fields["name"].annotation).endswith("<class 'str'>")
    assert fields["age"].is_required() is False
    assert fields["age"].description == "The age"
    assert str(fields["age"].annotation).endswith("Optional[int]")  # Should be Optional[int] due to nullable


def test_object_empty():
    schema = {"type": "object"}
    model = schema_to_pydantic_type(schema)
    assert issubclass(model, BaseModel)
    assert model.__fields__ == {}


def test_missing_type_with_properties():
    schema = {"properties": {"foo": {"type": "string"}}}
    model = schema_to_pydantic_type(schema)
    assert issubclass(model, BaseModel)
    assert "foo" in model.__fields__


def test_missing_type_with_items():
    schema = {"items": {"type": "integer"}}
    result = schema_to_pydantic_type(schema)
    assert get_origin(result) is list
    assert get_args(result)[0] == int


def test_missing_type_fallback_to_any():
    schema = {"example": 123}
    result = schema_to_pydantic_type(schema)
    assert result == Any


def test_object_with_optional_field():
    schema = {
        "type": "object",
        "properties": {"field1": {"type": "integer"}, "field2": {"type": "string"}},
        "required": ["field1"],
    }
    model = schema_to_pydantic_type(schema)
    f1 = model.model_fields["field1"]
    f2 = model.model_fields["field2"]
    assert f1.is_required() is True
    assert f2.is_required() is False
    # Optional type for not required
    assert f2.annotation._name == "Optional"


def test_array_of_objects():
    schema = {"type": "array", "items": {"type": "object", "properties": {"id": {"type": "integer"}}}}
    result = schema_to_pydantic_type(schema)
    assert get_origin(result) is list
    item_type = get_args(result)[0]
    assert issubclass(item_type, BaseModel)
    assert "id" in item_type.__fields__
