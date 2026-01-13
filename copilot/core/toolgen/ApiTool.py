import json
from typing import Any, Dict, List, Optional, Union

import aiohttp
import requests
from copilot.baseutils.logging_envvar import copilot_debug, read_optional_env_var
from copilot.core.toolgen.api_tool_util import (
    is_etendo_headless_path,
    is_etendo_headless_post,
)
from langchain.tools import BaseTool
from pydantic import BaseModel, ConfigDict, Field, create_model


def schema_to_pydantic_type(schema: Dict[str, Any]) -> Any:
    schema_type = read_schema_type(schema)
    if schema_type is None:
        return Any

    type_map = {
        "string": str,
        "integer": int,
        "number": float,
        "boolean": bool,
    }

    if schema_type == "array":
        items_schema = schema.get("items", {})
        if not items_schema:  # If items is not defined or empty, List[Any] is a safe bet
            return List[Any]
        item_type = schema_to_pydantic_type(items_schema)
        return List[item_type]
    elif schema_type == "object":
        properties = schema.get("properties", {})
        if not properties:  # Handle object with no properties

            class EmptyModel(BaseModel):
                pass

            return EmptyModel
        fields = get_fields(properties, schema)

        model_name = schema.get("title", "DynamicModel")
        return create_model(
            model_name, __config__=ConfigDict(extra="allow", arbitrary_types_allowed=True), **fields
        )
    else:
        # Fallback to str if type is unknown and not one of the above complex types
        return type_map.get(schema_type, str)


def get_fields(properties, schema):
    """
    Converts JSON Schema properties to Pydantic fields.
    Args:
        properties: Dict[str, Any]: The properties defined in the JSON Schema.
        schema: Dict[str, Any]: The entire schema definition, used to determine
        required fields and other properties.

    Returns:
        Dict[str, Tuple[Any, Field]]: A dictionary where keys are property names and
        values are tuples of type and Field.
    """
    fields = {}
    required_fields = schema.get("required", [])
    for prop, prop_schema in properties.items():
        prop_type = schema_to_pydantic_type(prop_schema)
        prop_description = prop_schema.get("description", f"Field {prop} of the object")
        field_args = {"description": prop_description}
        if prop_schema.get("type") == "string":
            if "maxLength" in prop_schema:
                field_args["max_length"] = prop_schema["maxLength"]
            if "pattern" in prop_schema:
                field_args["pattern"] = prop_schema["pattern"]

        if prop_schema.get("nullable", False) or prop not in required_fields:
            # Use Optional to handle fields that can be None
            prop_type = Optional[prop_type]

        if prop in required_fields:
            fields[prop] = (prop_type, Field(..., **field_args))
        else:
            fields[prop] = (prop_type, Field(None, **field_args))
    return fields


def read_schema_type(schema):
    if "type" not in schema:
        # If no type is specified, it's safer to default to Any
        # or handle based on other properties like 'properties' or 'items'
        # For instance, if 'properties' exists, it's likely an object.
        if "properties" in schema:
            schema_type = "object"
        elif "items" in schema:
            schema_type = "array"
        else:
            schema_type = None
    else:
        schema_type = schema["type"]
    return schema_type


def summarize(method, url, text):
    simple_mode = read_optional_env_var("COPILOT_SIMPLE_MODE", "false").lower() == "true"
    if (method.upper() in ["POST", "PUT"]) and is_etendo_headless_path(url) and simple_mode:
        try:
            # lest resume the json
            resp_json = json.loads(text)
            res_id = resp_json.get("response").get("data")[0].get("id")
            endpoint_name = url.split("/")[-1]

            msg = f" {endpoint_name} record has been {'created' if method.upper() == 'POST' else 'updated'} successfully with id: {res_id}"
            rsp = {
                "summary": msg,
                "id": res_id,
            }
            return json.dumps({"response": {"data": [rsp]}})
        except Exception as e:
            copilot_debug(f"Response cannot be summarized: {str(e)}")
    return text


