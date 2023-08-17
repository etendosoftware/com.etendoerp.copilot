"""Routes for the core blueprint."""
import os

from flask import Blueprint, render_template, request
from transformers.tools import OpenAiAgent

from . import core
from .tool_manager import configured_tools

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

core_blueprint = Blueprint("core", __name__)


@core.route("/", methods=["GET"])
def serve_index():
    """Serves the home page of the website.

    Returns:
        str: The HTML content of the home page.
    """
    return render_template("index.html")


@core_blueprint.route("/question", methods=["POST"])
def serve_question():
    """Serves the question answering endpoint."""
    # Get the JSON data from the request
    data = request.get_json()

    # Extract the question from the data
    question = data["question"]

    agent = OpenAiAgent(
        model="gpt-4",
        api_key=OPENAI_API_KEY,
        additional_tools=configured_tools,
    )

    response = agent.chat(
        question,
        remote=True,
        return_code=False,
    )

    if isinstance(response, list):
        response = "\n".join(response)

    if isinstance(response, (dict, str)):
        return {
            "answer": response,
        }
    raise ValueError("Invalid response type")
