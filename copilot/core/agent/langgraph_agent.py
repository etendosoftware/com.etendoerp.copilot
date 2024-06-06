import functools
from typing import Final, List, Sequence

from langchain.agents.openai_assistant import OpenAIAssistantRunnable
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.messages import (
    HumanMessage, BaseMessage,
)
from langchain_core.utils.function_calling import convert_to_openai_function
from langchain_openai import ChatOpenAI

from . import AssistantAgent, LangchainAgent
from .agent import AgentResponse, CopilotAgent
from .agent import AssistantResponse
from .. import utils
from ..langgraph.copilot_langgraph import GraphMember, CopilotLangGraph
from ..schemas import QuestionSchema, GraphQuestionSchema


class LanggraphAgent(CopilotAgent):
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4-turbo-preview")

    def __init__(self):
        super().__init__()

    # The agent state is the input to each node in the graph
    def execute(self, question: GraphQuestionSchema) -> AgentResponse:
        thread_id = question.conversation_id
        _tools = self._configured_tools
        members = []

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

        if question.assistants:
            for assistant in question.assistants:
                member = None
                if assistant.type == "openai-assistant":
                    agent = AssistantAgent().get_agent(assistant.assistant_id)
                    model_node = functools.partial(invoke_model_openai, _agent=agent, _name=assistant.name)
                    member = GraphMember(assistant.name, model_node)
                else:
                    agent = LangchainAgent().get_agent(assistant.provider, assistant.model, assistant.tools, assistant.system_prompt)
                    model_node = functools.partial(invoke_model_langchain, _agent=agent, _name=assistant.name)
                    member = GraphMember(assistant.name, model_node)

                members.append(member)

        lang_graph = CopilotLangGraph(members, question.graph)

        final_response = lang_graph.invoke(question=question.question)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=final_response, conversation_id=thread_id
            )
        )
