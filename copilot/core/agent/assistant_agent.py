import json
import os
from time import sleep
from typing import Final, AsyncGenerator

from langchain.agents import AgentExecutor
from langchain.agents.openai_assistant.base import OpenAIAssistantAction
from langchain_community.agents.openai_assistant import OpenAIAssistantV2Runnable
from langchain_core.runnables import AddableDict

from .agent import AgentResponse, AssistantResponse, CopilotAgent
from .. import utils
from ..schemas import QuestionSchema

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
        return agent

    def get_executor(self, agent) -> AgentExecutor:
        return AgentExecutor(agent=agent, tools=self._configured_tools)

    def execute(self, question: QuestionSchema) -> AgentResponse:
        agent = self.get_agent(question.assistant_id)
        agent_executor = self.get_executor(agent)
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
            output=AssistantResponse(
                response=response["output"], conversation_id=response["thread_id"]
            ),
        )

    def _response(self, response: AssistantResponse):
        json_value = json.dumps({"answer": {"assistant_id": response.assistant_id, "response": response.response,
                                            "conversation_id": response.conversation_id}})
        return "data: " + json_value + "\n"

    async def aexecute(self, question: QuestionSchema) -> AssistantResponse:
        copilot_stream_debug = os.getenv("COPILOT_STREAM_DEBUG", "false").lower() == "true" # Debug mode
        agent = self.get_agent(question.assistant_id)
        agent_executor = self.get_executor(agent)
        full_question = question.question
        if question.local_file_ids is not None and len(question.local_file_ids) > 0:
            full_question += "\n\n" + "LOCAL FILES: " + "\n".join(question.local_file_ids)

        _input = {
            "content": full_question,
        }
        if question.conversation_id is not None:
            _input["thread_id"] = question.conversation_id

        async for event in agent_executor.astream_events(_input, version="v2"):
            if copilot_stream_debug:
                yield AssistantResponse(
                    response=str(event), conversation_id=question.conversation_id, role="debug"
                )
            kind = event["event"]
            if kind == "on_chain_end":
                if type(event["data"]["output"]) == list:
                    for output in event["data"]["output"]:
                        if type(output) == OpenAIAssistantAction:
                            action: OpenAIAssistantAction = output
                            yield AssistantResponse(
                                response=action.tool, conversation_id=action.thread_id, role="tool"
                            )
                else:
                    if type(event["data"]["output"]) == OpenAIAssistantAction:
                        action: OpenAIAssistantAction = event["data"]["output"]
                        yield AssistantResponse(
                            response=action.tool, conversation_id=action.thread_id, role="tool"
                        )
                    else:
                        if type(event["data"]["output"]) != AddableDict:
                            return_values = event["data"]["output"].return_values
                            yield AssistantResponse(
                                response=str(return_values["output"]), conversation_id=return_values["thread_id"]
                            )