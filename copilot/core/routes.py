"""Routes for the core blueprint."""
from http import HTTPStatus
from typing import Dict

from flask import Blueprint, jsonify, request

from .agent import open_ai_agent
from .schemas import QuestionSchema

core_blueprint = Blueprint("core", __name__)


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

    return jsonify({"answer": agent_response}), HTTPStatus.OK
