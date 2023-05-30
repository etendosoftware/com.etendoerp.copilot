"""Initialize Flask app."""
from flask import Flask, render_template

# Register blueprints
from .core import core as core_blueprint


class Copilot:
    """The Copilot app."""

    def run(self):
        """Run the app."""
        self.create_app().run(debug=True)

    def create_app(self, test_config=None):
        """Create and configure an instance of the Flask application.

        Args:

        Returns:
            Flask: The Flask app.
        """
        # create and configure the app
        __app = Flask(__name__, instance_relative_config=True)
        __app.config.from_mapping(
            SECRET_KEY="dev",
        )

        if test_config is None:
            # load the instance config, if it exists, when not testing
            __app.config.from_pyfile("config.py", silent=True)
        else:
            # load the test config if passed in
            __app.config.from_mapping(test_config)

        @__app.errorhandler(404)
        def page_not_found(error):
            # pylint: disable=unused-argument
            # note that we set the 404 status explicitly
            return render_template("404.html"), 404

        @__app.errorhandler(500)
        def internal_server_error(error):
            # pylint: disable=unused-argument
            return render_template("500.html"), 500

        __app.register_blueprint(core_blueprint)

        return __app


if __name__ == "__main__":
    app = Copilot()
    app.create_app().run(debug=True)
