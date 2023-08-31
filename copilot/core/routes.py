from fastapi import APIRouter

from .agent import open_ai_agent
from .schemas import QuestionSchema

core_router = APIRouter()

@core_router.post("/question")
def serve_question(question: QuestionSchema):
    """Copilot main endpdoint to answering questions."""
    agent_response = open_ai_agent.chat(
        question.question,
        remote=True,
        return_code=False,
    )

    return {"answer": agent_response}