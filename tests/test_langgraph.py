import unittest
from unittest.mock import patch, MagicMock

from copilot.core.langgraph.copilot_langgraph import CopilotLangGraph
from copilot.core.langgraph.patterns.base_pattern import GraphMember


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
        pattern.connect_graph = MagicMock()
        pattern.compile.return_value = MagicMock()

        # Creating instance
        instance = CopilotLangGraph(members, assistant_graph, pattern)

        # Assertions to ensure correct initialization
        self.assertIsNotNone(instance._graph)
        pattern.construct_nodes.assert_called_once_with(members, assistant_graph)
        pattern.connect_graph.assert_called_once_with(assistant_graph, pattern.construct_nodes.return_value)

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


if __name__ == '__main__':
    unittest.main()