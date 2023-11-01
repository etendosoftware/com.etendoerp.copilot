from unittest.mock import Mock

import pytest


def test_open_ai_agent_creation_without_api_key(monkeypatch, set_fake_openai_api_key):
    with pytest.raises(Exception) as exc_info:
        from copilot.core import agent

        agent.OPENAI_API_KEY = None
        agent.get_langchain_agent_executor(open_ai_model=Mock())

    from copilot.core.exceptions import OpenAIApiKeyNotFound

    assert isinstance(exc_info.value, OpenAIApiKeyNotFound)
    assert str(exc_info.value) == OpenAIApiKeyNotFound.message
