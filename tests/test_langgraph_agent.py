import unittest
from unittest.mock import AsyncMock, patch

import pytest

from copilot.core.agent.langgraph_agent import LanggraphAgent
from copilot.core.langgraph.copilot_langgraph import CopilotLangGraph
from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.schemas import GraphQuestionSchema


class TestLanggraphAgent(unittest.TestCase):


    def get_graph_question(self):
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
    async def test_aexecute(self):
        langgraph_agent = LanggraphAgent()
        responses = []
        async for response in langgraph_agent.aexecute(self.get_graph_question()):
            responses.append(response)

        assert len(responses) > 0
        assert responses[-1].response is not None
        assert responses[-1].conversation_id == "test_conversation_async"

    @pytest.mark.asyncio
    async def test_aexecute_with_recording(self):
        langgraph_agent = LanggraphAgent()
        responses = []
        async for response in langgraph_agent.aexecute(self.get_graph_question()):
            responses.append(response)

        assert len(responses) > 0
        assert responses[-1].response is not None
        assert responses[-1].conversation_id == "test_conversation_async_with_recording"

    @pytest.mark.asyncio
    async def test_aexecute2(self):
        # Mock the necessary components
        # Instantiate the agent
        agent = LanggraphAgent()
        # Mock the methods that are called within aexecute
        with patch.object(MembersUtil, 'get_members', return_value=[]), \
                patch('path.to.AsyncSqliteSaver.from_conn_string', return_value=AsyncMock()), \
                patch.object(CopilotLangGraph, 'invoke', return_value=AsyncMock()), \
                patch('path.to.LanggraphAgent._configured_tools', new_callable=AsyncMock):
            # Record the responses
            responses = []
            async for response in agent.aexecute(self.get_graph_question()):
                responses.append(response)
            # Now you can assert the responses as per your expectation
            assert len(responses) > 0
            assert responses[0].response == "Expected response"
