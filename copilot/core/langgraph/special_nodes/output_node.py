from typing import Final

from copilot.core import utils
from copilot.core.langgraph.members_util import MembersUtil
from copilot.core.schemas import AssistantSchema


class OutputNode:
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4o")

    def build(self, system_prompt=None):
        if system_prompt is None:
            system_prompt = (
                "You are tasked with finishing a conversation between the"
                " workers and the user request "
                " answer with the previous message."
            )
        return MembersUtil().get_member(AssistantSchema.model_validate({
            "name": "output",
            "type": "langchain",
            "provider": "openai",
            "model": "gpt-4o",
            "system_prompt": system_prompt
        }))
