from copilot.core.toolgen.openapi_tool_gen import generate_tools_from_openapi
from pydantic import BaseModel
from typing import Union, get_args, get_origin


def test_openapi_tool_body_schema_regression():
    """
    Test that ensures generated tools from OpenAPI specs have a proper Pydantic model
    defined for the 'body' field in their args_schema, instead of using Any.

    This regression test validates the fix where the body schema was previously
    being set to Any or missing structure.
    """

    # Sample OpenAPI spec provided in the issue description
    openapi_spec = {
        "openapi": "3.0.0",
        "servers": [{"url": "http://localhost:8080"}],
        "paths": {
            "/webhooks/DBQueryExec": {
                "post": {
                    "tags": ["SQL Expert Flow"],
                    "summary": "Retrieves information of Etendo DB and allow to execute SQL queries with automatic security filters",
                    "description": "Use this endpoint to execute SELECT SQL queries in Etendo Classic.",
                    "operationId": "DBQueryExec",
                    "requestBody": {
                        "description": "Request body for request DBQueryExec",
                        "content": {
                            "application/json": {
                                "schema": {
                                    "required": ["Mode"],
                                    "type": "object",
                                    "properties": {
                                        "Mode": {
                                            "type": "string",
                                            "description": "This parameter indicates the mode of the tool...",
                                            "exampleSetFlag": False,
                                        },
                                        "Query": {
                                            "type": "string",
                                            "description": "Used in the mode EXECUTE_QUERY...",
                                            "exampleSetFlag": False,
                                        },
                                        "Table": {
                                            "type": "string",
                                            "description": "Only used in the mode SHOW_COLUMNS...",
                                            "exampleSetFlag": False,
                                        },
                                    },
                                    "exampleSetFlag": False,
                                },
                                "example": "{}",
                                "exampleSetFlag": True,
                            }
                        },
                        "required": True,
                    },
                    "responses": {"200": {"description": "Successful response."}},
                }
            }
        },
    }

    # Generate tools from the spec
    tools = generate_tools_from_openapi(openapi_spec)

    # Verify we got one tool
    assert len(tools) == 1
    tool = tools[0]

    # Verify the tool name matches expected sanitization/generation
    # operationId is DBQueryExec, sanitized -> DBQueryExec (or similar)
    # The tool name generation logic:
    # tool_name_raw = f"{method.upper()}{tool_name_base.title()}"
    # tool_name_base comes from operationId if present.
    # So "POSTDbqueryexec" or "POSTDBQueryExec"?
    # Let's just check the args_schema which is what matters.

    # args_schema should be a Pydantic model
    assert issubclass(tool.args_schema, BaseModel)

    # Check if 'body' field exists in the schema
    assert "body" in tool.args_schema.model_fields, "The 'body' field is missing from the tool's args_schema"

    # Get the field info for 'body'
    body_field = tool.args_schema.model_fields["body"]

    # The annotation should be a BaseModel subclass for standard object schemas
    # (Union types are only generated for oneOf schemas)
    body_annotation = body_field.annotation

    # For a standard object schema (not oneOf), body should be a BaseModel subclass
    assert isinstance(body_annotation, type), "Body annotation should be a type"
    assert issubclass(
        body_annotation, BaseModel
    ), f"Body type {body_annotation} is not a subclass of BaseModel"

    # Introspect the body model to ensure it has the correct properties from the spec
    body_props = body_annotation.model_fields

    assert "Mode" in body_props, "Mode field missing from body schema"
    assert "Query" in body_props, "Query field missing from body schema"
    assert "Table" in body_props, "Table field missing from body schema"

    # Verify strict typing for 'Mode' (required in spec)
    # Depending on pydantic version and how it's defined:
    mode_field = body_props["Mode"]

    # In the generation logic:
    # if is_required and not is_nullable:
    #    field_type ...
    # else:
    #    Optional[field_type]

    # 'Mode' is in required list.
    assert mode_field.annotation == str, f"Mode field should be str, got {mode_field.annotation}"

    # 'Query' is NOT in required list.
    query_field = body_props["Query"]
    # It should be Optional[str]
    # In Pydantic inspection, Optional[str] is usually Union[str, NoneType] or similar.
    # We can check if it allows None or check annotation.
    # For simplicity, just checking it's not strictly 'str' might be enough, or check the origin.
    # But let's look at the implementation:
    # field_meta = Field(description=prop_description, default=None)
    # body_model_fields[prop_name] = (Optional[field_type], field_meta)

    # So we expect Optional[str]
    assert query_field.annotation != str, "Query field should be Optional, but appears to be required str"
    # We could be more specific but this differentiates from the required field.
