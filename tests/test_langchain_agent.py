"""
Test the LangchainAgent class
"""
from unittest.mock import MagicMock, patch

import pytest
from copilot.core.agent.langchain_agent import CustomOutputParser, LangchainAgent
from copilot.core.schemas import QuestionSchema
from copilot.core.utils import get_full_question
from langchain_core.agents import AgentFinish
from langchain_core.runnables import RunnableSequence


@pytest.fixture
def langchain_agent():
    with patch("copilot.core.tool_loader.ToolLoader") as mock_tool_loader:
        mock_load_configured_tools = mock_tool_loader.return_value.load_configured_tools
        mock_load_configured_tools.return_value = []  # Return empty list instead of MagicMock
        return LangchainAgent()


def test_get_agent_openai(langchain_agent):
    with patch("langchain_openai.ChatOpenAI"):
        agent = langchain_agent.get_agent(
            provider="openai", open_ai_model="gpt-4o", tools=[], system_prompt="Test system prompt"
        )
        assert agent is not None
        assert isinstance(agent, RunnableSequence)


def test_get_agent_gemini(langchain_agent):
    with patch("langchain_google_genai.ChatGoogleGenerativeAI"):
        agent = langchain_agent.get_agent(
            provider="gemini", open_ai_model="gpt-4o", tools=[], system_prompt="Test system prompt"
        )
        assert agent is not None
        assert isinstance(agent, RunnableSequence)


def test_get_agent_executor(langchain_agent):
    with patch.object(langchain_agent, "get_openai_agent") as mock_get_openai_agent:
        agent = langchain_agent.get_agent(
            provider="gemini", open_ai_model="gpt-4o", tools=[], system_prompt="Test system prompt"
        )
        mock_get_openai_agent.return_value = agent
        agent_executor = langchain_agent.get_agent_executor(agent)
        assert agent_executor.agent.runnable == agent


def test_execute(langchain_agent):
    question = QuestionSchema(
        provider="openai",
        model="gpt-4o",
        tools=[],
        system_prompt="Test system prompt",
        history=[],
        question="Test question",
        conversation_id="123",
    )
    with patch.object(langchain_agent, "get_agent") as mock_get_agent, patch.object(
        langchain_agent, "get_agent_executor"
    ) as mock_get_agent_executor, patch.object(
        langchain_agent._memory, "get_memory"
    ) as mock_get_memory, patch(
        "langchain.agents.AgentExecutor.invoke"
    ) as mock_invoke:
        mock_agent = MagicMock()
        mock_executor = MagicMock()
        mock_get_agent.return_value = mock_agent
        mock_get_agent_executor.return_value = mock_executor
        mock_get_memory.return_value = "mock memory"
        mock_invoke.return_value = {"output": "mock output"}

        response = langchain_agent.execute(question)
        assert response.input == get_full_question(question)


async def test_aexecute():
    langchain_agent = LangchainAgent()
    question = QuestionSchema(
        provider="openai",
        model="gpt-4o",
        tools=[],
        system_prompt="Test system prompt",
        history=[],
        question="Test question",
        conversation_id="123",
    )
    with patch.object(langchain_agent, "get_agent") as mock_get_agent, patch.object(
        langchain_agent, "get_agent_executor"
    ) as mock_get_agent_executor, patch.object(
        langchain_agent._memory, "get_memory"
    ) as mock_get_memory, patch(
        "langchain.agents.AgentExecutor.astream_events"
    ) as mock_astream_events:
        mock_agent = MagicMock()
        mock_executor = MagicMock()
        mock_get_agent.return_value = mock_agent
        mock_get_agent_executor.return_value = mock_executor
        mock_get_memory.return_value = "mock memory"
        mock_astream_events.return_value = [
            {"event": "on_tool_start", "name": "tool", "parent_ids": [1]},
            {
                "event": "on_chain_end",
                "data": {"output": AgentFinish(return_values={"output": "mock output"}, log="mock log")},
            },
        ]

        response_generator = langchain_agent.aexecute(question)
        [response async for response in response_generator]


def test_custom_output_parser():
    parser = CustomOutputParser()
    output = "Test output"
    parsed_output = parser.parse(output)
    assert isinstance(parsed_output, AgentFinish)
    assert parsed_output.return_values["output"] == output