def build_payload(kwargs):
    """
    Builds a serialized payload based on the provided data.

    This function extracts the 'body' from kwargs and serializes it depending on its type.
    If the data is a Pydantic BaseModel instance, it uses model_dump to exclude unset values.
    If the data is a list, it processes each item, serializing BaseModel instances.
    Otherwise, it returns the data as is.

    Args:
        kwargs (dict): Dictionary containing the data, including an optional 'body' key representing the payload body.

    Returns:
        Any: The serialized payload, which can be a dictionary, a list, or the original data.
    """
    data = kwargs.get("body", None)
    if isinstance(data, BaseModel):
        payload_serialized = data.model_dump(exclude_unset=True)
    elif isinstance(data, list):
        payload_serialized = []
        for item in data:
            if isinstance(item, BaseModel):
                item_serialized = item.model_dump(exclude_unset=True)
                payload_serialized.append(item_serialized)
            else:
                payload_serialized.append(item)
    else:
        payload_serialized = data
    return payload_serialized


class ApiTool(BaseTool, BaseModel):
    base_url: str
    path: str
    method: str
    parameters: List[Dict[str, Any]]
    request_body: Optional[Dict[str, Any]] = None

    def __init__(
        self,
        name: str,
        description: str,
        base_url: str,
        path: str,
        method: str,
        parameters: List[Dict[str, Any]],
        request_body: Optional[Dict[str, Any]] = None,
        **kwargs,  # For BaseTool/BaseModel compatibility
    ):
        super(BaseTool, self).__init__(
            name=name,
            description=description,
            base_url=base_url,
            path=path,
            method=method,
            parameters=parameters,
            **kwargs,
        )
        BaseModel.__init__(
            self,
            name=name,
            description=description,
            base_url=base_url.rstrip("/"),
            path=path.lstrip("/"),
            method=method.lower(),
            parameters=parameters or [],
            request_body=request_body,
            **kwargs,
        )

        fields = {}
        for param in self.parameters:
            param_name = f"{param['in']}_{param['name']}"
            param_type = schema_to_pydantic_type(param.get("schema", {}))
            param_default = param.get("default")
            fields[param_name] = (
                param_type,
                Field(default=param_default, description=param.get("description", f"Parameter {param_name}")),
            )

        if self.request_body:
            content = self.request_body.get("content", {}).get("application/json", {})
            body_schema = content.get("schema", {})

            body_model = schema_to_pydantic_type(body_schema)
            body_description = self.request_body.get(
                "description", "Request body - can be a single item or a list of items"
            )

            # Enrich description with property info
            if body_schema.get("properties"):
                props_list = []
                for p_name, p_data in body_schema["properties"].items():
                    p_desc = p_data.get("description", "")
                    p_type = p_data.get("type", "any")
                    p_req = " (required)" if p_name in body_schema.get("required", []) else ""
                    props_list.append(f"- {p_name} ({p_type}){p_req}: {p_desc}")
                if props_list:
                    body_description += "\n\nProperties:\n" + "\n".join(props_list)

            # Use Union only for Etendo Headless POST which supports bulk
            if is_etendo_headless_post(self.method, self.path) and body_model is not Any:
                body_type = Union[body_model, List[body_model]]
            else:
                body_type = body_model

            fields["body"] = (body_type, Field(..., description=body_description))

        # Create the args schema with arbitrary_types_allowed to bypass strict validation
        self.args_schema = create_model(
            f"{name}Args", __config__=ConfigDict(arbitrary_types_allowed=True), **fields
        )

    def _run(self, **kwargs: Any) -> str:
        path_params = {p["name"]: kwargs[f"path_{p['name']}"] for p in self.parameters if p["in"] == "path"}
        query_params = self.extract_parameters(kwargs, "query")
        headers = self.extract_parameters(kwargs, "header")
        from copilot.core.utils import etendo_utils
        from copilot.core.utils.etendo_utils import normalize_etendo_token

        token = etendo_utils.get_etendo_token()
        headers["Authorization"] = normalize_etendo_token(token)
        url = f"{self.base_url}/{self.path}"
        for param_name, param_value in path_params.items():
            url = url.replace(f"{{{param_name}}}", str(param_value))

        payload_serialized = build_payload(kwargs)

        try:
            response = requests.request(
                method=self.method,
                url=url,
                params=query_params,
                headers=headers,
                json=payload_serialized,
            )
            response.raise_for_status()
            return summarize(self.method, url, response.text)
        except Exception as e:
            return f"Error en la llamada a la API: {str(e)}"

    def extract_parameters(self, kwargs, type_param):
        return {
            p["name"]: kwargs[f"{type_param}_{p['name']}"]
            for p in self.parameters
            if p["in"] == type_param and kwargs.get(f"{type_param}_{p['name']}") is not None
        }

    async def _arun(self, **kwargs: Any) -> str:
        path_params = {p["name"]: kwargs[f"path_{p['name']}"] for p in self.parameters if p["in"] == "path"}
        query_params = self.extract_parameters(kwargs, "query")
        headers = self.extract_parameters(kwargs, "header")
        from copilot.core.utils import etendo_utils
        from copilot.core.utils.etendo_utils import normalize_etendo_token

        token = etendo_utils.get_etendo_token()
        headers["Authorization"] = normalize_etendo_token(token)

        url = f"{self.base_url}/{self.path}"
        for param_name, param_value in path_params.items():
            url = url.replace(f"{{{param_name}}}", str(param_value))

        payload_serialized = build_payload(kwargs)

        async with aiohttp.ClientSession() as session:
            try:
                # Map method to aiohttp method
                method = self.method.upper()
                if method not in ("GET", "POST", "PUT", "DELETE", "PATCH"):
                    raise ValueError(f"Unsupported HTTP method: {method}")

                # Perform the async request
                async with session.request(
                    method=method,
                    url=url,
                    params=query_params if query_params else None,
                    headers=headers if headers else None,
                    json=payload_serialized if payload_serialized else None,
                ) as response:
                    response.raise_for_status()
                    text = await response.text()
                    return summarize(self.method, url, text)
            except aiohttp.ClientError as e:
                return f"Error en la llamada a la API (async): {str(e)}"
            except ValueError as e:
                return f"Error de método HTTP: {str(e)}"


