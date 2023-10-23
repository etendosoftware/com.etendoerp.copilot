from fastapi import APIRouter

from .agent import open_ai_agent
from .schemas import QuestionSchema
from .local_history import ChatHistory, local_history_recorder

core_router = APIRouter()


@core_router.post("/question")
def serve_question(question: QuestionSchema):
    """Copilot main endpdoint to answering questions."""
    agent_response = open_ai_agent.chat(
        question.question,
        remote=True,
        return_code=False,
    )
    local_history_recorder.record_chat(chat_question=question.question, chat_answer=agent_response)

    return {"answer": agent_response}


@core_router.get("/history")
def get_chat_history():
    chat_history: ChatHistory = local_history_recorder.get_chat_history()
    return chat_history