import os
from unittest.mock import Mock
from langsmith import unit 
import pytest
from copilot.core import agent
from copilot.core.exceptions import (
    OpenAIApiKeyNotFound,
    SystemPromptNotFound,
    UnsupportedAgent,
)

@unit 
def test_langchain_agent_is_instanciated():
    agent.AGENT_TYPE_ENVAR = agent.AgentEnum.LANGCHAIN.value
    copilot_agent = agent._get_agent_executors()[agent.AGENT_TYPE_ENVAR]
    assert isinstance(copilot_agent, agent.langchain_agent.LangchainAgent)

@unit 
def test_assistant_agent_is_instanciated():
    agent.AGENT_TYPE_ENVAR = agent.AgentEnum.OPENAI_ASSISTANT.value
    copilot_agent = agent._get_agent_executors()[agent.AGENT_TYPE_ENVAR]
    assert isinstance(copilot_agent, agent.assistant_agent.AssistantAgent)

@unit 
def test_unnsupported_agent():
    agent.AGENT_TYPE_ENVAR = "unexistent_agent"
    with pytest.raises(KeyError, match="'unexistent_agent'"):
        agent._get_agent_executors()[agent.AGENT_TYPE_ENVAR]

@unit 
@pytest.fixture
def copilot_agent():
    return agent.agent.CopilotAgent()

@unit 
def test_open_api_key_is_not_set(copilot_agent):
    copilot_agent.OPENAI_API_KEY = None
    with pytest.raises(OpenAIApiKeyNotFound, match=OpenAIApiKeyNotFound.message):
        copilot_agent._assert_open_api_key_is_set()

@unit 
def test_system_propmt_is_not_set(copilot_agent):
    copilot_agent.SYSTEM_PROMPT = None
    with pytest.raises(SystemPromptNotFound, match=SystemPromptNotFound.message):
        copilot_agent._assert_system_prompt_is_set()

@unit 
def test_agent_must_be_implemented(copilot_agent):
    with pytest.raises(NotImplementedError):
        copilot_agent.execute(question=Mock())
