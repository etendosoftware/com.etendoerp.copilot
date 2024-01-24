import os
from typing import Dict, Final

from langchain.agents import AgentExecutor
from langchain.agents.format_scratchpad import format_to_openai_functions
from langchain.agents.output_parsers import OpenAIFunctionsAgentOutputParser
from langchain.chat_models import ChatOpenAI
from langchain.chat_models.base import BaseChatModel
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.tools.render import format_tool_to_openai_function

from .. import utils
from ..schemas import QuestionSchema
from .agent import AgentResponse, CopilotAgent


class LangchainAgent(CopilotAgent):
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-3.5-turbo")

    def __init__(self):
        super().__init__()
        self._langchain_agent_executor: Final[BaseChatModel] = self._get_langchain_agent_executor(
            open_ai_model=self.OPENAI_MODEL
        )

    def _get_langchain_agent_executor(self, open_ai_model: str) -> AgentExecutor:
        """Construct and return an agent from scratch, using LangChain Expression Language.

        Raises:
            OpenAIApiKeyNotFound: raised when OPENAI_API_KEY is not configured
            SystemPromptNotFound: raised when SYSTEM_PROMPT is not configured
        """
        self._assert_open_api_key_is_set()
        self._assert_system_prompt_is_set()

        # loads the language model we are going to use to control the agent
        llm = ChatOpenAI(temperature=0, model_name=open_ai_model)

        # binds tools to the LLM
        llm_with_tools = llm.bind(
            functions=[format_tool_to_openai_function(tool) for tool in self._configured_tools]
        )

        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", self.SYSTEM_PROMPT),
                ("user", "{input}"),
                MessagesPlaceholder(variable_name="copilot_agent_scratchpad"),
            ]
        )

        agent = (
                {
                    "input": lambda x: x["input"],
                    "copilot_agent_scratchpad": lambda x: format_to_openai_functions(x["intermediate_steps"]),
                }
                | prompt
                | llm_with_tools
                | OpenAIFunctionsAgentOutputParser()
        )

        return AgentExecutor(agent=agent, tools=self._configured_tools, verbose=True)

    def execute(self, question: QuestionSchema) -> AgentResponse:
        langchain_respose: Dict = self._langchain_agent_executor.invoke({"input": question.question})
        output_answer = {"response": langchain_respose["output"]}
        return AgentResponse(input=langchain_respose["input"], output=output_answer)

    def get_tools(self):
        return self._configured_tools