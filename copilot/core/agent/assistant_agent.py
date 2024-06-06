import os
from typing import Final

from langchain_community.agents.openai_assistant import OpenAIAssistantV2Runnable
from langchain.agents import AgentExecutor

from .agent import AgentResponse, AssistantResponse, CopilotAgent
from .. import utils
from ..schemas import QuestionSchema


def _get_openai_client():
    from openai import OpenAI
    return OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


class AssistantAgent(CopilotAgent):
    """OpenAI Assistant Agent implementation."""

    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4-1106-preview")
    ASSISTANT_NAME: Final[str] = "Copilot [LOCAL]"

    def __init__(self):
        super().__init__()
        self._assistant_id = None

    def get_assistant_id(self) -> str:
        return self._assistant_id

    def get_agent(self, assistant_id: str):
        agent = OpenAIAssistantV2Runnable(assistant_id=assistant_id, as_agent=True)

    def get_executor(self, agent) -> AgentExecutor:
        return AgentExecutor(agent=agent, tools=self._configured_tools)

    def execute(self, question: QuestionSchema) -> AgentResponse:
        agent = self.get_agent(question.assistant_id)
        agent_executor = self.get_executor(agent)
        _input = {
            "content": question.question,
        }
        if question.conversation_id is not None:
            _input["thread_id"] = question.conversation_id
        response = agent_executor.invoke(_input)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=response["output"], assistant_id=question.assistant_id, conversation_id=response["thread_id"]
            ),
        )
