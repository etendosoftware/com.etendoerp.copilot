from fastapi import APIRouter

from .agent import AgentResponse, copilot_agent, AssistantAgent
from .local_history import ChatHistory, local_history_recorder
from .schemas import QuestionSchema

core_router = APIRouter()


@core_router.post("/question")
def serve_question(question: QuestionSchema):
    """Copilot main endpdoint to answering questions."""
    agent_response: AgentResponse = copilot_agent.execute(question)

    local_history_recorder.record_chat(chat_question=question.question, chat_answer=agent_response.output)

    return {"answer": agent_response.output}


@core_router.get("/history")
def get_chat_history():
    chat_history: ChatHistory = local_history_recorder.get_chat_history()
    return chat_history


@core_router.get("/assistant")
def serve_assistant():
    if not isinstance(copilot_agent, AssistantAgent):
        raise Exception("Copilot is not using AssistantAgent")

    return {"assistant_id": copilot_agent.get_assistant_id()}

