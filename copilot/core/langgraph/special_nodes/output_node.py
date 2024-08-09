from typing import Final

from copilot.core import utils
from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.schemas import AssistantSchema


class OutputNode:
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4o")

    def build(self, system_prompt=None, temperature=1):
        if system_prompt is None:
            system_prompt = (
                "You are tasked with the task of responding to a user's question. Use the information provided to "
                "generate a response."
            )
        return MembersUtil().get_member(AssistantSchema.model_validate({
            "name": "output",
            "type": "langchain",
            "provider": "openai",
            "model": "gpt-4o",
            "system_prompt": system_prompt,
            "temperature": temperature
        }))
