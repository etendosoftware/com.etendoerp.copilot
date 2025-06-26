import json
from typing import Any, Dict, List, Optional, Union

import aiohttp
import requests
from copilot.core import etendo_utils, utils
from copilot.core.utils import copilot_debug
from langchain.tools import BaseTool
from pydantic import BaseModel, Field, create_model


def schema_to_pydantic_type(schema: Dict[str, Any]) -> Any:
    if "type" not in schema:
        # If no type is specified, it's safer to default to Any
        # or handle based on other properties like 'properties' or 'items'
        # For instance, if 'properties' exists, it's likely an object.
        if "properties" in schema:
            schema_type = "object"
        elif "items" in schema:
            schema_type = "array"
        else:
            return Any # Fallback if type cannot be inferred
    else:
        schema_type = schema["type"]

    type_map = {
        "string": str,
        "integer": int,
        "number": float,
        "boolean": bool,
    }

    if schema_type == "array":
        items_schema = schema.get("items", {})
        if not items_schema: # If items is not defined or empty, List[Any] is a safe bet
            return List[Any]
        item_type = schema_to_pydantic_type(items_schema)
        return List[item_type]
    elif schema_type == "object":
        properties = schema.get("properties", {})
        if not properties: # Handle object with no properties
            class EmptyModel(BaseModel):
                pass
            return EmptyModel
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

        model_name = schema.get("title", "DynamicModel")
        return create_model(model_name, **fields)
    else:
        # Fallback to str if type is unknown and not one of the above complex types
        return type_map.get(schema_type, str)


def summarize(method, url, text):
    simple_mode = utils.read_optional_env_var("COPILOT_SIMPLE_MODE", "false").lower() == "true"
    if (method.upper() in ["POST", "PUT"]) and "com.etendoerp.etendorx.datasource" in url and simple_mode:
        try:
            # lest resume the json
            resp_json = json.loads(text)
            res_id = resp_json.get("response").get("data")[0].get("id")
            endpoint_name = url.split("/")[-1]

            msg = f" {endpoint_name} record has been {'created' if method.upper() == 'POST' else 'updated'} successfully with id: {id}"
            rsp = {
                "summary": msg,
                "id": res_id,
            }
            return json.dumps({"response": {"data": [rsp]}})
        except Exception as e:
            copilot_debug(f"Response cannot be summarized: {str(e)}")
    return text


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

            item_schema_type = schema_to_pydantic_type(body_schema)

            fields["body"] = (Union[item_schema_type, List[item_schema_type]], ...)
            self.args_schema = create_model(f"{name}Args", **fields)

        self.args_schema = create_model(f"{name}Args", **fields)

    def _run(self, **kwargs: Any) -> str:
        path_params = {p["name"]: kwargs[f"path_{p['name']}"] for p in self.parameters if p["in"] == "path"}
        query_params = {
            p["name"]: kwargs[f"query_{p['name']}"]
            for p in self.parameters
            if p["in"] == "query" and kwargs.get(f"query_{p['name']}") is not None
        }
        headers = {
            p["name"]: kwargs[f"header_{p['name']}"]
            for p in self.parameters
            if p["in"] == "header" and kwargs.get(f"header_{p['name']}") is not None
        }
        from copilot.core import etendo_utils

        token = etendo_utils.get_etendo_token()
        headers["Authorization"] = f"Bearer {token}"
        url = f"{self.base_url}/{self.path}"
        for param_name, param_value in path_params.items():
            url = url.replace(f"{{{param_name}}}", str(param_value))

        data = kwargs.get("body", None)
        if isinstance(data, BaseModel):
            payload_serialized = data.model_dump()
        elif isinstance(data, list):
            # Si el payload es una lista
            payload_serialized = []
            for item in data:
                if isinstance(item, BaseModel):
                    payload_serialized.append(item.model_dump())
                else:
                    # Si el item en la lista no es un modelo Pydantic, se asume que ya es serializable
                    payload_serialized.append(item)
        else:
            # Si el payload no es ni un modelo Pydantic ni una lista (ej. ya es un dict), se usa como está
            payload_serialized = data

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

    async def _arun(self, **kwargs: Any) -> str:
        path_params = {p["name"]: kwargs[f"path_{p['name']}"] for p in self.parameters if p["in"] == "path"}
        query_params = {
            p["name"]: kwargs[f"query_{p['name']}"]
            for p in self.parameters
            if p["in"] == "query" and kwargs.get(f"query_{p['name']}") is not None
        }
        headers = {
            p["name"]: kwargs[f"header_{p['name']}"]
            for p in self.parameters
            if p["in"] == "header" and kwargs.get(f"header_{p['name']}") is not None
        }
        from copilot.core import etendo_utils

        token = etendo_utils.get_etendo_token()
        headers["Authorization"] = f"Bearer {token}"

        url = f"{self.base_url}/{self.path}"
        for param_name, param_value in path_params.items():
            url = url.replace(f"{{{param_name}}}", str(param_value))

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
    base_url = etendo_utils.get_etendo_host()

    for path, methods in paths.items():
        for method, operation in methods.items():
            tool_name = f"{method.upper()}_{path.replace('/', '_').replace('.', '_').replace('{', '-').replace('}', '-').strip('_')}"
            description = f"\n\n{operation.get('description', '')}"
            if len(description) > 1024:
                description = description[:1021] + "..."
            if len(tool_name) > 64:
                tool_name = tool_name[:60] + "_" + str(len(tools) + 1)

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
