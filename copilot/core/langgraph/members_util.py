import functools
from typing import List, Sequence

from langchain.agents import AgentExecutor
from langchain_core.messages import BaseMessage, AIMessage
from langsmith import traceable

from copilot.core.agent import LangchainAgent, AssistantAgent
from copilot.core.langgraph.patterns.base_pattern import GraphMember
from copilot.core.schemas import AssistantSchema
from copilot.core.utils import copilot_debug, is_debug_enabled


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
            copilot_debug(f"Invoking model LANGCHAIN: {_name} with state: ")
            messages = state["messages"]
            messages.append(AIMessage(content=state["instructions"], name="Supervisor"))
            debug_messages(messages)
            response = _agent.invoke({"messages": messages})
            response_msg = response["output"]
            copilot_debug(f"Response from LANGCHAIN: {_name} is: {response_msg}")
            return {"messages": [AIMessage(content=response_msg, name=_name)]}

        return invoke_model_langchain

    def get_member(self, assistant: AssistantSchema):
        member = None
        if assistant.type == "openai-assistant":
            agent: AssistantAgent = self.get_assistant_agent()
            _agent = agent.get_agent(assistant.assistant_id)
            agent_executor = agent.get_agent_executor(_agent)
            model_node = functools.partial(self.model_openai_invoker(), _agent=agent_executor, _name=assistant.name)
            member = GraphMember(assistant.name, model_node)
        else:
            langchain_agent = LangchainAgent()
            kb_vectordb_id = assistant.kb_vectordb_id if hasattr(assistant, "kb_vectordb_id") else None
            _agent = langchain_agent.get_agent(assistant.provider, assistant.model, assistant.tools,
                                               assistant.system_prompt, assistant.temperature, kb_vectordb_id)
            agent_executor = langchain_agent.get_agent_executor(_agent)
            model_node = functools.partial(self.model_langchain_invoker(), _agent=agent_executor, _name=assistant.name)
            member = GraphMember(assistant.name, model_node)
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
