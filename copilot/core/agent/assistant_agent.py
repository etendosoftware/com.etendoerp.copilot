import os
from typing import AsyncGenerator, Dict, Final

from copilot.baseutils.logging_envvar import read_optional_env_var
from copilot.core.agent.agent import AgentResponse, AssistantResponse, CopilotAgent
from copilot.core.schemas import QuestionSchema
from copilot.core.utils.models import get_openai_client
from langchain.agents import AgentExecutor
from langchain.agents.openai_assistant.base import OpenAIAssistantAction
from langchain_community.agents.openai_assistant import OpenAIAssistantV2Runnable
from langchain_core.agents import AgentFinish
from langchain_core.runnables import AddableDict


class AssistantAgent(CopilotAgent):
    """OpenAI Assistant Agent implementation."""

    OPENAI_MODEL: Final[str] = read_optional_env_var("OPENAI_MODEL", "gpt-4o")
    ASSISTANT_NAME: Final[str] = "Copilot [LOCAL]"

    def __init__(self):
        super().__init__()
        self._assistant_id = None

    def get_assistant_id(self) -> str:
        return self._assistant_id

    def get_agent(self, assistant_id: str) -> OpenAIAssistantV2Runnable:
        agent = OpenAIAssistantV2Runnable(
            client=get_openai_client(), assistant_id=assistant_id, as_agent=True
        )
        return agent

    def get_agent_executor(self, agent: OpenAIAssistantV2Runnable) -> AgentExecutor:
        return AgentExecutor(agent=agent, tools=self._configured_tools)

    def execute(self, question: QuestionSchema) -> AgentResponse:
        agent = self.get_agent(question.assistant_id)
        agent_executor = self.get_agent_executor(agent)
        full_question = question.question
        if question.local_file_ids is not None and len(question.local_file_ids) > 0:
            full_question += "\n\n" + "LOCAL FILES: " + "\n".join(question.local_file_ids)

        _input = {
            "content": full_question,
        }
        if question.conversation_id is not None:
            _input["thread_id"] = question.conversation_id
        response = agent_executor.invoke(_input)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(response=response["output"], conversation_id=response["thread_id"]),
        )

    async def aexecute(self, question: QuestionSchema) -> AsyncGenerator[AssistantResponse, None]:
        copilot_stream_debug = os.getenv("COPILOT_STREAM_DEBUG", "false").lower() == "true"  # Debug mode
        agent_executor = self._prepare_agent_executor(question)
        _input = self._prepare_input(question)

        async for event in agent_executor.astream_events(_input, version="v2"):
            async for response in self._handle_event(event, copilot_stream_debug):
                yield response

    def _prepare_agent_executor(self, question: QuestionSchema) -> AgentExecutor:
        agent = self.get_agent(question.assistant_id)
        return self.get_agent_executor(agent)

    def _prepare_input(self, question: QuestionSchema) -> Dict:
        full_question = question.question
        if question.local_file_ids:
            full_question += "\n\n" + "LOCAL FILES: " + "\n".join(question.local_file_ids)
        _input = {"content": full_question, "system_prompt": question.system_prompt}
        if question.conversation_id:
            _input["thread_id"] = question.conversation_id
        return _input

    async def _handle_event(
        self, event: Dict, copilot_stream_debug: bool
    ) -> AsyncGenerator[AssistantResponse, None]:
        if copilot_stream_debug:
            yield AssistantResponse(response=str(event), conversation_id="", role="debug")
        kind = event["event"]
        if kind == "on_tool_start" and len(event["parent_ids"]) == 1:
            yield AssistantResponse(response=event["name"], conversation_id="", role="tool")
        elif kind == "on_chain_end":
            output = event["data"]["output"]
            if (
                isinstance(output, list)
                or isinstance(output, OpenAIAssistantAction)
                or isinstance(output, AddableDict)
            ):
                return
            if isinstance(output, AgentFinish):
                return_values = output.return_values
                yield AssistantResponse(
                    response=str(return_values["output"]), conversation_id=return_values["thread_id"]
                )
