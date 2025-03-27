import functools
import json
import re
from typing import List, Sequence

from colorama import Fore, Style
from copilot.core.agent import AssistantAgent
from copilot.core.agent.agent import (
    get_kb_tool,
)
from copilot.core.langgraph.tool_utils.ApiTool import generate_tools_from_openapi
from copilot.core.schema.graph_member import GraphMember
from copilot.core.schemas import AssistantSchema
from copilot.core.utils import copilot_debug, copilot_debug_custom, is_debug_enabled
from langchain.agents import AgentExecutor
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from langgraph.prebuilt.chat_agent_executor import create_react_agent


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


tools_specs = []


def codify_name(name):
    # need to have the format  '^[a-zA-Z0-9_-]+$'.
    name_field = name
    # Replace all non-alphanumeric characters with underscores.
    # Step 1: Strip any whitespace characters from the beginning and end of the string
    name_field = name_field.strip()

    # Step 2: Replace white spaces within the string with underscores
    name_field = re.sub(r"\s+", "", name_field)

    # Step 3: Remove any characters that do not match the [a-zA-Z0-9_-] pattern
    name_field = re.sub(r"[^a-zA-Z0-9_-]", "", name_field)

    # Step 4: Truncate to 63 characters
    name_field = name_field[:63]

    # Step 5: Validate against the regex pattern
    if not re.match(r"^[a-zA-Z0-9_-]{1,63}$", name_field):
        raise ValueError("Invalid characters in name field")

    return name_field


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

            multimodel_agent = MultimodelAgent()
            configured_tools = multimodel_agent.get_tools()
            tools = []

            for tool in assistant.tools if assistant.tools is not None else []:
                for t in configured_tools:
                    if t.name == tool.function.name:
                        tools.append(t)
                        break
            kb_tool = get_kb_tool(assistant.kb_vectordb_id)  # type: ignore
            if kb_tool is not None:
                tools.append(kb_tool)
            if assistant.specs is not None:
                for spec in assistant.specs:
                    if spec.type == "FLOW":
                        api_spec = json.loads(spec.spec)
                        openapi_tools = generate_tools_from_openapi(api_spec)
                        tools_specs.extend(openapi_tools)

            from copilot.core.agent.multimodel_agent import get_llm

            llm = get_llm(assistant.model, assistant.provider, assistant.temperature)

            member = create_react_agent(
                model=llm,
                tools=tools,
                name=codify_name(assistant.name),
                prompt=assistant.system_prompt,
                # , debug=True
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
