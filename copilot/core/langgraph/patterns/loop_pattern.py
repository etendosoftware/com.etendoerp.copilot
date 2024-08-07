from langgraph.graph import StateGraph, END
from typing import Literal
from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.langgraph.special_nodes.supervisor_node import SupervisorNode


class LoopPattern(BasePattern):
    _first = None
    def should_continue(self, state):
        """Return the next node to execute."""
        last_message = state["messages"][-1]
        # If there is no function call, then we finish
        if not last_message.type == "HumanMessage":
            if not last_message.tool_calls:
                return "__end__"
        # Otherwise if there is, we continue
        return self._first

    def get_nodes(self, assistant_graph):
        assistants = []
        for stage in assistant_graph.stages:
            for assistant_name in stage.assistants:
                assistants.append(assistant_name)
        return assistants

    def connect_graph(self, assistant_graph, workflow):
        workflow.set_entry_point("supervisor")
        workflow.add_edge("supervisor", END)
        conditional_map = self.get_nodes(assistant_graph)
        for node in conditional_map:
            workflow.add_edge(node, "check_continue")

        workflow.add_conditional_edges("supervisor", lambda x: x["next"], conditional_map)
        workflow.add_conditional_edges("check_continue", lambda x: x["next"], ["supervisor", END])
        workflow.add_edge("check_continue", "supervisor")

    def construct_nodes(self, members, assistant_graph):
        workflow = super().construct_nodes(members, assistant_graph)
        supervisor_chain = SupervisorNode().build(self.get_nodes(assistant_graph))
        workflow.add_node("supervisor", supervisor_chain)

        checker_prompt = (
            "You are a supervisor tasked with the end of a response cycle between user and the {members}. "
            " Always respond with FINISH ."
        )
        checker_node = SupervisorNode().build(["supervisor"], checker_prompt)
        workflow.add_node("check_continue", checker_node)
        return workflow
