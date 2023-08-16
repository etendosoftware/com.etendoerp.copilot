"""Routes for the core blueprint."""
import json
import os

from flask import Blueprint, render_template, request
from transformers.tools import (
    OpenAiAgent,
)

from .bastian_tool import BastianFetcher, XMLTranslatorTool

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

core = Blueprint("core", __name__)


class ToolManager:
    def __init__(self, tool_config_path="tools_config.json"):
        self.tools = []
        self.tool_config_path = tool_config_path
        self.load_tools()

    def load_tools(self):
        with open(self.tool_config_path, "r") as file:
            tool_config = json.load(file)

        if tool_config.get("BastianFetcher", "enabled") == "enabled":
            self.tools.append(BastianFetcher())

        if tool_config.get("XMLTranslatorTool", "enabled") == "enabled":
            self.tools.append(XMLTranslatorTool())

    def get_tools(self):
        return self.tools


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
    tool_manager = ToolManager()
    enabled_tools = tool_manager.get_tools()
    # Extract the question from the data
    question = data["question"]

    agent = OpenAiAgent(
        model="gpt-4",
        api_key=OPENAI_API_KEY,
        additional_tools=enabled_tools,
    )

    response = agent.chat(
        question,
        remote=True,
        return_code=False,
    )

    print(response)

    if isinstance(response, list):
        response = "\n".join(response)

    if isinstance(response, (dict, str)):
        return {
            "answer": response,
        }
    raise ValueError("Invalid response type")
