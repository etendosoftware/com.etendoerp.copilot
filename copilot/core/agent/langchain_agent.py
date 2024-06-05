from typing import Dict, Final, Union

from langchain.agents import create_openai_tools_agent, AgentExecutor, AgentOutputParser, create_openai_functions_agent
from langchain.agents.format_scratchpad import format_to_openai_functions
from langchain.agents.output_parsers import OpenAIFunctionsAgentOutputParser
from langchain_openai import ChatOpenAI
from langchain.chat_models.base import BaseChatModel
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.tools.render import format_tool_to_openai_function
from langchain_core.utils.function_calling import convert_to_openai_function
from langchain_core.agents import AgentAction, AgentFinish
from langchain_core.messages import (
    HumanMessage, AIMessage,
)
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.runnables import Runnable, RunnablePassthrough
from langchain.agents.output_parsers.openai_tools import OpenAIToolsAgentOutputParser
from langchain.agents.format_scratchpad.openai_tools import (
    format_to_openai_tool_messages,
)

from .agent import AgentResponse, CopilotAgent, AssistantResponse
from .. import utils
from ..schemas import QuestionSchema, ToolSchema
from .agent import AgentResponse, CopilotAgent
from ..utils import get_full_question

SYSTEM_PROMPT_PLACEHOLDER = "{system_prompt}"


class CustomOutputParser(AgentOutputParser):
    def parse(self, output) -> Union[AgentAction, AgentFinish]:
        final_answer = output
        agent_finish = AgentFinish(
            return_values={"output": final_answer},
            log=output,
        )
        return agent_finish


class LangchainAgent(CopilotAgent):
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4-turbo-preview")

    def __init__(self):
        super().__init__()

    def _get_langchain_agent_executor(self, provider: str, open_ai_model: str,
                                      tools: list[ToolSchema] = None) -> AgentExecutor:
        """Construct and return an agent from scratch, using LangChain Expression Language.

        Raises:
            OpenAIApiKeyNotFound: raised when OPENAI_API_KEY is not configured
            SystemPromptNotFound: raised when SYSTEM_PROMPT is not configured
        """
        self._assert_open_api_key_is_set()
        self._assert_system_prompt_is_set()

        if provider == "gemini":
            agent = self.get_gemini_agent(open_ai_model)
        else:
            agent = self.get_openai_agent(open_ai_model, tools)

        return AgentExecutor(agent=agent, tools=self._configured_tools, verbose=True, log=True)

    def get_openai_agent(self, open_ai_model, tools):
        _llm = ChatOpenAI(temperature=0, streaming=False, model_name=open_ai_model)
        _enabled_tools = self.get_functions(tools)
        if len(_enabled_tools):
            prompt = ChatPromptTemplate.from_messages(
                [
                    ("system", SYSTEM_PROMPT_PLACEHOLDER),
                    MessagesPlaceholder(variable_name="messages"),
                    MessagesPlaceholder(variable_name="agent_scratchpad"),
                ]
            )
            agent = create_openai_functions_agent(_llm, _enabled_tools, prompt)
        else:
            llm = _llm
            prompt = ChatPromptTemplate.from_messages(
                [
                    ("system", SYSTEM_PROMPT_PLACEHOLDER),
                    MessagesPlaceholder(variable_name="messages"),
                ]
            )
            agent = (
                    prompt
                    | llm
                    | OpenAIToolsAgentOutputParser()
            )
        return agent

    def get_functions(self, tools):
        _enabled_tools = []
        if tools:
            for tool in tools:
                for t in self._configured_tools:
                    if t.name == tool.function.name:
                        _enabled_tools.append(t)
                        break
        return _enabled_tools

    def get_gemini_agent(self, open_ai_model):
        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", "{system_prompt}"),
                ("user", "{input}"),
            ]
        )
        _llm = ChatGoogleGenerativeAI(temperature=1, model=open_ai_model, convert_system_message_to_human=True)
        llm = _llm.bind(
        )
        agent = (
                {
                    "system_prompt": lambda x: x["system_prompt"],
                    "input": lambda x: x["input"],
                }
                | prompt
                | llm
                | CustomOutputParser()
        )
        return agent

    def execute(self, question: QuestionSchema) -> AgentResponse:
        full_question = get_full_question(question)
        executor: Final[AgentExecutor] = self._get_langchain_agent_executor(
            provider=question.provider,
            open_ai_model=question.model,
            tools=question.tools
        )
        messages = []
        for message in question.history:
            if message.role == "USER":
                messages.append(HumanMessage(content=message.content))
            elif message.role == "ASSISTANT":
                messages.append(AIMessage(content=message.content))
        messages.append(HumanMessage(content=full_question))
        langchain_respose: Dict = executor.invoke({"system_prompt": question.system_prompt, "messages": messages})
        output_answer = {"response": langchain_respose["output"]}
        return AgentResponse(input=full_question, output=AssistantResponse(
            response=output_answer["response"],
            assistant_id=question.assistant_id,
            conversation_id=question.conversation_id
        ))

    def get_tools(self):
        return self._configured_tools
