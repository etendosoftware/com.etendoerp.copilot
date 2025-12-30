from typing import List

from copilot.core.langgraph.members_util import codify_name
from copilot.core.langgraph.patterns.base_pattern import BasePattern
from copilot.core.langgraph.special_nodes.supervisor_node import (
    get_supervisor_system_prompt,
)
from copilot.core.langgraph.tool_utils.TaskManagementTool import task_management_tool
from copilot.core.utils.agent import get_llm, get_structured_output
from langgraph.prebuilt.chat_agent_executor import AgentState
from langgraph_supervisor import create_supervisor


class LangSupervisorState(AgentState):
    tasks_to_process: List[str]  # pending
    current_task: str  # current
    done_tasks: List[str]  # Already done
    structured_response: str  # Final structured response


class LangSupervisorPattern(BasePattern):
    _first = None

    def __init__(self, members, assistant_graph, pattern: BasePattern, memory, store, full_question=None):
        self._pattern = pattern
        self._full_question = full_question
        temperature = (
            full_question.temperature if full_question and full_question.temperature is not None else 0.1
        )
        supervisor_model = (
            full_question.model if full_question and full_question.model is not None else "gpt-4o"
        )
        model = get_llm(model=supervisor_model, provider=full_question.provider, temperature=temperature)
        # Create supervisor workflow
        members_names = [m.name for m in full_question.assistants]
        members_descriptions = [m.description for m in full_question.assistants]
        sv_prompt = get_supervisor_system_prompt(full_question, members_names, members_descriptions)

        _tool = []
        if full_question is not None and (full_question.tools is not None) and len(full_question.tools) > 0:
            for tl in full_question.tools:
                if tl.function.name == "TaskManagementTool":
                    _tool.append(task_management_tool)
        workflow = create_supervisor(
            members,
            model=model,
            tools=_tool,
            prompt=sv_prompt,
            supervisor_name=(
                codify_name(full_question.name) if full_question.name is not None else "Supervisor"
            ),
            state_schema=LangSupervisorState,
            response_format=get_structured_output(full_question),
        )

        # Compile and run
        self._graph = workflow.compile(checkpointer=memory, store=store)
        self._graph.get_graph().print_ascii()

    def get_graph(self):
        return self._graph
