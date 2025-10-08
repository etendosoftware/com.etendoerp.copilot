import asyncio
from unittest.mock import MagicMock, patch

from copilot.core.agent import AssistantAgent
from copilot.core.schemas import QuestionSchema
from copilot.core.utils.models import get_openai_client
from langchain_community.agents.openai_assistant import OpenAIAssistantV2Runnable
from langchain_core.agents import AgentFinish


@patch("copilot.core.agent.AssistantAgent.get_agent", autospec=True)
def test_get_agent(mock_get_agent):
    assistant_agent = AssistantAgent()
    assistant_id = "test_id"
    agent_instance = MagicMock(spec=OpenAIAssistantV2Runnable)
    mock_get_agent.return_value = agent_instance

    agent = assistant_agent.get_agent(assistant_id)

    mock_get_agent.assert_called_once_with(assistant_agent, assistant_id)
    assert agent == agent_instance


@patch("langchain.agents.AgentExecutor.invoke")
def test_execute(mock_invoke):
    assistant_agent = AssistantAgent()
    question = QuestionSchema(assistant_id="test_id", question="test question", local_file_ids=None)
    mock_invoke.return_value = {"output": "test response", "thread_id": "test_thread_id"}

    response = assistant_agent.execute(question)

    assert response.input == question.model_dump_json()
    assert response.output.response == "test response"
    assert response.output.conversation_id == "test_thread_id"


@patch("copilot.core.utils.read_optional_env_var")
@patch("langchain_community.agents.openai_assistant.OpenAIAssistantV2Runnable")
def set_up(mock_openai_assistant, mock_read_optional_env_var):
    client = get_openai_client()

    assistant = client.beta.assistants.create(
        name="Math Tutor",
        instructions="You are a personal math tutor. Write and run code to answer math questions.",
        tools=[{"type": "code_interpreter"}],
        model="gpt-4o",
    )
    assistant_id = assistant.id

    def mock_read_optional_env_var_side_effect(var_name):
        if var_name == "model":
            return "gpt-4o"
        return None

    mock_read_optional_env_var.side_effect = mock_read_optional_env_var_side_effect

    QuestionSchema(
        assistant_id=assistant_id,
        question="What is the capital of France?",
        local_file_ids=[],
        conversation_id=None,
        system_prompt="",
    )


@patch("copilot.core.agent.assistant_agent.AssistantAgent._prepare_agent_executor")
def test_aexecute(mock_prepare):
    assistant_id = "test_assistant_id"
    question = QuestionSchema(
        assistant_id=assistant_id,
        question="What is the capital of France?",
        local_file_ids=[],
        conversation_id=None,
        system_prompt="",
    )
    agent = AssistantAgent()
    mock_agent_executor_instance = MagicMock()
    mock_prepare.return_value = mock_agent_executor_instance

    async def mock_astream_events(*args, **kwargs):
        yield {"event": "on_tool_start", "parent_ids": [1], "name": "Test Tool"}
        yield {
            "event": "on_chain_end",
            "data": {
                "output": AgentFinish(return_values={"output": "Paris", "thread_id": "test_thread"}, log="")
            },
        }

    mock_agent_executor_instance.astream_events = mock_astream_events

    async def test_coroutine():
        responses = []
        async for response in agent.aexecute(question):
            responses.append(response)
        assert len(responses) == 2
        assert responses[0].response == "Test Tool"
        assert responses[0].role == "tool"
        assert responses[1].response == "Paris"
        assert responses[1].conversation_id == "test_thread"

    loop = asyncio.get_event_loop()
    loop.run_until_complete(test_coroutine())
