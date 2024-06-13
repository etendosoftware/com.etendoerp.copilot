import unittest
from unittest.mock import patch, MagicMock

from langchain_core.messages import HumanMessage
from langgraph.pregel.io import AddableUpdatesDict

from copilot.core.agent.langgraph_agent import LanggraphAgent
from copilot.core.langgraph.copilot_langgraph import CopilotLangGraph
from copilot.core.langgraph.patterns import SupervisorPattern
from copilot.core.langgraph.patterns.base_pattern import GraphMember
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


class TestCopilotLangGraph(unittest.TestCase):

    @patch('copilot.core.schemas.AssistantGraph')
    @patch('copilot.core.langgraph.patterns.SupervisorPattern')
    @patch('copilot.core.langgraph.patterns.base_pattern.GraphMember')
    @patch('copilot.core.langgraph.patterns.base_pattern.GraphMember')
    def test_initialization(self, MockSupervisorPattern, MockAssistantGraph, MockMember1, MockMember2):
        # Mocking the necessary components
        members = [GraphMember('member1', MockMember1), GraphMember('member2', MockMember2)]
        assistant_graph = MockAssistantGraph()
        pattern = MockSupervisorPattern()
        pattern.construct_nodes.return_value = MagicMock()
        mock_connect_graph = MagicMock()
        mock_connect_graph.return_value = ["Hello"]
        pattern.connect_graph = mock_connect_graph
        pattern.compile.return_value = MagicMock()

        # Creating instance
        instance = CopilotLangGraph(members, assistant_graph, pattern)

        # Assertions to ensure correct initialization
        self.assertIsNotNone(instance._graph)
        pattern.construct_nodes.assert_called_once_with(members, assistant_graph)
        pattern.connect_graph.assert_called_once_with(assistant_graph, pattern.construct_nodes.return_value)

        instance.invoke("Test message", "Test thread_id")

    @patch('langchain_core.messages.HumanMessage')
    @patch('copilot.core.langgraph.patterns.SupervisorPattern')
    @patch('langgraph.graph.graph.CompiledGraph')
    def test_invoke(self, MockHumanMessage, MockSupervisorPattern, MockCompiledGraph):
        # Mocking components
        members = ['member1', 'member2']
        assistant_graph = MagicMock()
        pattern = MockSupervisorPattern()
        pattern.construct_nodes.return_value = MagicMock()
        pattern.connect_graph.return_value = None
        graph = MagicMock()
        graph.stream.return_value = [{"__end__": False, "content": "Test message"}]
        pattern.compile.return_value = graph

        # Creating instance
        instance = CopilotLangGraph(members, assistant_graph, pattern)

    @patch('copilot.core.agent.assistant_agent.AssistantAgent')
    @patch('copilot.core.agent.langgraph_agent.LanggraphAgent.get_assistant_agent')
    def test_payload(self, mockAssistantAgent, mock_get_assistant_agent):
        agent = LanggraphAgent()
        mock_get_assistant_agent.return_value = mockAssistantAgent

    def test_copilot_lang_graph(self):
        invoke_model_langchain = MagicMock()
        invoke_model_openai = MagicMock()
        pattern = SupervisorPattern()

        agent = LanggraphAgent()
        members = agent.get_members(invoke_model_langchain, invoke_model_openai, payload)
        self.assertEqual(len(members), 3)

        assistant_graph = MagicMock()

        graph = CopilotLangGraph(members, payload.graph, pattern)


if __name__ == '__main__':
    unittest.main()
