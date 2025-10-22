import os
from abc import abstractmethod
from dataclasses import dataclass
from enum import Enum
from typing import Final, Optional

from copilot.baseutils.logging_envvar import read_optional_env_var
from copilot.core import tool_installer
from copilot.core.exceptions import (
    OpenAIApiKeyNotFound,
    SystemPromptNotFound,
    ToolDependencyMismatch,
)
from copilot.core.schemas import QuestionSchema
from copilot.core.tool_dependencies import Dependency


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
    metadata: Optional[dict] = None


@dataclass
class AgentResponse:
    input: str
    output: AssistantResponse


class CopilotAgent:
    """Copilot Agent interface."""

    OPENAI_API_KEY: Final[str] = os.getenv("OPENAI_API_KEY")
    SYSTEM_PROMPT: Final[str] = read_optional_env_var(
        "SYSTEM_PROMPT",
        "You are a very powerful assistant with a set of tools, which you will try to use for the requests made to you.",
    )

    def __init__(self):
        # Use lazy import to avoid circular dependency
        from copilot.core.tool_loader import LangChainTools, ToolLoader

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
