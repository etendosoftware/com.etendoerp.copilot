"""Test copilot core."""


def test_hello(client):
    """Start with a blank page."""

    respose_value = client.get("/")
    assert respose_value.data == b""
