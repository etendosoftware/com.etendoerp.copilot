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

class CopilotLangGraph:
    def supervisor_chain(self, members):
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
        members_names = [member.name for member in members]
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
                    "Given the conversation above you have some information to respond and we can FINISH or we should act?"
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


    def construct_graph(self, supervisor_chain, members):

        # The agent state is the input to each node in the graph
        class AgentState(TypedDict):
            # The annotation tells the graph that new messages will always
            # be added to the current states
            messages: Annotated[Sequence[BaseMessage], operator.add]
            # The 'next' field indicates where to route to next
            next: str

        #research_agent = self.create_agent(llm, [tavily_tool], "You are a web researcher.")
        #research_node = functools.partial(agent_node, _agent=research_agent, _name="Researcher")

        # NOTE: THIS PERFORMS ARBITRARY CODE EXECUTION. PROCEED WITH CAUTION
        #code_agent = self.create_agent(
            #llm,
            #[python_repl_tool],
            #"You may generate safe python code to analyze data and generate charts using matplotlib.",
        #)
        #code_node = functools.partial(agent_node, agent=code_agent, name="Coder")

        workflow = StateGraph(AgentState)
        for member in members:
            workflow.add_node(member.name, member.node)
        workflow.add_node("supervisor", supervisor_chain)
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

    def connect_graph(self, members, workflow):
        for member in members:
            # We want our workers to ALWAYS "report back" to the supervisor when done
            workflow.add_edge(member.name, "supervisor")
        # The supervisor populates the "next" field in the graph state
        # which routes to a node or finishes
        members_names = [member.name for member in members]
        conditional_map = {k: k for k in members_names}
        conditional_map["FINISH"] = END
        workflow.add_conditional_edges("supervisor", lambda x: x["next"], conditional_map)
        # Finally, add entrypoint
        workflow.set_entry_point("supervisor")

    def __init__(self, members):
        supervisor_chain = self.supervisor_chain(members)
        workflow = self.construct_graph(supervisor_chain, members)
        self.connect_graph(members, workflow)
        self.graph = workflow.compile()
        self.graph.get_graph().print_ascii()

    def invoke(self, question):
        message = None
        for message in self.graph.stream(
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
