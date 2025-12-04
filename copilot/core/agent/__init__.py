from copilot.baseutils.logging_envvar import print_green

from .agent import AgentEnum, AgentResponse
from .multimodel_agent import MultimodelAgent


def _get_agent_executors():
    _agents = {
        AgentEnum.OPENAI_ASSISTANT.value: MultimodelAgent.__name__,
        AgentEnum.LANGCHAIN.value: MultimodelAgent.__name__,
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
