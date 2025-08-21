import base64
import logging
import re
from typing import Any, Dict, List, Optional

from copilot.core.tool_wrapper import CopilotTool
from copilot.core.utils.etendo_utils import get_etendo_token
from pydantic import BaseModel, Field, create_model

from .api_tool_util import do_request

logger = logging.getLogger(__name__)

# Constants
ETENDO_HEADLESS_PATH_PREFIX = "/sws/com.etendoerp.etendorx.datasource"
ETENDO_HEADLESS_PAGINATION_PARAMS = {"_startRow": "startRow", "_endRow": "endRow"}


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


def _get_type_mapping():
    """Get the type mapping for OpenAPI types to Python types."""
    return {
        "string": (str, Field(description="")),
        "integer": (int, Field(description="")),
        "number": (float, Field(description="")),
        "boolean": (bool, Field(description="")),
        "array": (list, Field(description="")),
        "object": (dict, Field(description="")),
    }


def _is_etendo_headless_get(method: str, path: str) -> bool:
    """Check if this is an Etendo Headless GET endpoint."""
    return method.lower() == "get" and path.startswith(ETENDO_HEADLESS_PATH_PREFIX)


def _process_etendo_headless_param(original_name: str, param: Dict) -> tuple:
    """Process Etendo Headless pagination parameters."""
    if original_name in ETENDO_HEADLESS_PAGINATION_PARAMS:
        name = ETENDO_HEADLESS_PAGINATION_PARAMS[original_name]
        logger.info(f"Etendo Headless: Converting parameter '{original_name}' to '{name}'")

        # Ensure pagination params are strings and add descriptive text
        param_description = param.get("description", "")
        if name == "startRow":
            param_description = param_description or "Starting row number for pagination (e.g., '0')"
        elif name == "endRow":
            param_description = param_description or "Ending row number for pagination (e.g., '10')"

        return name, "string", param_description
    return None, None, None


def _process_openapi_parameters(
    openapi_params: List[Dict], type_map: Dict, path: str = "", method: str = ""
) -> tuple:
    """Process OpenAPI parameters and return model fields and parameter locations."""
    model_fields = {}
    param_locations = {}
    param_mapping = {}  # Track parameter name mappings for Etendo Headless

    is_etendo_headless = _is_etendo_headless_get(method, path)

    for param in openapi_params:
        original_name = param["name"]
        name = original_name
        param_type_str = param.get("schema", {}).get("type", "string")
        param_description = param.get("description", "")

        # Handle Etendo Headless pagination parameters
        if is_etendo_headless:
            etendo_name, etendo_type, etendo_desc = _process_etendo_headless_param(original_name, param)
            if etendo_name:
                name = etendo_name
                param_type_str = etendo_type
                param_description = etendo_desc
                param_mapping[name] = original_name
            elif original_name.startswith("_"):
                continue  # Skip other underscore parameters for Etendo Headless
        elif name.startswith("_"):
            continue  # Skip parameters that start with underscore (private/internal parameters)

        param_locations[name] = param["in"]

        # Create the Field with the correct parameter description
        field_type, _ = type_map.get(param_type_str, (Any, Field(description="")))
        field_meta = Field(description=param_description)

        if param.get("required", False) or param["in"] == "path":
            model_fields[name] = (field_type, field_meta)
        else:
            model_fields[name] = (Optional[field_type], field_meta)

    return model_fields, param_locations, param_mapping


def _process_request_body(method: str, operation: Dict, path: str, type_map: Dict) -> Optional[tuple]:
    """Process request body for POST/PUT methods and return body model and field info."""
    if method not in ["post", "put"]:
        return None

    request_body = operation.get("requestBody", {})
    content = request_body.get("content", {})
    schema_dict = content.get("application/json", {}).get("schema", {})

    if schema_dict.get("type") != "object" or "properties" not in schema_dict:
        return None

    body_model_name = f"{method.capitalize()}{path.replace('/', '').title()}Body"
    body_model_fields = {}

    for prop_name, prop_data in schema_dict["properties"].items():
        prop_type_str = prop_data.get("type", "string")
        prop_description = prop_data.get("description", "")

        # Create the Field with the correct property description
        field_type, _ = type_map.get(prop_type_str, (Any, Field(description="")))
        field_meta = Field(description=prop_description)

        if prop_name in schema_dict.get("required", []):
            body_model_fields[prop_name] = (field_type, field_meta)
        else:
            body_model_fields[prop_name] = (Optional[field_type], field_meta)

    body_model = create_model(body_model_name, **body_model_fields)
    body_description = request_body.get("description", "Request body for the tool.")

    return body_model, body_description


def _generate_tool_name(operation: Dict, path: str) -> str:
    """Generate a tool name base from operation or path."""
    path_for_name = path
    if ETENDO_HEADLESS_PATH_PREFIX in path:
        path_for_name = path.replace(ETENDO_HEADLESS_PATH_PREFIX, "")

    if "operationId" in operation:
        return operation["operationId"]
    else:
        # Only use the path without the method to avoid duplication
        tool_name_base = path_for_name.replace("/", "_").replace("-", "_").strip("_")
        return tool_name_base if tool_name_base else "api_call"


