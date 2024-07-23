import operator
from abc import abstractmethod
from typing import TypedDict, Annotated, Sequence, Final

from langchain.agents import create_openai_tools_agent, AgentExecutor
from langchain_core.messages import BaseMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_openai import ChatOpenAI
from langgraph.graph import StateGraph
from langsmith import traceable


from copilot.core import utils


class BasePattern():
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4o")

    @traceable
    def construct_nodes(self, members, assistant_graph = None) -> StateGraph:
        class AgentState(TypedDict):
            # The annotation tells the graph that new messages will always
            # be added to the current states
            messages: Annotated[Sequence[BaseMessage], operator.add]
            # The 'next' field indicates where to route to next
            next: str

        workflow = StateGraph(AgentState)
        for member in members:
            workflow.add_node(member.name, member.node)
        return workflow

    @traceable
    def create_agent(self, llm: ChatOpenAI, tools: list, system_prompt: str):
        # Each worker node will be given a name and some tools.
        prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    system_prompt,
                ),
                MessagesPlaceholder(variable_name="messages"),
                MessagesPlaceholder(variable_name="agent_scratchpad"),
            ]
        )
        agent = create_openai_tools_agent(llm, tools, prompt)
        executor = AgentExecutor(agent=agent, tools=tools)
        return executor

    @traceable
    @abstractmethod
    def connect_graph(self, assistant_graph, workflow):
        raise NotImplementedError

class GraphMember:
    name: str
    node: object

    @traceable
    def __init__(self, name, node):
        self.name = name
        self.node = node
