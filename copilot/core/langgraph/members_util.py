import functools
import json
from typing import Sequence

from colorama import Fore, Style

from copilot.core import utils
from copilot.core.agent import AssistantAgent
from copilot.core.langgraph.patterns.graph_member import GraphMember
from copilot.core.schemas import AssistantSchema
from copilot.core.utils import copilot_debug, copilot_debug_custom, is_debug_enabled
from langchain.agents import AgentExecutor
from langgraph.prebuilt.chat_agent_executor import create_react_agent
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
import aiohttp

from typing import Optional, Dict, Any, List, Type
from pydantic import BaseModel, create_model, Field
from langchain.tools import BaseTool
import requests

def debug_messages(messages):
    try:
        if not is_debug_enabled():
            return
        if not messages:
            return
        copilot_debug("Messages: ")
        for msg in messages:
            copilot_debug(f"  {type(msg).__name__} - {msg.name if hasattr(msg, 'name') else None} ")
            copilot_debug(f"    Content: {msg.content}")
    except Exception as e:
        copilot_debug(f"Error when trying to debug messages {e}")


class MembersUtil:
    def get_members(self, question) -> list[GraphMember]:
        members = []
        if question.assistants:
            for assistant in question.assistants:
                members.append(self.get_member(assistant))
        return members

    def model_openai_invoker(self):
        def invoke_model_openai(state: List[BaseMessage], _agent: AgentExecutor, _name: str):
            copilot_debug(f"Invoking model OPENAI: {_name} with state: {str(state)}")
            copilot_debug(f"The response is called with: {state['messages'][-1].content}")
            response = _agent.invoke({"content": state["messages"][-1].content})
            response_msg = response["output"]
            copilot_debug(f"Response from OPENAI: {_name} is: {response_msg}")
            return {"messages": [AIMessage(content=response_msg, name=_name)]}

        return invoke_model_openai

    def model_langchain_invoker(self):
        def invoke_model_langchain(state: Sequence[BaseMessage], _agent, _name: str, **kwargs):
            copilot_debug_custom(
                f"Supervisor call {_name} with this instructions:\n {state['instructions']}",
                Fore.MAGENTA + Style.BRIGHT,
            )
            messages = state["messages"]
            messages.append(HumanMessage(content=state["instructions"], name="Supervisor"))
            if _name == "output":
                return {"messages": [AIMessage(content=state["instructions"], name=_name)]}
            response = _agent.invoke({"messages": messages})
            response_msg = response["output"]
            copilot_debug_custom(f"Node {_name} response: \n{response_msg}", Fore.BLUE + Style.BRIGHT)
            return {"messages": [AIMessage(content=response_msg, name=_name)]}

        return invoke_model_langchain

    def get_member(self, assistant: AssistantSchema):
        member = None
        if assistant.type == "openai-assistant":
            agent: AssistantAgent = self.get_assistant_agent()
            _agent = agent.get_agent(assistant.assistant_id)
            agent_executor = agent.get_agent_executor(_agent)
            model_node = functools.partial(
                self.model_openai_invoker(), _agent=agent_executor, _name=assistant.name
            )
            member = GraphMember(assistant.name, model_node)
        else:
            from copilot.core.agent import MultimodelAgent
            configured_tools = MultimodelAgent().get_tools()
            tools = []
            for tool in assistant.tools:
                for t in configured_tools:
                    if t.name == tool.function.name:
                        tools.append(t)
                        break
            if not assistant.specs is None:
                for spec in assistant.specs:
                    if spec.type == "FLOW":
                        api_spec = json.loads(spec.spec)
                        openapi_tools = generate_tools_from_openapi(api_spec)
                        tools += openapi_tools

            from copilot.core.agent.multimodel_agent import get_llm
            llm = get_llm(assistant.model, assistant.provider, assistant.temperature)
            member = create_react_agent(
                model=llm,
                tools=tools,
                name=assistant.name,
                prompt=assistant.system_prompt,
                debug=True
            )

        return member

    def get_assistant_agent(self):
        return AssistantAgent()

    def get_assistant_supervisor_info(self, assistant_name, full_question):
        if full_question is None:
            return None
        for member_info in full_question.assistants:
            if member_info.name == assistant_name:
                return ": " + member_info.description
        return None


