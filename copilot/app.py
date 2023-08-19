"""Initialize Flask app."""
from typing import Optional

from flask import Flask

from .core import core_blueprint


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

    @app.errorhandler(404)
    def page_not_found(error):
        return "not found", 404

    @app.errorhandler(500)
    def internal_server_error(error):
        return "interal server error", 500

    app.register_blueprint(core_blueprint)

    return app
