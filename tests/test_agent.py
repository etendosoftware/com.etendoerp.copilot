from unittest.mock import Mock

import pytest
from copilot.core import agent
from copilot.core.exceptions import (
    OpenAIApiKeyNotFound,
    SystemPromptNotFound,
    UnsupportedAgent,
)


def test_langchain_agent_is_intanciated():
    agent.AGENT_TYPE = agent.AgentEnum.LANGCHAIN.value
    copilot_agent = agent._get_agent_executor()
    assert isinstance(copilot_agent, agent.langchain_agent.LangchainAgent)


def test_assistant_agent_is_intanciated():
    agent.AGENT_TYPE = agent.AgentEnum.OPENAI_ASSISTANT.value
    copilot_agent = agent._get_agent_executor()
    assert isinstance(copilot_agent, agent.assistant_agent.AssistantAgent)


def test_unnsupported_agent():
    agent.AGENT_TYPE = "unexistent_agent"
    with pytest.raises(UnsupportedAgent, match=UnsupportedAgent.message):
        agent._get_agent_executor()


@pytest.fixture
def copilot_agent():
    return agent.agent.CopilotAgent()


def test_open_api_key_is_not_set(copilot_agent):
    copilot_agent.OPENAI_API_KEY = None
    with pytest.raises(OpenAIApiKeyNotFound, match=OpenAIApiKeyNotFound.message):
        copilot_agent._assert_open_api_key_is_set()


def test_system_propmt_is_not_set(copilot_agent):
    copilot_agent.SYSTEM_PROMPT = None
    with pytest.raises(SystemPromptNotFound, match=SystemPromptNotFound.message):
        copilot_agent._assert_system_prompt_is_set()


def test_agent_must_be_implemented(copilot_agent):
    with pytest.raises(NotImplementedError):
        copilot_agent.execute(question=Mock())
