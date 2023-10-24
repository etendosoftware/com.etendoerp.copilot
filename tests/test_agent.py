import pytest


def test_open_ai_agent_creation_without_api_key(monkeypatch, set_fake_openai_api_key):
    with monkeypatch.context() as m:
        m.delenv("OPENAI_API_KEY", raising=False)

        with pytest.raises(Exception) as exc_info:
            from copilot.core.agent import create_open_ai_agent

            create_open_ai_agent()

        from copilot.core.exceptions import OpenAIApiKeyNotFound

        assert isinstance(exc_info.value, OpenAIApiKeyNotFound)
        assert str(exc_info.value) == OpenAIApiKeyNotFound.message
