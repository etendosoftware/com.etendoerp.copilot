from copilot.app import create_app
from pytest import fixture


@fixture
def client():
    """Create a test client."""
    app = create_app(__name__)
    app.config["TESTING"] = True
    app.config["DEBUG"] = False

    with app.test_client() as client:
        yield client
