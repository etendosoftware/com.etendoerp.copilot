from copilot.core.exceptions import (
    ApplicationError,
    AssistantIdNotFound,
    AssistantTimeout,
    OpenAIApiKeyNotFound,
    SystemPromptNotFound,
    ToolConfigFileNotFound,
    ToolDependenciesFileNotFound,
    ToolDependencyMismatch,
    UnsupportedAgent,
)
from copilot.core.tool_dependencies import Dependency


def test_application_error_uses_default_message():
    assert str(ApplicationError()) == ApplicationError.message


def test_application_error_uses_custom_message():
    assert str(ApplicationError("custom failure")) == "custom failure"


def test_static_application_errors_return_their_class_messages():
    for error_cls in (
        OpenAIApiKeyNotFound,
        SystemPromptNotFound,
        ToolConfigFileNotFound,
        ToolDependenciesFileNotFound,
        UnsupportedAgent,
        AssistantTimeout,
    ):
        assert str(error_cls()) == error_cls.message


def test_tool_dependency_mismatch_includes_dependency_details():
    dependency = Dependency(name="sample-package", version=">=1.2.3")

    message = str(ToolDependencyMismatch(dependency, installed_version="1.0.0"))

    assert "sample-package" in message
    assert "Installed 1.0.0" in message
    assert "Required: >=1.2.3" in message


def test_assistant_id_not_found_includes_requested_id():
    assert str(AssistantIdNotFound("assistant-123")) == "No assistant found with id 'assistant-123'"
