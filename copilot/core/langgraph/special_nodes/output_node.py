from typing import Final

from copilot.core import utils
from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.schemas import AssistantSchema


class OutputNode:
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4o")

    def build(self, system_prompt=None, temperature=1):
        if system_prompt is None:
            system_prompt = (
                "You are an output node."
                " Your task is to provide a summary of the conversation."
                " You will receive messages which are the conversation history."
                " The last Human message is the user's last request, we will call this message 'request'."
                " And the messages after that are messages that have a tag from each assistant that delivered their response,"
                " we will call these messages 'assistant responses'."
                " Your response should be a summary (Without omitting information) of these assistant responses."
                " Example:"
                " #Request: What is the capital of France? Is it bigger than London?"
                " #Assistant Response 1: The capital of France is Paris."
                " #Assistant Response 2: Paris is bigger than London."
                " Your response: The capital of France is Paris and it is bigger than London."
            )
        return MembersUtil().get_member(AssistantSchema.model_validate({
            "name": "output",
            "type": "langchain",
            "provider": "openai",
            "model": self.OPENAI_MODEL,
            "system_prompt": system_prompt,
            "temperature": temperature
        }))
