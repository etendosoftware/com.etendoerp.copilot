from pytest import fixture
from copilot.app import Copilot


@fixture
def client():
    """Create a test client."""
    app = Copilot().create_app()
    app.config["TESTING"] = True

    with app.test_client() as client:
        yield client