def schema_to_pydantic_type(schema: Dict[str, Any]) -> Any:
    if "type" not in schema:
        return Any

    type_map = {
        "string": str,
        "integer": int,
        "number": float,
        "boolean": bool,
    }

    schema_type = schema["type"]
    if schema_type == "array":
        items_schema = schema.get("items", {})
        return List[schema_to_pydantic_type(items_schema)]
    elif schema_type == "object":
        properties = schema.get("properties", {})
        fields = {
            prop: (schema_to_pydantic_type(prop_schema), ...)
            for prop, prop_schema in properties.items()
        }
        return create_model("DynamicModel", **fields)
    else:
        return type_map.get(schema_type, str)

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
        super(BaseTool, self).__init__(name=name, description=description, base_url=base_url, path=path, method=method, parameters=parameters, **kwargs)
        BaseModel.__init__(
            self,
            name=name,
            description=description,
            base_url=base_url.rstrip('/'),
            path=path.lstrip('/'),
            method=method.lower(),
            parameters=parameters or [],
            request_body=request_body,
            **kwargs
        )

        fields = {}
        for param in self.parameters:
            param_name = f"{param['in']}_{param['name']}"
            param_type = schema_to_pydantic_type(param.get("schema", {}))
            fields[param_name] = (
                param_type if param.get("required", False) else Optional[param_type],
                Field(default=None) if not param.get("required", False) else ...
            )

        if self.request_body:
            content = self.request_body.get("content", {}).get("application/json", {})
            body_schema = content.get("schema", {})
            fields["body"] = (schema_to_pydantic_type(body_schema), ...)

        self.args_schema = create_model(f"{name}Args", **fields)

    def _run(self, **kwargs: Any) -> str:
        path_params = {p["name"]: kwargs[f"path_{p['name']}"] for p in self.parameters if p["in"] == "path"}
        query_params = {p["name"]: kwargs[f"query_{p['name']}"] for p in self.parameters if
                        p["in"] == "query" and kwargs.get(f"query_{p['name']}") is not None}
        headers = {p["name"]: kwargs[f"header_{p['name']}"] for p in self.parameters if
                   p["in"] == "header" and kwargs.get(f"header_{p['name']}") is not None}
        from copilot.core import etendo_utils
        token = etendo_utils.get_etendo_token()
        headers["Authorization"] = f"Bearer {token}"
        url = f"{self.base_url}/{self.path}"
        for param_name, param_value in path_params.items():
            url = url.replace(f"{{{param_name}}}", str(param_value))

        data = kwargs.get("body", None)
        if data and isinstance(data, BaseModel):
            data = data.dict()

        try:
            response = requests.request(
                method=self.method,
                url=url,
                params=query_params,
                headers=headers,
                json=data,
            )
            response.raise_for_status()
            return response.text
        except Exception as e:
            return f"Error en la llamada a la API: {str(e)}"

    async def _arun(self, **kwargs: Any) -> str:
        path_params = {p["name"]: kwargs[f"path_{p['name']}"] for p in self.parameters if p["in"] == "path"}
        query_params = {p["name"]: kwargs[f"query_{p['name']}"] for p in self.parameters if
                        p["in"] == "query" and kwargs.get(f"query_{p['name']}") is not None}
        headers = {p["name"]: kwargs[f"header_{p['name']}"] for p in self.parameters if
                   p["in"] == "header" and kwargs.get(f"header_{p['name']}") is not None}
        from copilot.core import etendo_utils
        token = etendo_utils.get_etendo_token()
        headers["Authorization"] = f"Bearer {token}"

        url = f"{self.base_url}/{self.path}"
        for param_name, param_value in path_params.items():
            url = url.replace(f"{{{param_name}}}", str(param_value))

        data = kwargs.get("body", None)
        if data and isinstance(data, BaseModel):
            data = data.dict()

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
                    json=data if data else None,
                ) as response:
                    response.raise_for_status()
                    return await response.text()
            except aiohttp.ClientError as e:
                return f"Error en la llamada a la API (async): {str(e)}"
            except ValueError as e:
                return f"Error de método HTTP: {str(e)}"

def generate_tools_from_openapi(openapi_spec: Dict[str, Any]) -> List[ApiTool]:
    tools = []
    paths = openapi_spec.get("paths", {})
    base_url = utils.read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")

    for path, methods in paths.items():
        for method, operation in methods.items():
            tool_name = f"{method.upper()}_{path.replace('/', '_').replace('.', '_').replace('{','-').replace('}', '-').strip('_')}"
            description = f"\n\n{operation.get('description', '')}"
            if len(description) > 1024:
                description = description[:1021] + "..."
            if len(tool_name) > 64:
                tool_name = tool_name[:60] + "_" + str(len(tools) + 1)

            parameters = operation.get("parameters", [])
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
