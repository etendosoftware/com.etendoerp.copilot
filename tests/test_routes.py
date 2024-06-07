import os
from http import HTTPStatus
from unittest import mock

from pytest import fixture

from copilot.core.agent.agent import AssistantResponse


@fixture
def mocked_agent_response() -> AssistantResponse:
    return AssistantResponse(response="Mocked agent response", conversation_id="mocked_conversation_id")


@fixture
def mocked_agent(mocked_agent_response, monkeypatch):
    from copilot.core.agent import AgentResponse

    mocked_agent_executor = mock.MagicMock()
    mocked_agent_executor.execute = mock.MagicMock(
        return_value=AgentResponse(input="fake", output=mocked_agent_response)
    )

    with monkeypatch.context() as patch_context:
        patch_context.setenv("OPENAI_API_KEY", os.getenv("OPENAI_API_KEY"))
        patch_context.setenv("AGENT_TYPE", "langchain")
        from copilot.core import routes

        routes.select_copilot_agent = mocked_agent_executor


def test_copilot_question_with_wrong_payload(client):
    response = client.post("/question", json={})
    assert response.status_code == HTTPStatus.BAD_REQUEST
    assert response.json()["detail"][0]["message"] == "Field required"


def test_copilot_question_with_valid_payload(client, mocked_agent, mocked_agent_response):
    response = client.post("/question", json={"question": "What is Etendo?", "provider": "langchain", "model": "gpt-4o"})
    assert response.status_code == HTTPStatus.OK
    assert "answer" in response.json()
    assert response.json()["answer"] == {}
