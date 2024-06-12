import functools
from typing import Final, List, Sequence

from langchain_core.messages import (
    HumanMessage, BaseMessage,
)

from . import AssistantAgent, LangchainAgent
from .agent import AgentResponse, CopilotAgent
from .agent import AssistantResponse
from ..langgraph.copilot_langgraph import CopilotLangGraph
from ..langgraph.patterns import SupervisorPattern
from ..langgraph.patterns.base_pattern import GraphMember
from ..memory.memory_handler import MemoryHandler
from ..schemas import GraphQuestionSchema


class LanggraphAgent(CopilotAgent):
    _memory: MemoryHandler = None

    def __init__(self):
        super().__init__()
        self._memory = MemoryHandler()

    # The agent state is the input to each node in the graph
    def execute(self, question: GraphQuestionSchema) -> AgentResponse:
        thread_id = question.conversation_id
        _tools = self._configured_tools

        def invoke_model_openai(state: List[BaseMessage], _agent, _name: str):
            response = _agent.invoke({"content": state["messages"][0].content})
            return {"messages": [HumanMessage(content=response.return_values["output"], name=_name)]}

        def invoke_model_langchain(state: Sequence[BaseMessage], _agent, _name: str):
            result = _agent.invoke(state)
            content = ""
            if result.type == "AgentFinish":
                content = result.messages[-1].content
            else:
                content = result.content

            return {"messages": [HumanMessage(content=content, name=_name)]}

        members: list[GraphMember] = self.get_members(invoke_model_langchain, invoke_model_openai, question)

        lang_graph = CopilotLangGraph(members, question.graph, SupervisorPattern())

        final_response = lang_graph.invoke(question=question.question, thread_id=thread_id)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=final_response, conversation_id=thread_id
            )
        )

    def get_members(self, invoke_model_langchain, invoke_model_openai, question) -> list[GraphMember]:
        members = []
        if question.assistants:
            for assistant in question.assistants:
                member = None
                if assistant.type == "openai-assistant":
                    agent = self.get_assistant_agent().get_agent(assistant.assistant_id)
                    model_node = functools.partial(invoke_model_openai, _agent=agent, _name=assistant.name)
                    member = GraphMember(assistant.name, model_node)
                else:
                    agent = LangchainAgent().get_agent(assistant.provider, assistant.model, assistant.tools,
                                                       assistant.system_prompt)
                    model_node = functools.partial(invoke_model_langchain, _agent=agent, _name=assistant.name)
                    member = GraphMember(assistant.name, model_node)

                members.append(member)
        return members

    def get_assistant_agent(self):
        return AssistantAgent()
