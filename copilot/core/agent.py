import os

from transformers.tools import OpenAiAgent

from .tool_manager import configured_tools

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")


open_ai_agent: OpenAiAgent = None


def set_open_ai_agent():
    global open_ai_agent
    open_ai_agent = OpenAiAgent(
        model="gpt-4",
        api_key=OPENAI_API_KEY,
        additional_tools=configured_tools,
    )
