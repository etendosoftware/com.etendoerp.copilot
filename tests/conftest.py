from copilot.app import app
from fastapi.testclient import TestClient
from pytest import fixture


@fixture(scope="session", autouse=True)
def client():
    """Create a test client."""
    return TestClient(app)
