from .agent import AgentResponse, CopilotAgent
from .agent import AssistantResponse
from ..langgraph.copilot_langgraph import CopilotLangGraph
from ..langgraph.members_util import MembersUtil
from ..langgraph.patterns import SupervisorPattern
from ..langgraph.patterns.base_pattern import GraphMember
from ..memory.memory_handler import MemoryHandler
from ..schemas import GraphQuestionSchema


class LanggraphAgent(CopilotAgent):
    _memory: MemoryHandler = None

    def __init__(self):
        super().__init__()
        self._memory = MemoryHandler()

    # The agent state is the input to each node in the graph
    def execute(self, question: GraphQuestionSchema) -> AgentResponse:
        thread_id = question.conversation_id
        _tools = self._configured_tools

        members: list[GraphMember] = MembersUtil().get_members(question)

        lang_graph = CopilotLangGraph(members, question.graph, SupervisorPattern())


        final_response = lang_graph.invoke(question=question.question, thread_id=thread_id, get_image=question.generate_image)

        return AgentResponse(
            input=question.model_dump_json(),
            output=AssistantResponse(
                response=final_response, conversation_id=thread_id
            )
        )
