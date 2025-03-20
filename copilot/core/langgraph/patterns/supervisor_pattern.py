from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.langgraph.special_nodes.output_node import OutputNode
from copilot.core.langgraph.special_nodes.supervisor_node import (
    SupervisorNode,
    get_supervisor_system_prompt,
    get_supervisor_temperature,
)
from langgraph.graph import END


class SupervisorPattern(BasePattern):
    def construct_nodes(self, members, assistant_graph=None, full_question=None):
        workflow = super().construct_nodes(members, assistant_graph)
        for stage in assistant_graph.stages:
            members_names = []
            members_description = []

            for assistant_name in stage.assistants:
                members_names.append(assistant_name)

                description = MembersUtil().get_assistant_supervisor_info(assistant_name, full_question)
                members_description.append(description)
            if len(members_names) > 1:
                # if stage is not assistant_graph.stages[-1]:
                # connect with next supervisor
                members_names.append("FINISH")
                members_description.append(
                    "End of response cycle. The instruction"
                    " must contain the exactly message "
                    "to be sent to the user."
                )
                sv_temperature = get_supervisor_temperature(full_question)
                sv_prompt = get_supervisor_system_prompt(full_question)
                supervisor_chain = SupervisorNode().build(
                    members_names=members_names,
                    members_descriptions=members_description,
                    system_prompt=sv_prompt,
                    temperature=sv_temperature,
                )
                workflow.add_node("supervisor-" + stage.name, supervisor_chain)
        if len(assistant_graph.stages[-1].assistants) > 1:
            workflow.add_node("output", OutputNode().build(temperature=sv_temperature).nodes["agent"])
        return workflow

    def connect_graph(self, assistant_graph, workflow):
        for i in range(0, len(assistant_graph.stages)):
            stage = assistant_graph.stages[i]
            if len(stage.assistants) == 1:
                for assistant_name in stage.assistants:
                    if i < len(assistant_graph.stages) - 1:
                        next_stage = assistant_graph.stages[i + 1]
                        if len(next_stage.assistants) == 1:
                            workflow.add_edge(assistant_name, next_stage.assistants[0])
                        else:
                            workflow.add_edge(
                                assistant_name, "supervisor-" + assistant_graph.stages[i + 1].name
                            )
                    else:
                        workflow.add_edge(assistant_name, END)
            else:
                members_names = []
                for assistant_name in stage.assistants:
                    members_names.append(assistant_name)
                    if i < len(assistant_graph.stages) - 1:
                        next_stage = assistant_graph.stages[i + 1]
                        if len(next_stage.assistants) == 1:
                            workflow.add_edge(assistant_name, next_stage.assistants[0])
                        else:
                            workflow.add_edge(
                                assistant_name, "supervisor-" + assistant_graph.stages[i + 1].name
                            )
                            workflow.add_edge(assistant_name, "supervisor-" + assistant_graph.stages[i].name)
                    else:
                        workflow.add_edge(assistant_name, "supervisor-" + assistant_graph.stages[i].name)
                conditional_map = {k: k for k in members_names}
                if i == len(assistant_graph.stages) - 1:
                    conditional_map["FINISH"] = "output"
                workflow.add_conditional_edges(
                    "supervisor-" + stage.name, lambda x: x["next"], conditional_map
                )
        if len(assistant_graph.stages[0].assistants) == 1:
            workflow.set_entry_point(assistant_graph.stages[0].assistants[0])
        else:
            workflow.set_entry_point("supervisor-" + assistant_graph.stages[0].name)
        if len(assistant_graph.stages[-1].assistants) > 1:
            workflow.add_edge("output", END)
