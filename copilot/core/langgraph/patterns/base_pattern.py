import operator
from abc import abstractmethod
from typing import Annotated, Final, Sequence, TypedDict

from copilot.baseutils.logging_envvar import read_optional_env_var
from langchain.agents import AgentExecutor, create_openai_tools_agent
from langchain_core.messages import BaseMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_openai import ChatOpenAI
from langgraph.graph import StateGraph
from langgraph.graph.state import CompiledStateGraph


class BasePattern:
    OPENAI_MODEL: Final[str] = read_optional_env_var("OPENAI_MODEL", "gpt-4o")

    def construct_nodes(self, members, assistant_graph=None, full_question=None) -> StateGraph:
        class AgentState(TypedDict):
            # The annotation tells the graph that new messages will always
            # be added to the current states
            messages: Annotated[Sequence[BaseMessage], operator.add]
            # The 'next' field indicates where to route to next
            next: str
            instructions: str

        workflow = StateGraph(AgentState)
        for member in members:
            if isinstance(member, CompiledStateGraph):
                workflow.add_node(member.name, member.nodes["agent"])
            else:
                workflow.add_node(member.name, member.node)
        return workflow

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

    @abstractmethod
    def connect_graph(self, assistant_graph, workflow):
        raise NotImplementedError
