from langchain.agents import create_openai_tools_agent, AgentExecutor
from langchain_community.chat_models import ChatOpenAI
from langchain_core.messages import BaseMessage, HumanMessage
import operator
from typing import Annotated, Sequence, TypedDict
import functools
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langgraph.graph import StateGraph, END
from langchain_community.tools.tavily_search import TavilySearchResults
from langchain_experimental.tools import PythonREPLTool

from copilot.core.schemas import AssistantGraph


class CopilotLangGraph:

    assistant_graph: AssistantGraph

    def supervisor_chain(self, members_names):
        from langchain.output_parsers.openai_functions import JsonOutputFunctionsParser
        from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

        system_prompt = (
            "You are a supervisor tasked with managing a conversation between the"
            " following workers:  {members}. Given the following user request,"
            " respond with the worker to act next. Each worker will perform a"
            " task and respond with their results and status. When finished,"
            " respond with FINISH."
        )
        # Our team supervisor is an LLM node. It just picks the next agent to process
        # and decides when the work is completed
        options = ["FINISH"] + members_names
        # Using openai function calling can make output parsing easier for us
        function_def = {
            "name": "route",
            "description": "Select the next role.",
            "parameters": {
                "title": "routeSchema",
                "type": "object",
                "properties": {
                    "next": {
                        "title": "Next",
                        "anyOf": [
                            {"enum": options},
                        ],
                    }
                },
                "required": ["next"],
            },
        }
        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", system_prompt),
                MessagesPlaceholder(variable_name="messages"),
                (
                    "system",
                    "Given the conversation above, who should act next?"
                    " Select one of: {options}",
                ),
            ]
        ).partial(options=str(options), members=", ".join(members_names))

        llm = ChatOpenAI(model="gpt-4-1106-preview")

        supervisor_chain = (
                prompt
                | llm.bind_functions(functions=[function_def], function_call="route")
                | JsonOutputFunctionsParser()
        )

        return supervisor_chain


    def construct_graph(self, members, assistant_graph):

        class AgentState(TypedDict):
            # The annotation tells the graph that new messages will always
            # be added to the current states
            messages: Annotated[Sequence[BaseMessage], operator.add]
            # The 'next' field indicates where to route to next
            next: str

        workflow = StateGraph(AgentState)
        for member in members:
            workflow.add_node(member.name, member.node)

        for stage in assistant_graph.stages:
            members_names = []
            for assistant_name in stage.assistants:
                members_names.append(assistant_name)
            if len(members_names) > 1:
                if stage is not assistant_graph.stages[-1]:
                    # connect with next supervisor
                    members_names.append(assistant_graph.stages[assistant_graph.stages.index(stage) + 1].name)
                supervisor_chain = self.supervisor_chain(members_names)
                workflow.add_node("supervisor-" + stage.name, supervisor_chain)

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

    def connect_graph(self, assistant_graph, workflow):
        for i in range(0, len(assistant_graph.stages)):
            stage = assistant_graph.stages[i]
            if len(stage.assistants) == 1:
                for assistant_name in stage.assistants:
                    if i < len(assistant_graph.stages) - 1:
                        next_stage = assistant_graph.stages[i + 1]
                        if len(next_stage.assistants) == 1:
                            workflow.add_edge(assistant_name, next_stage.assistants[0])
                        else:
                            workflow.add_edge(assistant_name, "supervisor-" + assistant_graph.stages[i + 1].name)
                    else:
                        workflow.add_edge(assistant_name, END)
            else:
                members_names = []
                for assistant_name in stage.assistants:
                    members_names.append(assistant_name)
                    if i < len(assistant_graph.stages) - 1:
                        next_stage = assistant_graph.stages[i+1]
                        if len(next_stage.assistants) == 1:
                            workflow.add_edge(assistant_name, next_stage.assistants[0])
                        else:
                            workflow.add_edge(assistant_name, "supervisor-" + assistant_graph.stages[i+1].name)
                    else:
                        workflow.add_edge(assistant_name, END)
                conditional_map = {k: k for k in members_names}
                workflow.add_conditional_edges("supervisor-" + stage.name, lambda x: x["next"], conditional_map)
        if len(assistant_graph.stages) == 1:
            workflow.set_entry_point(assistant_graph.stages[0].assistants[0])
        else:
            # Finally, add entrypoint
            workflow.set_entry_point("supervisor-" + assistant_graph.stages[0].name)

    def __init__(self, members, assistant_graph):

        workflow = self.construct_graph(members, assistant_graph)
        self.connect_graph(assistant_graph, workflow)
        self._assistant_graph = assistant_graph
        self._graph = workflow.compile()
        self._graph.get_graph().print_ascii()

    def invoke(self, question):
        message = None
        for message in self._graph.stream(
                {"messages": [HumanMessage(content=question)]},
                {"recursion_limit": 50},
        ):
            if "__end__" not in message:
                print("----")

        return message["__end__"]["messages"][-1].content

class GraphMember:
    name: str
    node: object

    def __init__(self, name, node):
        self.name = name
        self.node = node
