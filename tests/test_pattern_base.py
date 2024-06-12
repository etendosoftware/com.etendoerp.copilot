import unittest
from unittest.mock import MagicMock

from langchain_core.runnables.graph import Edge
from langgraph.graph import StateGraph

from copilot.core.agent.langgraph_agent import LanggraphAgent
from copilot.core.langgraph.patterns import SupervisorPattern
from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.schemas import GraphQuestionSchema

payload: GraphQuestionSchema = GraphQuestionSchema.model_validate({
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
        {
            "name": "Capo",
            "type": "langchain",
            "assistant_id": "FD8ss485BBE72D4B69BED2626D72114834",
            "tools": [],
            "provider": "openai",
            "model": "gpt-4o",
            "system_prompt": " Sos un argentino que redacta respuestas en cordobez."
        }
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

class TestPatternBase(unittest.TestCase):

    def test_initialization(self):

        invoke_model_langchain = MagicMock()
        invoke_model_openai = MagicMock()

        agent = LanggraphAgent()
        members = agent.get_members(invoke_model_langchain, invoke_model_openai, payload)
        self.assertEqual(len(members), 4)

        pattern = BasePattern()
        nodes: StateGraph = pattern.construct_nodes(members, payload.graph)
        keys = [str(k) for k in list(nodes.nodes.keys())]
        expected = ['SQLExpert', 'Ticketgenerator', 'Emojiswriter', 'Capo']
        assert len(keys) == len(expected)
        for k in keys:
            assert k in expected

        with self.assertRaises(NotImplementedError):
            pattern.connect_graph(payload.graph, nodes)
