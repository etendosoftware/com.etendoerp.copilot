import pytest
import asyncio
from unittest.mock import patch, AsyncMock, MagicMock

from httpx import AsyncClient
from fastapi.testclient import TestClient
from fastapi import FastAPI
from starlette.responses import StreamingResponse
from copilot.app import app
from copilot.core.routes import _serve_question_async, event_stream, serve_async_question, _serve_agraph, core_router
from copilot.core.schemas import QuestionSchema, GraphQuestionSchema


# Simulación de QuestionSchema
@pytest.fixture
def question():
    return QuestionSchema(
        type="test_type",
        question="What is the capital of France?",
        assistant_id="test_assistant_id",
        conversation_id="test_conversation_id",
        file_ids=["file1", "file2"],
        extra_info={"key": "value"}
    )

# Setup de FastAPI para pruebas
app = FastAPI()
app.include_router(core_router)
client = TestClient(app)

@pytest.mark.asyncio
async def test_serve_question_async(question):
    with patch('copilot.core.routes._initialize_agent') as mock_initialize_agent, \
            patch('copilot.core.routes.gather_responses', new_callable=AsyncMock) as mock_gather_responses, \
            patch('copilot.core.routes._response') as mock_response:
        mock_initialize_agent.return_value = ("test_type", MagicMock())
        mock_response.side_effect = lambda x: x  # Passthrough

        async def mock_gather(copilot_agent, question, queue):
            await queue.put("response1")
            await queue.put("response2")
            await queue.put(None)

        mock_gather_responses.side_effect = mock_gather

        responses = []
        async for response in _serve_question_async(question):
            responses.append(response)

        assert responses == ["response1", "response2"]


@pytest.mark.asyncio
async def test_serve_async_question():
    async with AsyncClient(app=app, base_url="http://test") as ac:
        question = QuestionSchema(question="What is the meaning of life?")
        response = await ac.post("/aquestion", json=question.dict())

        assert response.status_code == 200
        async for data in response.aiter_text():
            print(data)  # You can add more assertions based on the expected output


@pytest.fixture
def graph_question_payload():
    return GraphQuestionSchema.model_validate({
        "assistants": [
            {
                "name": "SQLExpert",
                "type": "openai-assistant",
                "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v"
            },
            {
                "name": "Ticketgenerator",
                "type": "openai-assistant",
                "assistant_id": "asst_7xpJ0v7UxjzWlhkQyPYbseC6"
            },
            {
                "name": "Emojiswriter",
                "type": "langchain",
                "assistant_id": "FD8485BBE72D4B69BED2626D72114834",
                "tools": [],
                "provider": "openai",
                "model": "gpt-4o",
                "system_prompt": "Eres un experto en hacer redacciones usando emojis. Si recibes un texto, lo redactaras de manera muy amigable y usando muchos emojis\n"
            },
        ],
        "history": [],
        "graph": {
            "stages": [
                {
                    "name": "stage1",
                    "assistants": [
                        "SQLExpert",
                        "Ticketgenerator",
                        "Emojiswriter"
                    ]
                }
            ]
        },
        "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
        "question": "What is the capital of France?",
        "local_file_ids": [],
        "extra_info": {
            "auth": {
                "ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"
            }
        }
    })


@pytest.mark.asyncio
@patch('copilot.core.agent.langgraph_agent.LanggraphAgent')
@patch('copilot.core.routes.gather_responses', new_callable=AsyncMock)
async def test_serve_async_graph(mock_gather_responses, MockLanggraphAgent, graph_question_payload):
    # Mock the LanggraphAgent
    mock_agent = MockLanggraphAgent.return_value

    class Output:
        response: str
        conversation_id: str
        role: str
    class Response:
       output:Output

    # Mock the gather_responses function to put mock data into the queue
    async def mock_gather(*args, **kwargs):

        output = Output()
        output.response = "Hi!"
        output.conversation_id = "123"
        output.role = "bot"
        response = Response()
        response.output = output

        await args[2].put(response)
        await args[2].put(None)

    mock_gather_responses.side_effect = mock_gather

    # Perform the test request
    response = client.post("/agraph", json=graph_question_payload.dict())

    # Validate the response
    assert response.status_code == 200
    assert 'data: {"answer": {"response": "Hi!", "conversation_id": "123", "role": "bot"}}\n' in response.text


def test_serve_agraph_exception_handling(graph_question_payload):
    with patch('copilot.core.agent.langgraph_agent.LanggraphAgent') as MockLanggraphAgent:
        mock_agent = MockLanggraphAgent.return_value
        mock_agent.process_question.side_effect = Exception("Test Exception")

        graph_question_payload.assistants = None
        _serve_agraph(graph_question_payload)

if __name__ == "__main__":
    pytest.main()