def generate_tools_from_openapi(openapi_spec: Dict[str, Any]) -> List[ApiTool]:
    tools = []
    paths = openapi_spec.get("paths", {})
    from copilot.core.utils import etendo_utils

    base_url = etendo_utils.get_etendo_host()

    for path, methods in paths.items():
        for method, operation in methods.items():
            tool_name = f"{method.upper()}_{path.replace('/', '_').replace('.', '_').replace('{', '-').replace('}', '-').strip('_')}"
            description = f"\n\n{operation.get('description', '')}"
            if len(description) > 1024:
                description = description[:1021] + "..."
            if len(tool_name) > 64:
                tool_name = tool_name[:60] + "_" + str(len(tools) + 1)
            # remove _sws_com_etendoerp_etendorx_datasource
            tool_name = tool_name.replace("_sws_com_etendoerp_etendorx_datasource", "_etendo")

            parameters = operation.get("parameters", [])
            if path.endswith("/{id}"):
                parameters.append(
                    {
                        "name": "id",
                        "in": "path",
                        "required": True,
                        "description": "ID of the resource",
                        "schema": {"type": "string"},
                    }
                )
            request_body = operation.get("requestBody", None)

            tool = ApiTool(
                name=tool_name,
                description=description,
                base_url=base_url,
                path=path,
                method=method,
                parameters=parameters,
                request_body=request_body,
            )
            tools.append(tool)

    return tools
