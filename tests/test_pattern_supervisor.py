import unittest

from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.langgraph.patterns import SupervisorPattern
from copilot.core.schemas import GraphQuestionSchema
from langchain_core.runnables.graph import Edge
from langgraph.graph import StateGraph

assistants = [
    {"name": "SQLExpert", "type": "openai-assistant", "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v"},
    {"name": "Ticketgenerator", "type": "openai-assistant", "assistant_id": "asst_7xpJ0v7UxjzWlhkQyPYbseC6"},
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
]

payload1: GraphQuestionSchema = GraphQuestionSchema.model_validate(
    {
        "assistants": assistants,
        "history": [],
        "graph": {
            "stages": [
                {"name": "stage1", "assistants": ["SQLExpert", "Ticketgenerator", "Emojiswriter", "Capo"]}
            ]
        },
        "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
        "question": "What is the capital of France?",
        "local_file_ids": [],
        "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
    }
)

payload2: GraphQuestionSchema = GraphQuestionSchema.model_validate(
    {
        "assistants": assistants,
        "history": [],
        "graph": {
            "stages": [
                {
                    "name": "stage1",
                    "assistants": [
                        "SQLExpert",
                        "Ticketgenerator",
                    ],
                },
                {"name": "stage2", "assistants": ["Emojiswriter", "Capo"]},
            ]
        },
        "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
        "question": "What is the capital of France?",
        "local_file_ids": [],
        "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
    }
)

payload3: GraphQuestionSchema = GraphQuestionSchema.model_validate(
    {
        "assistants": [
            {"name": "SQLExpert", "type": "openai-assistant", "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v"}
        ],
        "history": [],
        "graph": {"stages": [{"name": "stage1", "assistants": ["SQLExpert"]}]},
        "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
        "question": "What is the capital of France?",
        "local_file_ids": [],
        "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
    }
)

payload4: GraphQuestionSchema = GraphQuestionSchema.model_validate(
    {
        "assistants": assistants,
        "history": [],
        "graph": {
            "stages": [
                {
                    "name": "stage1",
                    "assistants": [
                        "SQLExpert",
                        "Ticketgenerator",
                        "Emojiswriter",
                    ],
                },
                {"name": "stage2", "assistants": ["Capo"]},
            ]
        },
        "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
        "question": "What is the capital of France?",
        "local_file_ids": [],
        "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
    }
)

payload5: GraphQuestionSchema = GraphQuestionSchema.model_validate(
    {
        "assistants": [
            {
                "name": "SQLExpert",
                "type": "openai-assistant",
                "assistant_id": "asst_xtery992WunjICv1Pjbrrp4v",
            },
            {
                "name": "Ticketgenerator",
                "type": "openai-assistant",
                "assistant_id": "asst_7xpJ0v7UxjzWlhkQyPYbseC6",
            },
        ],
        "graph": {
            "stages": [
                {
                    "name": "stage1",
                    "assistants": [
                        "SQLExpert",
                    ],
                },
                {"name": "stage2", "assistants": ["Ticketgenerator"]},
            ]
        },
        "conversation_id": "d2264c6d-14b8-42bd-9cfc-60a552d433b9",
        "question": "What is the capital of France?",
        "local_file_ids": [],
        "extra_info": {"auth": {"ETENDO_TOKEN": "eyJhbGciOiJIUzI1NiJ9"}},
    }
)


