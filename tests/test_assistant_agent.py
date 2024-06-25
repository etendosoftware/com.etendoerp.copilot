import os
import openai
from unittest.mock import Mock

import pytest
from copilot.core.agent import AssistantAgent
from copilot.core.exceptions import (
    AssistantIdNotFound,
    AssistantTimeout,
    OpenAIApiKeyNotFound,
)
from copilot.core.schemas import QuestionSchema

if os.getenv("AGENT_TYPE") != "openai-assistant":
    pytest.skip("Skipping open 1.2.4 is required", allow_module_level=True)


@pytest.fixture
def assistant_agent() -> AssistantAgent:
    from copilot.core import agent
    agent.AGENT_TYPE_ENVAR = agent.AgentEnum.OPENAI_ASSISTANT.value
    agent.agent.CopilotAgent.OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
    return AssistantAgent()


def test_missing_api_key(monkeypatch):
    """
    Test Case 2: Missing API Key
    Given: The OpenAI API key is not set in the environment variables.
    When: The assistant function is called.
    Then: The function fails gracefully, providing a clear error message about the missing API key.
    """
    with pytest.raises(OpenAIApiKeyNotFound, match=OpenAIApiKeyNotFound.message):
        from copilot.core.agent.agent import CopilotAgent

        CopilotAgent.OPENAI_API_KEY = None
        AssistantAgent()


def test_invalid_assistant_id(assistant_agent):
    """
    Test Case 3: Invalid Assistant ID
    Given: An invalid assistant_id is provided (e.g., a non-existent or malformed ID).
    When: The assistant function is called with this invalid assistant_id.
    Then: The function handles the error gracefully and returns an appropriate error message.
    """
    with pytest.raises(AssistantIdNotFound, match="No assistant found with id 'sarasa'"):
        assistant_agent.execute(QuestionSchema(question="Fake question", assistant_id="sarasa"))


def test_empty_question(assistant_agent):
    """
    Test Case 5: Empty Question
    Given: A valid assistant_id and conversation_id are provided.
           The question parameter is an empty string.
    When: The assistant function is called with these parameters.
    Then: The function returns an error or a prompt asking for a valid question.
    """
    response = assistant_agent.execute(
        QuestionSchema(question="", assistant_id=assistant_agent._assistant.id)
    )
    assert "assist you" in response.output.message or "you need assistance" in response.output.message

def test_update_assistant():
    """
    Test Case 7: Successful Update of Assistant
    Given: A valid assistant_id is provided for an existing assistant.
           The function is expected to update the assistant with new parameters.
    When: The assistant function is called with the assistant_id and new parameters.
    Then: The assistant is successfully updated.
          The function returns confirmation of the update along with the updated
          assistant_id and any other relevant information.
    """
    copilot_assistant_agent = AssistantAgent()

    _client = openai.OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
    new_assistant = _client.beta.assistants.create(name="test", model=copilot_assistant_agent.OPENAI_MODEL)

    old_assistant_id = copilot_assistant_agent._assistant.id
    copilot_assistant_agent.execute(QuestionSchema(question="Fake question", assistant_id=new_assistant.id))
    assert copilot_assistant_agent._assistant.id != old_assistant_id
    assert copilot_assistant_agent._assistant.id == new_assistant.id
