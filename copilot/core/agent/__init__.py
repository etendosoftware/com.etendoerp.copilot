from langsmith import traceable

from ...core.utils import print_green
from .agent import AgentEnum, AgentResponse
from .assistant_agent import AssistantAgent
from .langchain_agent import LangchainAgent
from .multimodel_agent import MultimodelAgent


@traceable
def _get_agent_executors():
    _agents = {
        AgentEnum.OPENAI_ASSISTANT.value: AssistantAgent.__name__,
        AgentEnum.LANGCHAIN.value: LangchainAgent.__name__,
        AgentEnum.MULTIMODEL.value: MultimodelAgent.__name__,
    }

    # create a Dict with the class name as key and the class as value
    # we go through the agents dictionary and create an instance of the corresponding class
    agents_classes = {}
    for agent in _agents.keys():
        print_green(f"Loading Copilot Agent: {agent}")
        class_name = globals()[_agents.get(agent)]
        agents_classes[agent] = class_name()
    return agents_classes


copilot_agents = _get_agent_executors()
