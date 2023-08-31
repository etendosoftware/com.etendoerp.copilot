from copilot.app import app
from pytest import fixture
from fastapi.testclient import TestClient


@fixture(scope="session", autouse=True)
def client():
    """Create a test client."""

    return TestClient(app)

