import os
from http import HTTPStatus
from typing import Dict, Type
from unittest import mock
from unittest.mock import MagicMock, patch

from copilot.core import core_router
from copilot.core.agent import AssistantAgent
from copilot.core.agent.agent import AssistantResponse
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper
from fastapi import FastAPI
from fastapi.testclient import TestClient
from langsmith import unit
from pytest import fixture

app = FastAPI()
app.include_router(core_router)

client = TestClient(app)


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
    response = client.post(
        "/question", json={"question": "What is Etendo?", "provider": "langchain", "model": "gpt-4o"}
    )
    assert response.status_code == HTTPStatus.OK
    assert "answer" in response.json()
    assert response.json()["answer"] == {}


@fixture
def mock_langchain_agent():
    mock_agent = MagicMock()
    tools = []

    class DummyInput(ToolInput):
        query: str = ToolField(description="query to look up")

    class Tool1(ToolWrapper):
        name: str = "HelloWorldTool"
        description: str = "This is the classic HelloWorld tool implementation."
        args_schema: Type[ToolInput] = DummyInput
        return_direct: bool = False

        def run(self, input_params: Dict = None, *args, **kwarg) -> str:
            return {"message": "a"}

    tools.append(Tool1())
    mock_agent.get_tools.return_value = tools
    return mock_agent


@fixture
def mock_chat_history():
    return {"messages": ["Hello", "How are you?"]}


@fixture
def mock_assistant_agent():
    mock_agent = MagicMock(spec=AssistantAgent)
    mock_agent.get_assistant_id.return_value = "assistant_12345"
    return mock_agent


@patch("copilot.core.routes.select_copilot_agent")
def test_serve_tools(mock_select_copilot_agent, mock_langchain_agent):
    mock_select_copilot_agent.return_value = mock_langchain_agent
    response = client.get("/tools")
    assert response.status_code == 200
    response_json = response.json()
    assert {
        "answer": {
            "HelloWorldTool": {
                "description": "This is the classic HelloWorld tool implementation.",
                "parameters": {
                    "query": {"description": "query to look up", "title": "Query", "type": "string"}
                },
            }
        }
    } == response_json
