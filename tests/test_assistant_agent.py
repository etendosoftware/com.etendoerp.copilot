from unittest.mock import patch

from copilot.core.schemas import QuestionSchema
from copilot.core.utils.models import get_openai_client


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
