
import asyncio
import unittest
from unittest.mock import MagicMock
from unittest.mock import patch, AsyncMock

import pytest
import vcr
from langchain.utils import openai
from langchain_community.adapters.openai import ChatCompletion
from langchain_community.agents.openai_assistant import OpenAIAssistantV2Runnable
from openai import OpenAI

from copilot.core.agent import AgentResponse
from copilot.core.agent import AssistantAgent
from copilot.core.agent.agent import AssistantResponse
from copilot.core.schemas import QuestionSchema


class TestAssistantAgent(unittest.TestCase):

    @pytest.fixture()
    def vcr_config(self):
        return {"record_mode": "rewrite"}

    @pytest.fixture
    def mock_openai_chatcompletion(self, monkeypatch):

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
    @vcr.use_cassette('tests/fixtures/assistants/test_get_agent.yaml', allow_playback_repeats=True)
    def test_get_agent(self, mock_get_agent):
        assistant_agent = AssistantAgent()
        assistant_id = "test_id"
        agent_instance = MagicMock(spec=OpenAIAssistantV2Runnable)
        mock_get_agent.return_value = agent_instance

        agent = assistant_agent.get_agent(assistant_id)

        mock_get_agent.assert_called_once_with(assistant_agent, assistant_id)
        assert agent == agent_instance


    @patch("langchain.agents.AgentExecutor.invoke")
    @vcr.use_cassette('tests/fixtures/assistants/test_execute.yaml', allow_playback_repeats=True)
    def test_execute(self, mock_invoke):
        assistant_agent = AssistantAgent()
        question = QuestionSchema(assistant_id="test_id", question="test question", local_file_ids=None)
        mock_invoke.return_value = {"output": "test response", "thread_id": "test_thread_id"}

        response = assistant_agent.execute(question)

        assert response.input == question.model_dump_json()
        assert response.output.response == "test response"
        assert response.output.conversation_id == "test_thread_id"

    @patch('copilot.core.utils.read_optional_env_var')
    @patch('langchain_community.agents.openai_assistant.OpenAIAssistantV2Runnable')
    @vcr.use_cassette('tests/fixtures/assistants/setup.yaml', allow_playback_repeats=True)
    def setUp(self, mock_openai_assistant, mock_read_optional_env_var):
        self.client = OpenAI()

        assistant = self.client.beta.assistants.create(
            name="Math Tutor",
            instructions="You are a personal math tutor. Write and run code to answer math questions.",
            tools=[{"type": "code_interpreter"}],
            model="gpt-4o",
        )
        self.assistant_id = assistant.id

        def mock_read_optional_env_var_side_effect(var_name):
            if var_name == "model":
                return "gpt-4o"
            return None

        self.mock_read_optional_env_var = mock_read_optional_env_var
        self.mock_read_optional_env_var.side_effect = mock_read_optional_env_var_side_effect

        self.mock_openai_assistant_instance = mock_openai_assistant.return_value
        self.agent = AssistantAgent()
        self.question = QuestionSchema(
            assistant_id=self.assistant_id,
            question="What is the capital of France?",
            local_file_ids=[],
            conversation_id=None,
            system_prompt="",
        )

    @patch('langchain.agents.AgentExecutor', autospec=True)
    @vcr.use_cassette('tests/fixtures/assistants/test_execute2.yaml')
    def test_execute2(self, mock_agent_executor):
        mock_agent_executor_instance = mock_agent_executor.return_value
        thread = self.client.beta.threads.create()
        mock_agent_executor_instance.invoke.return_value = {
            "output": "The capital of France is Paris.",
            "thread_id": thread.id
        }
        response = self.agent.execute(self.question)
        self.assertIsInstance(response, AgentResponse)
        self.assertEqual(response.output.response, "The capital of France is Paris.")

    @patch('json.dumps')
    def test_response(self, mock_json_dumps):
        response = AssistantResponse(response="Test response", conversation_id="1234")
        mock_json_dumps.return_value = '{"answer": {"assistant_id": null, "response": "Test response", "conversation_id": "1234"}}'
        result = self.agent._response(response)
        self.assertEqual(result,
                         'data: {"answer": {"assistant_id": null, "response": "Test response", "conversation_id": "1234"}}\n')

    @patch('langchain.agents.AgentExecutor')
    @vcr.use_cassette('tests/fixtures/assistants/test_aexecute.yaml')
    def test_aexecute(self, mock_agent_executor):
        mock_agent_executor_instance = mock_agent_executor.return_value
        mock_agent_executor_instance.astream_events = AsyncMock(return_value=AsyncMock())
        mock_event = {"event": "on_tool_start", "parent_ids": [1], "name": "Test Tool"}

        async def mock_async_generator():
            yield mock_event

        mock_agent_executor_instance.astream_events.return_value = mock_async_generator()

        async def test_coroutine():
            async for response in self.agent.aexecute(self.question):
                self.assertIsInstance(response, AssistantResponse)
                self.assertEqual(response.conversation_id, "")

        loop = asyncio.get_event_loop()
        loop.run_until_complete(test_coroutine())

