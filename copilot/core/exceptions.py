from .tool_dependencies import Dependency


class ApplicationError(RuntimeError):
    message = "There was an unexpected error, if this error persist contact support."

    def __init__(self, msg: str = None):
        self._message = msg or self.message

    def __str__(self) -> str:
        return self._message


class OpenAIApiKeyNotFound(ApplicationError):
    message = "The OpenAPI api-key is not found as environment variable"


class SystemPromptNotFound(ApplicationError):
    message = "The SYSTEM PROMPT variable is not found as environment variable"


class ToolConfigFileNotFound(ApplicationError):
    message = "The tools configuration file is not found as environment variable"


class ToolDependenciesFileNotFound(ApplicationError):
    message = "The tools dependencies file is not found as environment variable"


class ToolDependencyMismatch(ApplicationError):
    def __init__(self, dependency: Dependency, installed_version: str):
        message = (
            f"Dependency mismatch error for {dependency.name}. "
            f"Installed {installed_version}, Required: {dependency.version}"
        )
        super().__init__(msg=message)


class UnsupportedAgent(ApplicationError):
    message = "Unsupported agent. Please review AGENT_TYPE environment variable"


class AssistantIdNotFound(ApplicationError):
    def __init__(self, assistant_id: str):
        message = f"No assistant found with id '{assistant_id}'"
        super().__init__(msg=message)


class AssistantTimeout(ApplicationError):
    message = "Assistant agent connection error. Try it again."


class ToolException(Exception):
    pass
