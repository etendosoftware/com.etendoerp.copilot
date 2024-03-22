import functools
import json
import os
import time

from time import sleep
from typing import Final

from langchain.tools.render import format_tool_to_openai_function
from langchain_community.chat_models import ChatOpenAI
from langchain_community.tools.tavily_search import TavilySearchResults
from langchain_core.agents import AgentFinish
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from .. import utils
from ..exceptions import AssistantIdNotFound, AssistantTimeout
from ..schemas import QuestionSchema
from .agent import AgentResponse, AssistantResponse, CopilotAgent
from langchain.agents.openai_assistant.base import OpenAIAssistantRunnable
from typing import Annotated, Any, Dict, List, Optional, Sequence, TypedDict
from langchain_core.messages import BaseMessage, HumanMessage, AIMessage
import operator
from langgraph.graph import StateGraph, END, MessageGraph


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

    def agent_node(self, state, agent):
        result = agent.invoke(state)
        return {"messages": [AIMessage(content=result["output"], name=name)]}


    # The agent state is the input to each node in the graph
    def execute(self, question: QuestionSchema) -> AgentResponse:
        thread_id = question.conversation_id
        _tools = self._configured_tools
        _tools.append(TavilySearchResults(include_domains=["docs.etendo.software"]))
        tools = [format_tool_to_openai_function(tool) for tool in _tools]

        def invoke_model_openai(state: List[BaseMessage], _agent):
            response = _agent.invoke({"content": state[0].content})
            return HumanMessage(response.return_values["output"])

        def invoke_model_langchain(state: Sequence[BaseMessage], _agent):
            response = _agent.invoke({"messages": state})
            return response

        workflow = MessageGraph()
        if question.assistants:
            for assistant in question.assistants:
                if assistant.type == "openai-assistant":
                    agent = OpenAIAssistantRunnable(assistant_id=assistant.assistant_id, as_agent=True, tools=tools)
                    agent_node = functools.partial(invoke_model_openai, _agent=agent)
                    workflow.add_node(assistant.name, agent_node)
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
                    model_node = functools.partial(invoke_model_langchain, _agent=agent)
                    workflow.add_node(assistant.name, model_node)

            workflow.set_entry_point(question.assistants[0].name)
            for i in range(0, len(question.assistants) - 1):
                workflow.add_edge(question.assistants[i].name, question.assistants[i+1].name)

            workflow.add_edge(question.assistants[-1].name, END)

        """
        model = ChatOpenAI(temperature=0)
        reflection_prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "You're a kinder garden teacher, translate for a child with a lot of emojis"
                ),
                MessagesPlaceholder(variable_name="messages"),
            ]
        )
        teacher = reflection_prompt | model

        def teacher_node(state: Sequence[BaseMessage]):
            return teacher.invoke({"messages": state})

        workflow.add_node("teacher", teacher_node)

        workflow.add_edge("researcher", "teacher")
        workflow.add_edge("teacher", END)
        """


        graph = workflow.compile()

        #response = agent.invoke(params)
        #response = graph.invoke({"messages": question.question})
        response = graph.invoke(HumanMessage(question.question))

        #thread_id = response.thread_id
        final_response = response[-1].content
        # for resp in response:
            # if type(resp) == AIMessage:
                # final_response = resp.content + "\n"

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=final_response, assistant_id=question.assistant_id, conversation_id=thread_id
            )
        )

class AgentState(TypedDict):
    # The annotation tells the graph that new messages will always
    # be added to the current states
    messages: Annotated[Sequence[BaseMessage], operator.add]
    # The 'next' field indicates where to route to next
    next: str

