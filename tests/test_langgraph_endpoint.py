# test_app.py
import logging
import unittest

import pytest
from fastapi.testclient import TestClient

from copilot.app import app
from copilot.core.agent.agent import AssistantResponse
from copilot.core.routes import serve_async_graph, serve_graph
from copilot.core.schemas import GraphQuestionSchema

logging.basicConfig(level=logging.DEBUG)


class TestGraphEndpoint(unittest.TestCase):
    @pytest.fixture()
    def vcr_config(self):
        return {"record_mode": "rewrite"}

    def setUp(self):
        self.client = TestClient(app)
        self.url = "/graph"
        self.payload = {
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
        }

    def test_graph_endpoint(self):
        response = serve_graph(GraphQuestionSchema.model_validate(self.payload))
        assert response == {'answer': AssistantResponse(response='The capital of France is Paris.',
                                                        conversation_id='d2264c6d-14b8-42bd-9cfc-60a552d433b9',
                                                        message_id=None,
                                                        role=None,
                                                        assistant_id=None)}

    async def test_agraph_endpoint(self):
        response = await serve_async_graph(GraphQuestionSchema.model_validate(self.payload))
        assert response == {'answer': AssistantResponse(response='The capital of France is Paris.',
                                                        conversation_id='d2264c6d-14b8-42bd-9cfc-60a552d433b9',
                                                        message_id=None,
                                                        role=None,
                                                        assistant_id=None)}
