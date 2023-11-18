import os

from typing import Final

from .agent import AgentEnum, AgentResponse
from .langchain_agent import LangchainAgent
from .assistant_agent import AssistantAgent
from ..exceptions import UnsupportedAgent
from ...core.utils import print_green

AGENT_TYPE: Final[str] = os.getenv("AGENT_TYPE", AgentEnum.LANGCHAIN.value)


def _get_agent_executor():
    _agents = {
        AgentEnum.OPENAI_ASSISTANT.value: AssistantAgent.__name__,
        AgentEnum.LANGCHAIN.value: LangchainAgent.__name__
    }
    if AGENT_TYPE not in _agents:
        raise UnsupportedAgent()

    print_green(f"Copilot Agent: {AGENT_TYPE}")
    class_name = globals()[_agents.get(AGENT_TYPE)]
    return class_name()


copilot_agent = _get_agent_executor()