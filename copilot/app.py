"""Initialize Flask app."""
from typing import Optional

from flask import Flask

from .core import core_blueprint
from .handlers import register_error_handlers


def register_apis(app: Flask):
    app.register_blueprint(core_blueprint)


def create_app(self, test_config: Optional[str] = None) -> Flask:
    """Create and configure an instance of the Flask application.

    Args:

    Returns:
        Flask: The Flask app.
    """
    app: Flask = Flask(__name__, instance_relative_config=True)

    if test_config is None:
        # load the instance config, if it exists, when not testing
        app.config.from_pyfile("config.py", silent=True)
    else:
        # load the test config if passed in
        app.config.from_mapping(test_config)

    register_apis(app)
    register_error_handlers(app)
    return app
