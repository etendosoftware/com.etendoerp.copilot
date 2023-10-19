from fastapi.testclient import TestClient
from pytest import fixture


@fixture()
def client(monkeypatch):
    """Create a test client."""
    with monkeypatch.context() as patch_context:
        patch_context.setenv("OPENAI_API_KEY", "fake-openai-key")
        from copilot.app import app

        return TestClient(app)
