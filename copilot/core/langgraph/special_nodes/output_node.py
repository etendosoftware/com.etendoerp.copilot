from typing import Final

from copilot.baseutils.logging_envvar import read_optional_env_var
from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.schemas import AssistantSchema


class OutputNode:
    OPENAI_MODEL: Final[str] = read_optional_env_var("OPENAI_MODEL", "gpt-4o")

    def build(self, system_prompt=None, temperature=1):
        if system_prompt is None:
            system_prompt = """
            Your task is to act solely as a response integration node. You will receive a sequence of messages, some from 'User' and others from 'Assistant'.
            Identify the last message sent by 'User', and from that point onwards, collect all subsequent messages from 'Assistant'.
            Your job is to merge these 'Assistant' messages into a single response. For each piece of information, introduce it using the format:
            Do not perform any other actions beyond composing the response. Avoid redundancy, ensure clarity, and keep the final response well-structured.
            """
        return MembersUtil().get_member(
            AssistantSchema.model_validate(
                {
                    "name": "output",
                    "type": "langchain",
                    "provider": "openai",
                    "model": self.OPENAI_MODEL,
                    "system_prompt": system_prompt,
                    "temperature": temperature,
                }
            )
        )
