from typing import Dict, Final, Union

from langchain.agents import AgentExecutor, AgentOutputParser, create_openai_functions_agent
from langchain.agents.output_parsers.openai_tools import OpenAIToolsAgentOutputParser
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.agents import AgentAction, AgentFinish
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openai import ChatOpenAI

from .agent import AgentResponse, CopilotAgent
from .agent import AssistantResponse
from .. import utils
from ..memory.memory_handler import MemoryHandler
from ..schemas import QuestionSchema, ToolSchema
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
    _memory : MemoryHandler = None

    def __init__(self):
        super().__init__()
        self._memory = MemoryHandler()

    def get_agent(self, provider: str, open_ai_model: str,
                                      tools: list[ToolSchema] = None, system_prompt: str = None):
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
            agent = self.get_openai_agent(open_ai_model, tools, system_prompt)

        return agent

    def get_agent_executor(self, agent):
        return AgentExecutor(agent=agent, tools=self._configured_tools, verbose=True, log=True)

    def get_openai_agent(self, open_ai_model, tools, system_prompt):
        _llm = ChatOpenAI(temperature=0, streaming=False, model_name=open_ai_model)
        _enabled_tools = self.get_functions(tools)
        if len(_enabled_tools):
            prompt = ChatPromptTemplate.from_messages(
                [
                    ("system", SYSTEM_PROMPT_PLACEHOLDER if system_prompt is None else system_prompt),
                    MessagesPlaceholder(variable_name="messages"),
                    MessagesPlaceholder(variable_name="agent_scratchpad"),
                ]
            )
            agent = create_openai_functions_agent(_llm, _enabled_tools, prompt)
        else:
            llm = _llm
            prompt = ChatPromptTemplate.from_messages(
                [
                    ("system", SYSTEM_PROMPT_PLACEHOLDER if system_prompt is None else system_prompt),
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
        agent = self.get_agent(question.provider, question.model, question.tools)
        executor: Final[AgentExecutor] = self.get_agent_executor(agent)
        messages = self._memory.get_memory(question.history, full_question)
        langchain_respose: Dict = executor.invoke({"system_prompt": question.system_prompt, "messages": messages})
        output_answer = {"response": langchain_respose["output"]}
        return AgentResponse(input=full_question, output=AssistantResponse(
            response=output_answer["response"],
            conversation_id=question.conversation_id
        ))

    def get_tools(self):
        return self._configured_tools
