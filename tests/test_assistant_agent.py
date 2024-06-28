import unittest

import pytest
from unittest.mock import MagicMock, AsyncMock, patch

from langchain.utils import openai
from langchain_community.adapters.openai import ChatCompletion
from langchain_community.agents.openai_assistant import OpenAIAssistantV2Runnable

from copilot.core.agent import AssistantAgent
from copilot.core.schemas import QuestionSchema


@pytest.fixture
def assistant_agent():
    return AssistantAgent()

def test_get_assistant_id(assistant_agent):
    assert assistant_agent.get_assistant_id() is None

@pytest.fixture
def mock_openai_chatcompletion(monkeypatch):

    class AsyncChatCompletionIterator:
        def __init__(self, answer: str):
            self.answer_index = 0
            self.answer_deltas = answer.split(" ")

        def __aiter__(self):
            return self

        async def __anext__(self):
            if self.answer_index < len(self.answer_deltas):
                answer_chunk = self.answer_deltas[self.answer_index]
                self.answer_index += 1
                return openai.util.convert_to_openai_object(
                    {"choices": [{"delta": {"content": answer_chunk}}]})
            else:
                raise StopAsyncIteration

    async def mock_acreate(*args, **kwargs):
        return AsyncChatCompletionIterator("The capital of France is Paris.")

    monkeypatch.setattr(ChatCompletion, "acreate", mock_acreate)

@patch("copilot.core.agent.AssistantAgent.get_agent", autospec=True)
def test_get_agent(mock_get_agent, assistant_agent):
    assistant_id = "test_id"
    agent_instance = MagicMock(spec=OpenAIAssistantV2Runnable)
    mock_get_agent.return_value = agent_instance

    agent = assistant_agent.get_agent(assistant_id)

    mock_get_agent.assert_called_once_with(assistant_agent, assistant_id)
    assert agent == agent_instance


@patch("langchain.agents.AgentExecutor.invoke")
def test_execute(mock_invoke, assistant_agent):
    question = QuestionSchema(assistant_id="test_id", question="test question", local_file_ids=None)
    mock_invoke.return_value = {"output": "test response", "thread_id": "test_thread_id"}

    response = assistant_agent.execute(question)

    assert response.input == question.model_dump_json()
    assert response.output.response == "test response"
    assert response.output.conversation_id == "test_thread_id"

