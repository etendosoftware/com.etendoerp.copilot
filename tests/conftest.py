import json
import os
from typing import Final

from fastapi.testclient import TestClient
from pytest import fixture

FAKE_TOOL_CONFIG_FILEPATH: Final[str] = "/tmp/tools_config.json"


@fixture
def fake_valid_config_file(json_file_path: str = FAKE_TOOL_CONFIG_FILEPATH):
    with open(json_file_path, "w") as json_file:
        json.dump(
            {
                "native_tools": {"BastianFetcher": True, "XML_translation_tool": False},
                "third_party_tools": {"HelloWorldTool": True, "MyTool": False},
            },
            json_file,
            indent=4,
        )
    yield json_file_path
    import os

    os.remove(json_file_path)


@fixture
def set_fake_openai_api_key(monkeypatch, fake_valid_config_file):
    import copilot
    with monkeypatch.context() as patch_context:
        OPENAI_API_KEY: Final[str] = os.getenv("OPENAI_API_KEY")
        patch_context.setenv("OPENAI_API_KEY", OPENAI_API_KEY)
        patch_context.setenv(
            "SYSTEM_PROMPT", "You are very powerful assistant, but bad at calculating lengths of words"
        )
        patch_context.setenv("CONFIGURED_TOOLS_FILENAME", fake_valid_config_file)
        yield


@fixture
def client(monkeypatch, set_fake_openai_api_key):
    """Create a test client."""
    from copilot.app import app

    return TestClient(app)
