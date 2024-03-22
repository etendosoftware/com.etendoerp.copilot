import functools

from typing import Final

from langchain.tools.render import format_tool_to_openai_function
from langchain_community.chat_models import ChatOpenAI
from langchain_community.tools.tavily_search import TavilySearchResults
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from .. import utils
from copilot.core.langgraph.copilot_langgraph import CopilotLangGraph, GraphMember
from ..exceptions import AssistantIdNotFound
from ..schemas import QuestionSchema
from .agent import AgentResponse, AssistantResponse, CopilotAgent
from langchain.agents.openai_assistant.base import OpenAIAssistantRunnable
from typing import Annotated, List, Sequence, TypedDict
from langchain_core.messages import BaseMessage, HumanMessage, AIMessage
from langgraph.graph import END, MessageGraph


class AssistantAgent(CopilotAgent):
    """OpenAI Assistant Agent implementation."""

    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4-1106-preview")
    ASSISTANT_NAME: Final[str] = "Copilot [LOCAL]"

    def __init__(self):
        super().__init__()
        self._formated_tools_openai = None
        self._assistant = None  # self._get_openai_assistant()

    def _update_assistant(self, assistant_id: int):
        from openai import NotFoundError
        try:
            # import delayed to avoid conflict with openai version used by langchain agent
            from openai import OpenAI
            self._client = OpenAI(api_key=self.OPENAI_API_KEY)
            self._assistant = self._client.beta.assistants.update(
                assistant_id,
                name=self.ASSISTANT_NAME,
                instructions=self.SYSTEM_PROMPT,
                tools=self._formated_tools_openai,
                model=self.OPENAI_MODEL,
            )
        except NotFoundError as ex:
            raise AssistantIdNotFound(assistant_id=assistant_id) from ex

    def get_assistant_id(self) -> str:
        return self._assistant.id

    # The agent state is the input to each node in the graph
    def execute(self, question: QuestionSchema) -> AgentResponse:
        thread_id = question.conversation_id
        _tools = self._configured_tools
        _tools.append(TavilySearchResults(include_domains=["docs.etendo.software"]))
        tools = [format_tool_to_openai_function(tool) for tool in _tools]

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
                    model = ChatOpenAI(temperature=0, streaming=False, model="gpt-4-1106-preview")
                    agent = prompt | model
                    model_node = functools.partial(invoke_model_langchain, _agent=agent, _name=assistant.name)
                    member = GraphMember(assistant.name, model_node)

                members.append(member)

        copilotLangGraph = CopilotLangGraph(members, question.graph)

        final_response = copilotLangGraph.invoke(question=question.question)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=final_response, assistant_id=question.assistant_id, conversation_id=thread_id
            )
        )
