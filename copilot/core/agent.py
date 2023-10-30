import os
from dataclasses import dataclass
from typing import Final

from langchain.agents import AgentExecutor
from langchain.agents.format_scratchpad import format_to_openai_functions
from langchain.agents.output_parsers import OpenAIFunctionsAgentOutputParser
from langchain.chat_models import ChatOpenAI
from langchain.chat_models.base import BaseChatModel
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.tools.render import format_tool_to_openai_function

from .exceptions import OpenAIApiKeyNotFound
from .tool_manager import configured_tools

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")


@dataclass
class AgentResponse:
    input: str
    output: str


def get_langchain_agent_executor(chat_model: BaseChatModel) -> AgentExecutor:
    """Construct and return an agent from scratch, using LangChain Expression Language.

    Raises:
        OpenAIApiKeyNotFound: returned when OPENAI_API_KEY is not configured
    """
    if not OPENAI_API_KEY:
        raise OpenAIApiKeyNotFound()

    # loads the language model we are going to use to control the agent
    llm = chat_model(temperature=0)

    # binds tools to the LLM
    llm_with_tools = llm.bind(functions=[format_tool_to_openai_function(tool) for tool in configured_tools])

    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", "You are very powerful assistant, but bad at calculating lengths of words."),
            ("user", "{input}"),
            MessagesPlaceholder(variable_name="agent_scratchpad"),
        ]
    )

    agent = (
        {
            "input": lambda x: x["input"],
            "agent_scratchpad": lambda x: format_to_openai_functions(x["intermediate_steps"]),
        }
        | prompt
        | llm_with_tools
        | OpenAIFunctionsAgentOutputParser()
    )

    return AgentExecutor(agent=agent, tools=configured_tools, verbose=True)


langchain_agent_executor: Final[BaseChatModel] = get_langchain_agent_executor(chat_model=ChatOpenAI)
