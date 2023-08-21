from http import HTTPStatus
from unittest import mock

from copilot.core import agent
from pytest import fixture


@fixture
def mocked_agent_response() -> str:
    return "Mocked agent response"


@fixture
def mocked_agent(mocked_agent_response):
    mocked_chat = mock.MagicMock(return_value=mocked_agent_response)
    mock_response = mock.MagicMock(chat=mocked_chat)
    agent.open_ai_agent = mock_response


def test_copilot_question_with_wrong_payload(client):
    response = client.post("/question", content_type="application/json", json={})
    assert response.status_code == HTTPStatus.BAD_REQUEST
    assert "validation error for QuestionSchema" in response.json.get("description")


def test_copilot_question_with_valid_payload(client, mocked_agent, mocked_agent_response):
    response = client.post("/question", content_type="application/json", json={"question": "What is Etendo?"})
    assert response.status_code == HTTPStatus.OK
    assert "answer" in response.json
    assert response.json["answer"] == mocked_agent_response
