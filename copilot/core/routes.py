"""Routes for the core blueprint."""
from http import HTTPStatus
from typing import Dict

from flask import Blueprint, jsonify, request

from . import agent
from .schemas import QuestionSchema

core_blueprint = Blueprint("core", __name__)


@core_blueprint.route("/question", methods=["POST"])
def serve_question():
    """Serves the question answering endpoint."""
    data: Dict = request.get_json()
    question_schema: QuestionSchema = QuestionSchema(**data)
    question = question_schema.question

    if not agent.open_ai_agent:
        agent.set_open_ai_agent()

    agent_response = agent.open_ai_agent.chat(
        question,
        remote=True,
        return_code=False,
    )

    return jsonify({"answer": agent_response}), HTTPStatus.OK
