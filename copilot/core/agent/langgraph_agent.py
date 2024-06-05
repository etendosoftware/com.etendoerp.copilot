import functools
from typing import Final, List, Sequence

from langchain.agents.openai_assistant import OpenAIAssistantRunnable
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.messages import (
    HumanMessage, BaseMessage,
)
from langchain_core.utils.function_calling import convert_to_openai_function
from langchain_openai import ChatOpenAI

from .agent import AgentResponse, CopilotAgent
from .agent import AssistantResponse
from .. import utils
from ..langgraph.copilot_langgraph import GraphMember, CopilotLangGraph
from ..schemas import QuestionSchema

class LanggraphAgent(CopilotAgent):
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4-turbo-preview")

    def __init__(self):
        super().__init__()

    # The agent state is the input to each node in the graph
    def execute(self, question: QuestionSchema) -> AgentResponse:
        thread_id = question.conversation_id
        _tools = self._configured_tools
        tools = [convert_to_openai_function(tool) for tool in _tools]

        members = []

        def invoke_model_openai(state: List[BaseMessage], _agent, _name: str):
            response = _agent.invoke({"content": state["messages"][0].content})
            return {"messages": [HumanMessage(content=response.return_values["output"], name=_name)]}

        def invoke_model_langchain(state: Sequence[BaseMessage], _agent, _name: str):
            result = _agent.invoke(state)
            return {"messages": [HumanMessage(content=result.content, name=_name)]}

        if question.assistants:
            for assistant in question.assistants:
                member = None
                if assistant.type == "openai-assistant":
                    agent = OpenAIAssistantRunnable(assistant_id=assistant.assistant_id, as_agent=True, tools=tools)
                    model_node = functools.partial(invoke_model_openai, _agent=agent, _name=assistant.name)
                    member = GraphMember(assistant.name, model_node)
                else:
                    prompt = ChatPromptTemplate.from_messages(
                        [
                            (
                                "system",
                                assistant.system_prompt
                            ),
                            MessagesPlaceholder(variable_name="messages"),
                        ]
                    )
                    model = ChatOpenAI(temperature=0, streaming=False, model=self.OPENAI_MODEL)
                    agent = prompt | model
                    model_node = functools.partial(invoke_model_langchain, _agent=agent, _name=assistant.name)
                    member = GraphMember(assistant.name, model_node)

                members.append(member)

        lang_graph = CopilotLangGraph(members, question.graph)

        final_response = lang_graph.invoke(question=question.question)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=final_response, assistant_id=question.assistant_id, conversation_id=thread_id
            )
        )