def _generate_function_code(
    method: str,
    url: str,
    path: str,
    param_names: List[str],
    param_locations: Dict,
    param_mapping: Dict = None,
) -> str:
    """Generate the dynamic function code for the tool."""
    param_names_str = ", ".join(param_names)

    # Handle parameter mapping for Etendo Headless
    if param_mapping:
        path_params_mapping = []
        query_params_mapping = []

        for name, loc in param_locations.items():
            # Check if this parameter needs mapping
            original_name = param_mapping.get(name, name)
            if loc == "path":
                path_params_mapping.append(f"'{original_name}': {name}")
            elif loc == "query":
                query_params_mapping.append(f"'{original_name}': {name}")

        path_params_str = "{" + ", ".join(path_params_mapping) + "}"
        query_params_str = "{" + ", ".join(query_params_mapping) + "}"
    else:
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

    return f"""
def _run_dynamic(self, {param_names_str}):
    body_params = None
    if 'body' in locals():
        body_params = body
        if isinstance(body_params, BaseModel):
            body_params = body_params.model_dump()

    # The token now comes as a function parameter
    auth_token = token if 'token' in locals() and token is not None else None
    #print(f"Using auth_token: {{auth_token}}")
    #if auth_token is 'ETENDO_TOKEN':
    #    auth_token = get_etendo_token()
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


def _create_tool_instance(
    tool_name: str, tool_description: str, tool_args_schema: type, generated_run_func, tool_class
) -> Any:
    """Create a dynamic tool instance."""
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

    return DynamicTool()


def _process_single_operation(
    path: str, method: str, operation: Dict, url: str, type_map: Dict, tool_class
) -> Optional[Any]:
    """Process a single OpenAPI operation and return a tool instance."""
    # Process parameters
    openapi_params = operation.get("parameters", [])
    model_fields, param_locations, param_mapping = _process_openapi_parameters(
        openapi_params, type_map, path, method
    )

    # Process request body for POST/PUT
    body_info = _process_request_body(method, operation, path, type_map)
    if body_info:
        body_model, body_description = body_info
        model_fields["body"] = (body_model, Field(description=body_description))

    # Add token parameter
    model_fields["token"] = (
        Optional[str],
        Field(description="Authentication token for API access", default=None),
    )

    # Generate tool name and schema
    tool_name_base = _generate_tool_name(operation, path)
    tool_args_schema = create_model(f"{tool_name_base}Input", **model_fields)

    # Generate function code
    param_names = list(tool_args_schema.model_fields.keys())
    function_code = _generate_function_code(method, url, path, param_names, param_locations, param_mapping)

    # Execute function code
    namespace = {"do_request": do_request, "BaseModel": BaseModel, "get_etendo_token": get_etendo_token}
    exec(function_code, globals(), namespace)
    generated_run_func = namespace["_run_dynamic"]

    # Create tool
    tool_name_raw = f"{method.upper()}{tool_name_base.title()}"
    tool_name = sanitize_tool_name(tool_name_raw)
    tool_description = operation.get("summary", "No description provided")

    # Log information
    logger.info(f"Generating OpenAPI tool: {tool_name}")
    logger.info(f"  - Method: {method.upper()}")
    logger.info(f"  - Path: {path}")
    logger.info(f"  - Description: {tool_description}")
    logger.info(f"  - Parameters: {param_names}")
    if param_mapping:
        logger.info(f"  - Parameter mapping: {param_mapping}")

    tool_instance = _create_tool_instance(
        tool_name, tool_description, tool_args_schema, generated_run_func, tool_class
    )
    logger.info(f"Successfully created tool: {tool_name}")

    return tool_instance


def generate_tools_from_openapi(openapi_spec: Dict[str, Any]) -> List[Any]:
    """
    Generates a list of tool class instances (e.g., CopilotTool)
    from an OpenAPI specification, with _run signatures that don't use kwargs.

    Args:
        openapi_spec (Dict[str, Any]): The OpenAPI specification as a dictionary.

    Returns:
        List[Any]: A list of tool class instances.
    """
    tools = []
    tool_class = CopilotTool
    type_map = _get_type_mapping()
    url = openapi_spec.get("servers", [{}])[0].get("url", "unknown_url")

    for path, path_item in openapi_spec.get("paths", {}).items():
        for method, operation in path_item.items():
            method = method.lower()
            if method not in ["get", "post", "put", "delete"]:
                continue

            tool_instance = _process_single_operation(path, method, operation, url, type_map, tool_class)
            if tool_instance:
                tools.append(tool_instance)

    logger.info(f"Generated {len(tools)} tools from OpenAPI spec")
    return tools
