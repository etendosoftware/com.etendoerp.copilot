"""Test copilot core."""
import pytest

from . import create_app


@pytest.fixture
def client_setup():
    """Create a test client."""
    app = create_app()
    app.config["TESTING"] = True

    with app.test_client() as client:
        yield client


def test_hello(client_setup):
    """Start with a blank page."""

    respose_value = client_setup.get("/")
    assert respose_value.data == b""
