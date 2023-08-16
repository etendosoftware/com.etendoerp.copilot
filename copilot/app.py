"""Initialize Flask app."""
import os

from flask import Flask, render_template

# Register blueprints
from .core import core as core_blueprint


class Copilot:
    """The Copilot app."""

    def run(self):
        """Run the app."""
        self.create_app().run(debug=True, host="0.0.0.0", port=os.getenv("COPILOT_PORT"))

    def create_app(self, test_config=None):
        """Create and configure an instance of the Flask application.

        Args:

        Returns:
            Flask: The Flask app.
        """
        app = Flask(__name__, instance_relative_config=True)

        if test_config is None:
            # load the instance config, if it exists, when not testing
            app.config.from_pyfile("config.py", silent=True)
        else:
            # load the test config if passed in
            app.config.from_mapping(test_config)

        @app.errorhandler(404)
        def page_not_found(error):
            # note that we set the 404 status explicitly
            return render_template("404.html"), 404

        @app.errorhandler(500)
        def internal_server_error(error):
            return render_template("500.html"), 500

        app.register_blueprint(core_blueprint)

        return app