class TestPatternSupervisor(unittest.TestCase):
    def test_one_stage(self):
        members = MembersUtil().get_members(payload1)
        self.assertEqual(len(members), 4)

        pattern = SupervisorPattern()
        nodes: StateGraph = pattern.construct_nodes(members, payload1.graph)
        keys = [str(k) for k in list(nodes.nodes.keys())]
        expected = ["SQLExpert", "Ticketgenerator", "Emojiswriter", "Capo", "supervisor-stage1", "output"]
        assert len(keys) == len(expected)
        for k in keys:
            assert k in expected

        pattern.connect_graph(payload1.graph, nodes)

        compiled_graph = nodes.compile()
        graph = compiled_graph.get_graph()

        actual_edges = graph.edges
        expected_edges = [
            Edge(source="Capo", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="Emojiswriter", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="SQLExpert", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="Ticketgenerator", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="__start__", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="output", target="__end__", data=None, conditional=False),
            Edge(source="supervisor-stage1", target="SQLExpert", data=None, conditional=True),
            Edge(source="supervisor-stage1", target="Ticketgenerator", data=None, conditional=True),
            Edge(source="supervisor-stage1", target="Emojiswriter", data=None, conditional=True),
            Edge(source="supervisor-stage1", target="Capo", data=None, conditional=True),
            Edge(source="supervisor-stage1", target="output", data="FINISH", conditional=True),
        ]

        # Convertir listas de Edge a listas de tuplas
        actual_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in actual_edges]
        expected_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in expected_edges]

        # Comparar las listas de tuplas
        self.assert_equals_edges_lists(actual_edges_as_tuples, expected_edges_as_tuples)

    def test_two_stages(self):
        members = MembersUtil().get_members(payload2)
        self.assertEqual(len(members), 4)

        pattern = SupervisorPattern()
        nodes: StateGraph = pattern.construct_nodes(members, payload2.graph)
        keys = [str(k) for k in list(nodes.nodes.keys())]
        expected = [
            "SQLExpert",
            "Ticketgenerator",
            "Emojiswriter",
            "Capo",
            "supervisor-stage1",
            "supervisor-stage2",
            "output",
        ]
        assert len(keys) == len(expected)
        for k in keys:
            assert k in expected

        pattern.connect_graph(payload2.graph, nodes)

        compiled_graph = nodes.compile()
        graph = compiled_graph.get_graph()

        actual_edges = graph.edges
        expected_edges = [
            Edge(source="Capo", target="supervisor-stage2", data=None, conditional=False),
            Edge(source="Emojiswriter", target="supervisor-stage2", data=None, conditional=False),
            Edge(source="SQLExpert", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="SQLExpert", target="supervisor-stage2", data=None, conditional=False),
            Edge(source="Ticketgenerator", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="Ticketgenerator", target="supervisor-stage2", data=None, conditional=False),
            Edge(source="__start__", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="output", target="__end__", data=None, conditional=False),
            Edge(source="supervisor-stage1", target="SQLExpert", data=None, conditional=True),
            Edge(source="supervisor-stage1", target="Ticketgenerator", data=None, conditional=True),
            Edge(source="supervisor-stage2", target="Emojiswriter", data=None, conditional=True),
            Edge(source="supervisor-stage2", target="Capo", data=None, conditional=True),
            Edge(source="supervisor-stage2", target="output", data="FINISH", conditional=True),
        ]

        # Convertir listas de Edge a listas de tuplas
        actual_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in actual_edges]
        expected_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in expected_edges]

        # Comparar las listas de tuplas
        self.assert_equals_edges_lists(actual_edges_as_tuples, expected_edges_as_tuples)

    def test_one_node(self):
        members = MembersUtil().get_members(payload3)
        self.assertEqual(len(members), 1)

        pattern = SupervisorPattern()
        nodes: StateGraph = pattern.construct_nodes(members, payload3.graph)
        keys = [str(k) for k in list(nodes.nodes.keys())]
        expected = ["SQLExpert"]
        assert len(keys) == len(expected)
        for k in keys:
            assert k in expected

        pattern.connect_graph(payload3.graph, nodes)

        compiled_graph = nodes.compile()
        graph = compiled_graph.get_graph()

        actual_edges = graph.edges
        expected_edges = [
            Edge(source="SQLExpert", target="__end__", data=None, conditional=False),
            Edge(source="__start__", target="SQLExpert", data=None, conditional=False),
        ]

        # Convertir listas de Edge a listas de tuplas
        actual_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in actual_edges]
        expected_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in expected_edges]

        # Comparar las listas de tuplas
        self.assert_equals_edges_lists(actual_edges_as_tuples, expected_edges_as_tuples)

    def test_one_node_stage1(self):
        members = MembersUtil().get_members(payload4)
        self.assertEqual(len(members), 4)

        pattern = SupervisorPattern()
        nodes: StateGraph = pattern.construct_nodes(members, payload4.graph)
        keys = [str(k) for k in list(nodes.nodes.keys())]
        expected = ["SQLExpert", "Ticketgenerator", "Emojiswriter", "Capo", "supervisor-stage1"]
        assert len(keys) == len(expected)
        for k in keys:
            assert k in expected

        pattern.connect_graph(payload4.graph, nodes)

        compiled_graph = nodes.compile()
        graph = compiled_graph.get_graph()

        actual_edges = graph.edges
        expected_edges = [
            Edge(source="Capo", target="__end__", data=None, conditional=False),
            Edge(source="Emojiswriter", target="Capo", data=None, conditional=False),
            Edge(source="SQLExpert", target="Capo", data=None, conditional=False),
            Edge(source="Ticketgenerator", target="Capo", data=None, conditional=False),
            Edge(source="__start__", target="supervisor-stage1", data=None, conditional=False),
            Edge(source="supervisor-stage1", target="SQLExpert", data=None, conditional=True),
            Edge(source="supervisor-stage1", target="Ticketgenerator", data=None, conditional=True),
            Edge(source="supervisor-stage1", target="Emojiswriter", data=None, conditional=True),
        ]

        # Convertir listas de Edge a listas de tuplas
        actual_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in actual_edges]
        expected_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in expected_edges]

        # Comparar las listas de tuplas
        self.assert_equals_edges_lists(actual_edges_as_tuples, expected_edges_as_tuples)

    def test_one_node_each_stage(self):
        members = MembersUtil().get_members(payload5)
        self.assertEqual(len(members), 2)

        pattern = SupervisorPattern()
        nodes: StateGraph = pattern.construct_nodes(members, payload5.graph)
        keys = [str(k) for k in list(nodes.nodes.keys())]
        expected = ["SQLExpert", "Ticketgenerator"]
        assert len(keys) == len(expected)
        for k in keys:
            assert k in expected

        pattern.connect_graph(payload5.graph, nodes)

        compiled_graph = nodes.compile()
        graph = compiled_graph.get_graph()

        actual_edges = graph.edges
        expected_edges = [
            Edge(source="SQLExpert", target="Ticketgenerator", data=None, conditional=False),
            Edge(source="Ticketgenerator", target="__end__", data=None, conditional=False),
            Edge(source="__start__", target="SQLExpert", data=None, conditional=False),
        ]

        # Convertir listas de Edge a listas de tuplas
        actual_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in actual_edges]
        expected_edges_as_tuples = [(e.source, e.target, e.data, e.conditional) for e in expected_edges]

        # Comparar las listas de tuplas
        self.assert_equals_edges_lists(actual_edges_as_tuples, expected_edges_as_tuples)

    def assert_equals_edges_lists(self, actual_edges_as_tuples, expected_edges_as_tuples):
        """
        Compares two lists of tuples representing graph edges and checks that all expected edges
        are present in the actual edges.

        Args:
            actual_edges_as_tuples (list[tuple]): List of tuples representing the actual graph edges.
            expected_edges_as_tuples (list[tuple]): List of tuples representing the expected graph edges.

        Raises:
            AssertionError: If any expected edge is not found in the actual edges.
        """

        for exp in expected_edges_as_tuples:
            self.assertIn(exp, actual_edges_as_tuples, f"Expected edge {exp} not " f"found in actual edges.")
