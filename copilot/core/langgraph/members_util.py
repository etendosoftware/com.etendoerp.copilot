import functools
from typing import List, Sequence

from langchain_core.messages import BaseMessage, HumanMessage

from copilot.core.agent import LangchainAgent, AssistantAgent
from copilot.core.langgraph.patterns.base_pattern import GraphMember
from copilot.core.schemas import AssistantSchema


class MembersUtil:
    def get_members(self, question) -> list[GraphMember]:
        members = []
        if question.assistants:
            for assistant in question.assistants:
                members.append(self.get_member(assistant))
        return members

    def model_openai_invoker(self):
        def invoke_model_openai(state: List[BaseMessage], _agent, _name: str):
            response = _agent.invoke({"content": state["messages"][0].content})
            return {"messages": [HumanMessage(content=response.return_values["output"], name=_name)]}

        return invoke_model_openai

    def model_langchain_invoker(self):
        def invoke_model_langchain(state: Sequence[BaseMessage], _agent, _name: str):
            result = _agent.invoke(state)
            content = ""
            if result.type == "AgentFinish":
                content = result.messages[-1].content
            else:
                content = result.content

            return {"messages": [HumanMessage(content=content, name=_name)]}

        return invoke_model_langchain

    def get_member(self, assistant: AssistantSchema):
        member = None
        if assistant.type == "openai-assistant":
            agent = self.get_assistant_agent().get_agent(assistant.assistant_id)
            model_node = functools.partial(self.model_openai_invoker(), _agent=agent, _name=assistant.name)
            member = GraphMember(assistant.name, model_node)
        else:
            agent = LangchainAgent().get_agent(assistant.provider, assistant.model, assistant.tools,
                                               assistant.system_prompt)
            model_node = functools.partial(self.model_langchain_invoker(), _agent=agent, _name=assistant.name)
            member = GraphMember(assistant.name, model_node)
        return member

    def get_assistant_agent(self):
        return AssistantAgent()