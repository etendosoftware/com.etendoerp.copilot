"""Routes for the core blueprint."""
import os
from typing import Dict

from flask import Blueprint, request
from transformers.tools import OpenAiAgent

from .schemas import QuestionSchema
from .tool_manager import configured_tools

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

core_blueprint = Blueprint("core", __name__)

open_ai_agent: OpenAiAgent = OpenAiAgent(
    model="gpt-4",
    api_key=OPENAI_API_KEY,
    additional_tools=configured_tools,
)


@core_blueprint.route("/question", methods=["POST"])
def serve_question():
    """Serves the question answering endpoint."""
    data: Dict = request.get_json()
    question_schema: QuestionSchema = QuestionSchema(**data)
    question = question_schema.question

    agent_response = open_ai_agent.chat(
        question,
        remote=True,
        return_code=False,
    )

    if isinstance(agent_response, list):
        agent_response = "\n".join(agent_response)

    if isinstance(agent_response, (dict, str)):
        return {
            "answer": agent_response,
        }

    raise ValueError("Invalid response type")
