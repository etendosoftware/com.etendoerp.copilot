import os
from abc import abstractmethod
from dataclasses import dataclass
from enum import Enum
from typing import Final, Optional

from langchain.tools.retriever import create_retriever_tool
from langchain_chroma.vectorstores import Chroma

from .. import tool_installer, utils
from ..exceptions import (
    OpenAIApiKeyNotFound,
    SystemPromptNotFound,
    ToolDependencyMismatch,
)
from ..schemas import AssistantSchema, QuestionSchema
from ..tool_dependencies import Dependency
from ..tool_loader import LangChainTools, ToolLoader
from ..vectordb_utils import get_chroma_settings, get_embedding, get_vector_db_path


class AgentEnum(str, Enum):
    OPENAI_ASSISTANT = "openai-assistant"
    LANGCHAIN = "langchain"
    MULTIMODEL = "multimodel"


@dataclass
class AssistantResponse:
    response: str
    conversation_id: str
    message_id: Optional[str] = None
    role: Optional[str] = None
    assistant_id: Optional[str] = None


@dataclass
class AgentResponse:
    input: str
    output: AssistantResponse


def get_kb_tool(agent_config: AssistantSchema = None):
    kb_tool = None
    kb_search_k = agent_config.kb_search_k if agent_config else 4
    kb_vectordb_id = agent_config.kb_vectordb_id if agent_config else None
    if (
        kb_vectordb_id is not None
        and os.path.exists(get_vector_db_path(kb_vectordb_id))
        and os.listdir(get_vector_db_path(kb_vectordb_id))
    ):
        db_path = get_vector_db_path(kb_vectordb_id)
        db = Chroma(
            persist_directory=db_path,
            embedding_function=get_embedding(),
            client_settings=get_chroma_settings(),
        )
        # check if the db is empty
        res = db.get(limit=1)
        if len(res["ids"]) > 0:
            retriever = db.as_retriever(
                search_kwargs={"k": kb_search_k},
            )
            kb_tool = create_retriever_tool(
                retriever,
                "KnowledgeBaseSearch",
                "Search in the knowledge base for a term or question.",
            )
    return kb_tool


class CopilotAgent:
    """Copilot Agent interface."""

    OPENAI_API_KEY: Final[str] = os.getenv("OPENAI_API_KEY")
    SYSTEM_PROMPT: Final[str] = utils.read_optional_env_var(
        "SYSTEM_PROMPT",
        "You are a very powerful assistant with a set of tools, which you will try to use for the requests made to you.",
    )

    def __init__(self):
        self._configured_tools: LangChainTools = ToolLoader().load_configured_tools()

    def _assert_open_api_key_is_set(self):
        if not self.OPENAI_API_KEY:
            raise OpenAIApiKeyNotFound()

    def _assert_system_prompt_is_set(self):
        if not self.SYSTEM_PROMPT:
            raise SystemPromptNotFound()

    def _assert_openai_is_installed(self, version: str):
        dependency = Dependency(name="openai", version=f"=={version}")
        try:
            if not tool_installer._is_package_installed(dependency=dependency):
                tool_installer._pip_install(package=dependency.fullname())
        except ToolDependencyMismatch:
            tool_installer._pip_uninstall(package=dependency.fullname())
            tool_installer._pip_install(package=dependency.fullname())

    @abstractmethod
    def execute(self, question: QuestionSchema) -> AgentResponse:
        raise NotImplementedError
