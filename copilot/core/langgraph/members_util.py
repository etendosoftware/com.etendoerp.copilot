import functools
from typing import List, Sequence

from langchain.agents import AgentExecutor
from langchain_core.messages import BaseMessage, HumanMessage
from langsmith import traceable

from copilot.core.agent import LangchainAgent, AssistantAgent
from copilot.core.langgraph.patterns.base_pattern import GraphMember
from copilot.core.schemas import AssistantSchema


class MembersUtil:
    @traceable
    def get_members(self, question) -> list[GraphMember]:
        members = []
        if question.assistants:
            for assistant in question.assistants:
                members.append(self.get_member(assistant))
        return members

    @traceable
    def model_openai_invoker(self):
        def invoke_model_openai(state: List[BaseMessage], _agent: AgentExecutor, _name: str):
            response = _agent.invoke({"content": state["messages"][-1].content})
            return {"messages": [HumanMessage(content=response["output"], name=_name)]}

        return invoke_model_openai

    @traceable
    def model_langchain_invoker(self):
        def invoke_model_langchain(state: Sequence[BaseMessage], _agent, _name: str):
            response = _agent.invoke({"messages": state["messages"]})
            return {"messages": [HumanMessage(content=response["output"], name=_name)]}

        return invoke_model_langchain

    @traceable
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
            _agent = langchain_agent.get_agent(assistant.provider, assistant.model, assistant.tools,
                                               assistant.system_prompt)
            agent_executor = langchain_agent.get_agent_executor(_agent)
            model_node = functools.partial(self.model_langchain_invoker(), _agent=agent_executor, _name=assistant.name)
            member = GraphMember(assistant.name, model_node)
        return member

    @traceable
    def get_assistant_agent(self):
        return AssistantAgent()

    @traceable
    def get_assistant_supervisor_info(assistant_name, full_question):
        if full_question is None:
            return None
        for member_info in full_question.assistants:
            if member_info.name == assistant_name:
                return ": " + member_info.description
        return None
