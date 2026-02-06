import json
import unittest
from unittest.mock import MagicMock, patch

import pytest
from copilot.app import app
from copilot.core.agent.langgraph_agent import LanggraphAgent
from copilot.core.langgraph.copilot_langgraph import CopilotLangGraph
from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.langgraph.patterns import SupervisorPattern
from copilot.core.schema.graph_member import GraphMember
from copilot.core.schemas import GraphQuestionSchema
from fastapi.testclient import TestClient
from langgraph.checkpoint.memory import MemorySaver

client = TestClient(app)


@pytest.fixture
def mock_langgraph_agent():
    with patch("copilot.core.agent.langgraph_agent.LanggraphAgent") as mock_agent:
        instance = mock_agent.return_value
        instance.execute.return_value = MagicMock(output="Paris")
        yield mock_agent


@pytest.fixture
def graph_question_payload():
    return GraphQuestionSchema.model_validate(
        {
            "assistants": [
                {
                    "name": "SQLExpert",
                    "type": "langchain",
                    "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v",
                    "tools": [],
                    "provider": "openai",
                    "model": "gpt-4o",
                    "system_prompt": "You are a SQL expert assistant.",
                    "description": "Its a SQL expert assistant",
                },
                {
                    "name": "Ticketgenerator",
                    "type": "langchain",
                    "assistant_id": "asst_7xpJ0v7UxjzWlhkQyPYbseC6",
                    "tools": [],
                    "provider": "openai",
                    "model": "gpt-4o",
                    "system_prompt": "You are a ticket generator assistant.",
                    "description": "Its a ticket generator assistant.",
                },
                {
                    "name": "Emojiswriter",
                    "type": "langchain",
                    "assistant_id": "FD8485BBE72D4B69BED2626D72114834",
                    "tools": [],
                    "provider": "openai",
                    "model": "gpt-4o",
                    "system_prompt": "Eres un experto en hacer redacciones usando emojis. Si recibes un texto, lo redactaras de manera muy amigable y usando muchos emojis\n",
                    "description": "Its a emojis writer assistant.",
                },
            ],
            "history": [],
            "graph": {
                "stages": [{"name": "stage1", "assistants": ["SQLExpert", "Ticketgenerator", "Emojiswriter"]}]
            },
            "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
            "question": "What is the capital of France?",
            "local_file_ids": [],
            "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
        }
    )


class TestCopilotLangGraph(unittest.IsolatedAsyncioTestCase):
    @patch("copilot.core.schemas.AssistantGraph")
    @patch("copilot.core.langgraph.patterns.SupervisorPattern")
    @patch("copilot.core.schema.graph_member.GraphMember")
    @patch("copilot.core.schema.graph_member.GraphMember")
    async def test_initialization(
        self, mock_supervisor_pattern, mock_assistant_graph, mock_member_1, mock_member_2
    ):
        # Mocking the necessary components
        members = [GraphMember("member1", mock_member_1), GraphMember("member2", mock_member_2)]
        assistant_graph = mock_assistant_graph()
        pattern = mock_supervisor_pattern()

        # Mock construct_nodes as an async function
        async def mock_construct_nodes(*args, **kwargs):
            return MagicMock()

        pattern.construct_nodes = mock_construct_nodes
        mock_connect_graph = MagicMock()
        mock_connect_graph.return_value = ["Hello"]
        pattern.connect_graph = mock_connect_graph
        pattern.compile.return_value = MagicMock()

        memory = MemorySaver()
        # Creating instance
        instance = await CopilotLangGraph.create(members, assistant_graph, pattern, memory)

        # Assertions to ensure correct initialization
        self.assertIsNotNone(instance._graph)
        # pattern.construct_nodes.assert_called_once_with(members, assistant_graph, None) # Cant use assert_called_once_with easily with async wrapper
        pattern.connect_graph.assert_called_once()

    @patch("langchain_core.messages.HumanMessage")
    @patch("copilot.core.langgraph.patterns.SupervisorPattern")
    # @patch("langgraph.graph.graph.CompiledGraph")
    async def test_invoke(self, mock_human_message, mock_supervisor_pattern):  # , MockCompiledGraph
        # Mocking components

        members = ["member1", "member2"]
        assistant_graph = MagicMock()
        pattern = mock_supervisor_pattern()

        async def mock_construct_nodes(*args, **kwargs):
            return MagicMock()

        pattern.construct_nodes = mock_construct_nodes
        pattern.connect_graph.return_value = None
        graph = MagicMock()
        graph.stream.return_value = [{"__end__": False, "content": "Test message"}]
        pattern.compile.return_value = graph

        memory = MemorySaver()
        # Creating instance
        await CopilotLangGraph.create(members, assistant_graph, pattern, memory)


@pytest.mark.asyncio
async def test_copilot_lang_graph(graph_question_payload):
    pattern = SupervisorPattern()

    members = await MembersUtil().get_members(graph_question_payload)
    assert len(members) == 3

    memory = MemorySaver()
    await CopilotLangGraph.create(
        members, graph_question_payload.graph, pattern, memory, graph_question_payload
    )


@patch("copilot.core.agent.langgraph_agent.LanggraphAgent.execute")
def test_serve_question_success(mock_execute, graph_question_payload):
    mock_execute.return_value = MagicMock(
        output={
            "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
            "message_id": None,
            "response": "Paris.",
            "role": None,
        }
    )
    response = client.post("/graph", json=json.loads(graph_question_payload.json()))
    assert response.status_code == 200
    response_json = response.json()
    assert "answer" in response_json
    answer = response_json["answer"]
    assert answer["conversation_id"] == "d2264c6d-14b8-42bd-9cfc-60a552d433b9"
    assert answer["message_id"] is None
    assert "Paris" in answer["response"]
    assert answer["role"] is None


def test_initialization(mock_langgraph_agent):
    agent = LanggraphAgent()
    assert agent is not None
