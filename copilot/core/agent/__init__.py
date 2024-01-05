import os
from typing import Final

from .. import utils
from ...core.utils import print_green
from ..exceptions import UnsupportedAgent
from .agent import AgentEnum, AgentResponse
from .assistant_agent import AssistantAgent
from .langchain_agent import LangchainAgent



def _get_agent_executors():
    _agents = {
        AgentEnum.OPENAI_ASSISTANT.value: AssistantAgent.__name__,
        AgentEnum.LANGCHAIN.value: LangchainAgent.__name__,
    }

    # create a Dict with the class name as key and the class as value
    # recorremos el diccionario de agentes y creamos una instancia de la clase que corresponda
    agents_classes = {}
    for agent in _agents.keys():
        print_green(f"Loading Copilot Agent: {agent}")
        class_name = globals()[_agents.get(agent)]
        agents_classes[agent] = class_name()
    return agents_classes


copilot_agents = _get_agent_executors()
