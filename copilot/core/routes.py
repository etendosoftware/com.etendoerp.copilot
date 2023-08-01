"""Routes for the core blueprint."""
import os

from flask import render_template, request

# pylint: disable=import-error, no-name-in-module
from transformers.tools import (
    OpenAiAgent,
)

from . import core  # pylint: disable=cyclic-import
from .bastian_tool import BastianFetcher, XMLTranslatorTool
from .hello_word_tool import HelloWorldTool

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")


@core.route("/", methods=["GET"])
def serve_index():
    """Serves the home page of the website.

    Returns:
        str: The HTML content of the home page.
    """
    return render_template("index.html")


@core.route("/question", methods=["POST"])
def serve_question():
    """Serves the question answering endpoint."""
    # Get the JSON data from the request
    data = request.get_json()

    # Extract the question from the data
    question = data["question"]

    bastian_tool = BastianFetcher()
    translator_tool = XMLTranslatorTool()
    hello_word_tool = HelloWorldTool()

    agent = OpenAiAgent(
        # model="gpt-4",
        model="gpt-3",
        api_key=OPENAI_API_KEY,
        additional_tools=[bastian_tool, translator_tool, hello_word_tool],
    )

    response = agent.chat(
        question,
        remote=True,
        return_code=False,
    )

    if isinstance(response, (dict, str)):
        return {
            "answer": response,
        }
    raise ValueError("Invalid response type")
