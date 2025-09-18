import os
from typing import Dict, Final, Optional, Union

from copilot.core.agent.agent import (
    AgentResponse,
    AssistantResponse,
    CopilotAgent,
    get_kb_tool,
)
from copilot.core.utils import get_proxy_url
from langchain.agents import (
    AgentExecutor,
    AgentOutputParser,
    create_openai_functions_agent,
)
from langchain.agents.output_parsers.openai_tools import OpenAIToolsAgentOutputParser
from langchain.chat_models import init_chat_model
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.agents import AgentAction, AgentFinish
from langchain_core.runnables import AddableDict
from langchain_google_genai import ChatGoogleGenerativeAI

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
    OPENAI_MODEL: Final[str] = utils.read_optional_env_var("OPENAI_MODEL", "gpt-4o")
    _memory: MemoryHandler = None

    def __init__(self):
        super().__init__()
        self._memory = MemoryHandler()

    def get_agent(
        self,
        provider: str,
        open_ai_model: str,
        tools: list[ToolSchema] = None,
        system_prompt: str = None,
        temperature: float = 1,
        kb_vectordb_id: Optional[str] = None,
    ):
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
            agent = self.get_openai_agent(open_ai_model, tools, system_prompt, temperature, kb_vectordb_id)

        return agent

    def get_agent_executor(self, agent) -> AgentExecutor:
        agent_exec = AgentExecutor(
            agent=agent,
            tools=self._configured_tools,
            verbose=True,
            log=True,
            handle_parsing_errors=True,
            debug=True,
        )
        agent_exec.max_iterations = utils.read_optional_env_var_int("COPILOT_MAX_ITERATIONS", 100)
        max_exec_time = utils.read_optional_env_var_float("COPILOT_EXECUTION_TIMEOUT", 0)
        agent_exec.max_execution_time = None if max_exec_time == 0 else max_exec_time
        return agent_exec

    def get_openai_agent(
        self, open_ai_model, tools, system_prompt, temperature=1, kb_vectordb_id: Optional[str] = None
    ):
        _llm = init_chat_model(
            temperature=temperature,
            model_provider="openai",
            model=open_ai_model,
            streaming=False,
            base_url=get_proxy_url(),
        )
        _enabled_tools = self.get_functions(tools)

        kb_tool = get_kb_tool()
        if kb_tool is not None:
            _enabled_tools.append(kb_tool)
            self._configured_tools.append(kb_tool)

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
            agent = prompt | llm | OpenAIToolsAgentOutputParser()
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

    def get_gemini_agent(self, open_ai_model, temperature=1):
        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", "{system_prompt}"),
                ("user", "{input}"),
            ]
        )
        _llm = ChatGoogleGenerativeAI(
            temperature=temperature, model=open_ai_model, convert_system_message_to_human=True
        )
        llm = _llm.bind()
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
        agent = self.get_agent(
            question.provider,
            question.model,
            question.tools,
            question.system_prompt,
            question.temperature,
            question.kb_vectordb_id,
        )
        executor: Final[AgentExecutor] = self.get_agent_executor(agent)
        messages = self._memory.get_memory(question.history, full_question)
        langchain_respose: Dict = executor.invoke(
            {"system_prompt": question.system_prompt, "messages": messages}
        )
        output_answer = {"response": langchain_respose["output"]}
        return AgentResponse(
            input=full_question,
            output=AssistantResponse(
                response=output_answer["response"], conversation_id=question.conversation_id
            ),
        )

    def get_tools(self):
        return self._configured_tools

    async def aexecute(self, question: QuestionSchema) -> AgentResponse:
        copilot_stream_debug = os.getenv("COPILOT_STREAM_DEBUG", "false").lower() == "true"  # Debug mode
        agent = self.get_agent(
            question.provider,
            question.model,
            question.tools,
            question.system_prompt,
            question.temperature,
            question.kb_vectordb_id,
        )
        agent_executor: Final[AgentExecutor] = self.get_agent_executor(agent)
        full_question = question.question
        if question.local_file_ids is not None and len(question.local_file_ids) > 0:
            full_question += "\n\n" + "LOCAL FILES: " + "\n".join(question.local_file_ids)
        messages = self._memory.get_memory(question.history, full_question)
        _input = {"content": full_question, "messages": messages, "system_prompt": question.system_prompt}
        if question.conversation_id is not None:
            _input["thread_id"] = question.conversation_id
        async for event in agent_executor.astream_events(_input, version="v2"):
            if copilot_stream_debug:
                yield AssistantResponse(response=str(event), conversation_id="", role="debug")
            kind = event["event"]
            if kind == "on_tool_start":
                if len(event["parent_ids"]) == 1:
                    yield AssistantResponse(response=event["name"], conversation_id="", role="tool")
            elif kind == "on_chain_end":
                if not type(event["data"]["output"]) == AddableDict:
                    output = event["data"]["output"]
                    if type(output) == AgentFinish:
                        return_values = output.return_values
                        yield AssistantResponse(
                            response=str(return_values["output"]), conversation_id=question.conversation_id
                        )
