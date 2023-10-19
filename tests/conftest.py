from fastapi.testclient import TestClient
from pytest import fixture


@fixture
def set_fake_openai_api_key(monkeypatch):
    with monkeypatch.context() as patch_context:
        patch_context.setenv("OPENAI_API_KEY", "fake-openai-key")
        yield


@fixture
def client(monkeypatch, set_fake_openai_api_key):
    """Create a test client."""
    from copilot.app import app

    return TestClient(app)
