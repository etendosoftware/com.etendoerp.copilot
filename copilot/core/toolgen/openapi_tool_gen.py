import base64
import logging
import re
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field, create_model

from ..tool_wrapper import CopilotTool
from .api_tool_util import do_request

logger = logging.getLogger(__name__)


def replace_base64_filepaths(body_params):
    if isinstance(body_params, str):
        while "@BASE64_" in body_params:
            start = body_params.find("@BASE64_")
            end = body_params.find("@", start + 1)
            filepath = body_params[start + 8 : end]
            with open(filepath, "rb") as file:
                file_content = file.read()
                file_content = base64.b64encode(file_content).decode("utf-8")
                body_params = body_params.replace(f"@BASE64_{filepath}@", file_content)

    return body_params


def sanitize_tool_name(name: str) -> str:
    """
    Sanitizes the tool name to comply with OpenAI pattern: ^[a-zA-Z0-9_-]+$

    Args:
        name: The original tool name

    Returns:
        The sanitized name that complies with the required pattern
    """
    # Replace invalid characters with underscores
    sanitized = re.sub(r"[^a-zA-Z0-9_-]", "_", name)

    # Remove multiple consecutive underscores
    sanitized = re.sub(r"_+", "_", sanitized)

    # Remove underscores at the beginning and end
    sanitized = sanitized.strip("_")

    # Ensure it's not empty
    if not sanitized:
        sanitized = "generated_tool"

    logger.info(f"Sanitized tool name: '{name}' -> '{sanitized}'")
    return sanitized


def generate_tools_from_openapi(openapi_spec: Dict[str, Any]) -> List[Any]:
    """
    Generates a list of tool class instances (e.g., CopilotTool)
    from an OpenAPI specification, with _run signatures that don't use kwargs.

    Args:
        openapi_spec (Dict[str, Any]): The OpenAPI specification as a dictionary.
        tool_class (Type[Any]): The base tool class to instantiate.

    Returns:
        List[Any]: A list of tool class instances.
    """
    tools = []
    tool_class = CopilotTool
    TYPE_MAP = {
        "string": (str, Field(description="")),
        "integer": (int, Field(description="")),
        "number": (float, Field(description="")),
        "boolean": (bool, Field(description="")),
        "array": (list, Field(description="")),
        "object": (dict, Field(description="")),
    }
    url = openapi_spec.get("servers", [{}])[0].get("url", "unknown_url")

    for path, path_item in openapi_spec.get("paths", {}).items():
        for method, operation in path_item.items():
            method = method.lower()
            if method not in ["get", "post", "put", "delete"]:
                continue

            model_fields = {}
            param_locations = {}

            openapi_params = operation.get("parameters", [])

            for param in openapi_params:
                name = param["name"]
                if "_" in name:
                    continue  # Skip parameters with underscores in their names
                param_type_str = param.get("schema", {}).get("type", "string")
                param_description = param.get("description", "")
                param_locations[name] = param["in"]

                # Create the Field with the correct parameter description
                field_type, _ = TYPE_MAP.get(param_type_str, (Any, Field(description="")))
                field_meta = Field(description=param_description)

                if param.get("required", False) or param["in"] == "path":
                    model_fields[name] = (field_type, field_meta)
                else:
                    model_fields[name] = (Optional[field_type], field_meta)

            if method in ["post", "put"]:
                request_body = operation.get("requestBody", {})
                content = request_body.get("content", {})
                schema_dict = content.get("application/json", {}).get("schema", {})

                if schema_dict.get("type") == "object" and "properties" in schema_dict:
                    body_model_name = f"{method.capitalize()}{path.replace('/', '').title()}Body"
                    body_model_fields = {}

                    for prop_name, prop_data in schema_dict["properties"].items():
                        prop_type_str = prop_data.get("type", "string")
                        prop_description = prop_data.get("description", "")

                        # Create the Field with the correct property description
                        field_type, _ = TYPE_MAP.get(prop_type_str, (Any, Field(description="")))
                        field_meta = Field(description=prop_description)

                        if prop_name in schema_dict.get("required", []):
                            body_model_fields[prop_name] = (field_type, field_meta)
                        else:
                            body_model_fields[prop_name] = (Optional[field_type], field_meta)

                    body_model = create_model(body_model_name, **body_model_fields)
                    body_description = request_body.get("description", "Request body for the tool.")
                    model_fields["body"] = (body_model, Field(description=body_description))

            # Add token as an optional parameter for authentication
            model_fields["token"] = (
                Optional[str],
                Field(description="Authentication token for API access", default=None),
            )

            path_for_name = path
            if "/sws/com.etendoerp.etendorx.datasource" in path:
                path_for_name = path.replace("/sws/com.etendoerp.etendorx.datasource", "")

            # Generate tool_name_base without including the method if there's no operationId
            if "operationId" in operation:
                tool_name_base = operation["operationId"]
            else:
                # Only use the path without the method to avoid duplication
                tool_name_base = path_for_name.replace("/", "_").replace("-", "_").strip("_")
                if not tool_name_base:
                    tool_name_base = "api_call"

            tool_args_schema = create_model(f"{tool_name_base}Input", **model_fields)

            param_names = list(tool_args_schema.model_fields.keys())
            param_names_str = ", ".join(param_names)

            path_params_str = (
                "{"
                + ", ".join([f"'{name}': {name}" for name, loc in param_locations.items() if loc == "path"])
                + "}"
            )
            query_params_str = (
                "{"
                + ", ".join([f"'{name}': {name}" for name, loc in param_locations.items() if loc == "query"])
                + "}"
            )

            function_code = f"""
def _run_dynamic(self, {param_names_str}):
    body_params = None
    if 'body' in locals():
        body_params = body
        if isinstance(body_params, BaseModel):
            body_params = body_params.model_dump()

    # The token now comes as a function parameter
    auth_token = token if 'token' in locals() and token is not None else None

    path_params = {path_params_str}
    query_params = {query_params_str}

    query_params = {{k: v for k, v in query_params.items() if v is not None}}

    response = do_request(
        method='{method.upper()}',
        url='{url}',
        endpoint='{path}',
        body_params=body_params,
        path_params=path_params,
        query_params=query_params,
        token=auth_token
    )

    return response
"""

            namespace = {"do_request": do_request, "BaseModel": BaseModel}
            exec(function_code, globals(), namespace)

            generated_run_func = namespace["_run_dynamic"]

            # Generate and sanitize the tool name
            tool_name_raw = f"{method.upper()}{tool_name_base.title()}"
            tool_name = sanitize_tool_name(tool_name_raw)
            tool_description = operation.get("summary", "No description provided")

            # Log information about the generated tool
            logger.info(f"Generating OpenAPI tool: {tool_name}")
            logger.info(f"  - Method: {method.upper()}")
            logger.info(f"  - Path: {path}")
            logger.info(f"  - Description: {tool_description}")
            logger.info(f"  - Parameters: {param_names}")

            DynamicTool = type(
                tool_name,
                (tool_class,),
                {
                    "__module__": __name__,
                    "__annotations__": {
                        "name": str,
                        "description": str,
                        "args_schema": type,
                    },
                    "name": tool_name,
                    "description": tool_description,
                    "args_schema": tool_args_schema,
                    "_run": generated_run_func,
                },
            )

            tools.append(DynamicTool())
            logger.info(f"Successfully created tool: {tool_name}")

    logger.info(f"Generated {len(tools)} tools from OpenAPI spec")
    return tools
