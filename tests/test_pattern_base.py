import unittest

from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.schemas import GraphQuestionSchema
from langgraph.graph import StateGraph

payload: GraphQuestionSchema = GraphQuestionSchema.model_validate(
    {
        "assistants": [
            {
                "name": "SQLExpert",
                "type": "multimodel-assistant",
                "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v",
                "provider": "openai",
                "model": "gpt-4o",
            },
            {
                "name": "Ticketgenerator",
                "type": "langchain",
                "assistant_id": "TEST_ID_2",
                "tools": [],
                "provider": "openai",
                "model": "gpt-4o",
                "system_prompt": "You are a helpful ticket generator assistant.",
            },
            {
                "name": "Emojiswriter",
                "type": "langchain",
                "assistant_id": "FD8485BBE72D4B69BED2626D72114834",
                "tools": [],
                "provider": "openai",
                "model": "gpt-4o",
                "system_prompt": "Eres un experto en hacer redacciones usando emojis. Si recibes un texto, lo redactaras de manera muy amigable y usando muchos emojis\n",
            },
            {
                "name": "Capo",
                "type": "langchain",
                "assistant_id": "FD8ss485BBE72D4B69BED2626D72114834",
                "tools": [],
                "provider": "openai",
                "model": "gpt-4o",
                "system_prompt": " Sos un argentino que redacta respuestas en cordobez.",
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


class TestPatternBase(unittest.IsolatedAsyncioTestCase):
    async def test_initialization(self):
        members = await MembersUtil().get_members(payload)
        self.assertEqual(len(members), 4)

        pattern = BasePattern()
        nodes: StateGraph = await pattern.construct_nodes(members, payload.graph)
        keys = [str(k) for k in list(nodes.nodes.keys())]
        expected = ["SQLExpert", "Ticketgenerator", "Emojiswriter", "Capo"]
        assert len(keys) == len(expected)
        for k in keys:
            assert k in expected

        with self.assertRaises(NotImplementedError):
            pattern.connect_graph(payload.graph, nodes)
