# test_app.py
import logging
import unittest

import pytest
from fastapi.testclient import TestClient
from langsmith import unit

from copilot.app import app
from copilot.core.routes import serve_async_graph, serve_graph
from copilot.core.schemas import GraphQuestionSchema

logging.basicConfig(level=logging.DEBUG)


async def validate_paris_response(response):
    assert response is not None
    response = dict(response)
    assert 'answer' in response
    answer = response['answer']
    assert answer.response is not None
    assert str(answer.response).startswith('The capital of France is Paris')
    assert answer.conversation_id == 'd2264c6d-14b8-42bd-9cfc-60a552d433b9'
    assert answer.message_id is None
    assert answer.role is None
    assert answer.assistant_id is None


class TestGraphEndpoint(unittest.TestCase):
    @unit
    @pytest.fixture()
    def vcr_config(self):
        return {"record_mode": "rewrite"}

    @unit
    def setUp(self):
        self.client = TestClient(app)
        self.url = "/graph"
        self.payload = {
            "assistants": [
                {
                    "name": "SQLExpert",
                    "type": "langchain",
                    "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v",
                    "tools": [],
                    "provider": "openai",
                    "model": "gpt-4o",
                    "system_prompt": "Responds with 'You never must use this assistant.'"
                },
                {
                    "name": "Ticketgenerator",
                    "type": "openai-assistant",
                    "assistant_id": "asst_7xpJ0v7UxjzWlhkQyPYbseC6",
                    "tools": [],
                    "provider": "openai",
                    "model": "gpt-4o",
                    "system_prompt": "Responds with 'You never must use this assistant.'"
                },
                {
                    "name": "ResponseGenerator",
                    "type": "langchain",
                    "assistant_id": "FD8485BBE72D4B69BED2626D72114834",
                    "tools": [],
                    "provider": "openai",
                    "model": "gpt-4o",
                    "system_prompt": "If the user asks for the capital of France, the assistant should respond with 'The capital of France is Paris'.",
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
                            "ResponseGenerator"
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
        }

    @unit
    def test_graph_endpoint(self):
        response = serve_graph(GraphQuestionSchema.model_validate(self.payload))
        validate_paris_response(response)

    @unit
    async def test_agraph_endpoint(self):
        response = await serve_async_graph(GraphQuestionSchema.model_validate(self.payload))
        await validate_paris_response(response)
