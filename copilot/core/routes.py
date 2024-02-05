from fastapi import APIRouter

from . import utils
from .agent import AgentResponse, copilot_agents, AgentEnum
from .agent.assistant_agent import AssistantAgent
from .exceptions import UnsupportedAgent
from .local_history import ChatHistory, local_history_recorder
from .schemas import QuestionSchema
from .utils import copilot_debug

core_router = APIRouter()

current_agent = None


def select_copilot_agent(copilot_type: str):
    if copilot_type not in copilot_agents:
        raise UnsupportedAgent()
    return copilot_agents[copilot_type]


@core_router.post("/question")
def serve_question(question: QuestionSchema):
    """Copilot main endpdoint to answering questions."""
    agent_type = question.type
    if agent_type is None:
        agent_type = utils.read_optional_env_var("AGENT_TYPE", AgentEnum.LANGCHAIN.value)
    copilot_agent = select_copilot_agent(agent_type)
    copilot_debug("  Current agent loaded: " + copilot_agent.__class__.__name__)
    copilot_debug("/question endpoint):")
    copilot_debug("  question: " + question.question)
    copilot_debug("  agent_type: " + str(agent_type))
    copilot_debug("  assistant_id: " + str(question.assistant_id))
    copilot_debug("  conversation_id: " + str(question.conversation_id))
    copilot_debug("  file_ids: " + str(question.file_ids))
    agent_response: AgentResponse = copilot_agent.execute(question)
    local_history_recorder.record_chat(chat_question=question.question, chat_answer=agent_response.output)

    return {"answer": agent_response.output}


@core_router.get("/tools")
def serve_tools():
    """Show tools available, with their information."""
    langchain_agent = select_copilot_agent(AgentEnum.LANGCHAIN.value)
    tool_list = langchain_agent.get_tools()
    tool_dict = {}
    for tool in tool_list:
        tool_dict[tool.name] = {
            "description": tool.description,
            "parameters": tool.args,
        }
    return {"answer": tool_dict}


@core_router.get("/history")
def get_chat_history():
    chat_history: ChatHistory = local_history_recorder.get_chat_history()
    return chat_history


@core_router.get("/assistant")
def serve_assistant():
    if not isinstance(current_agent, AssistantAgent):
        raise Exception("Copilot is not using AssistantAgent")

    return {"assistant_id": current_agent.get_assistant_id()}
