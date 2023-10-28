import os
from http import HTTPStatus
from unittest import mock

from pytest import fixture


@fixture
def mocked_agent_response() -> str:
    return "Mocked agent response"


@fixture
def mocked_agent(mocked_agent_response, monkeypatch):
    mocked_agent_executor = mock.MagicMock()
    mocked_agent_executor.invoke = mock.MagicMock(
        return_value={"input": "fake", "output": mocked_agent_response}
    )

    with monkeypatch.context() as patch_context:
        patch_context.setenv("OPENAI_API_KEY", "fake-openai-key")
        from copilot.core import routes

        routes.langchain_agent_executor = mocked_agent_executor


def test_copilot_question_with_wrong_payload(client):
    response = client.post("/question", json={})
    assert response.status_code == HTTPStatus.BAD_REQUEST
    assert response.json()["detail"][0]["message"] == "Field required"


def test_copilot_question_with_valid_payload(client, mocked_agent, mocked_agent_response):
    response = client.post("/question", json={"question": "What is Etendo?"})
    assert response.status_code == HTTPStatus.OK
    assert "answer" in response.json()
    assert response.json()["answer"] == mocked_agent_response


def test_copilot_get_history(client, set_fake_openai_api_key, mocked_agent, mocked_agent_response):
    # reset file to ensure order
    from copilot.core.local_history import LOCAL_HISTORY_FILEPATH, LocalHistoryRecorder

    os.remove(LOCAL_HISTORY_FILEPATH)
    LocalHistoryRecorder()

    q1 = "What is Etendo 1?"
    response1 = client.post("/question", json={"question": q1})
    assert response1.status_code == HTTPStatus.OK

    q2 = "What is Etendo 2?"
    response2 = client.post("/question", json={"question": q2})
    assert response2.status_code == HTTPStatus.OK

    q3 = "What is Etendo 3?"
    response3 = client.post("/question", json={"question": q3})
    assert response3.status_code == HTTPStatus.OK

    history_response = client.get("/history")
    assert history_response.status_code == HTTPStatus.OK

    history = history_response.json()
    assert len(history) == 3
    assert history[0]["question"] == "What is Etendo 1?"
    assert history[0]["answer"] == "Mocked agent response"

    assert history[1]["question"] == "What is Etendo 2?"
    assert history[1]["answer"] == "Mocked agent response"

    assert history[2]["question"] == "What is Etendo 3?"
    assert history[2]["answer"] == "Mocked agent response"
