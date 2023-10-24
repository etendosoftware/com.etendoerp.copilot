import os
from typing import Final

from transformers.tools import OpenAiAgent

from .exceptions import OpenAIApiKeyNotFound
from .tool_manager import configured_tools

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")


def create_open_ai_agent() -> OpenAiAgent:
    if not OPENAI_API_KEY:
        raise OpenAIApiKeyNotFound()

    return OpenAiAgent(
        model="gpt-4",
        api_key=OPENAI_API_KEY,
        additional_tools=configured_tools,
    )


open_ai_agent: Final[OpenAiAgent] = create_open_ai_agent()
