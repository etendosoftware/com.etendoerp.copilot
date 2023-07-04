"""Routes for the core blueprint."""
from dotenv import dotenv_values
from flask import render_template, request

# pylint: disable=import-error, no-name-in-module
from transformers.tools import (
    OpenAiAgent,
)

from . import core  # pylint: disable=cyclic-import
from .bastian_tool import BastianFetcher, XMLTranslatorTool


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
    config = dotenv_values(".env")

    agent = OpenAiAgent(
        model="gpt-4",
        api_key=config.get("OPENAI_API_KEY"),
        additional_tools=[translator_tool],
    )
            
    response = agent.chat(
        question,
        remote=True,
        return_code=False,
    )
    print(response)
    if isinstance(response, (dict, str)):
        return {
            "answer": response,
        }
    raise ValueError("Invalid response type")
