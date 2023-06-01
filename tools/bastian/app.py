"""This is a Flask app that can be used to test the Bastian module."""

from flask import Flask, jsonify, make_response
from flask_wtf.csrf import CSRFProtect


def create_app():
    """Create and configure an instance of the Flask application."""
    app = Flask(__name__)
    csrf = CSRFProtect(app)
    csrf.init_app(app)

    @app.route("/")
    def hello_from_root():
        return jsonify(message="Hello from root!")

    @app.route("/hello")
    def hello():
        return jsonify(message="Hello from path!")

    @app.errorhandler(404)
    def resource_not_found(error):  # pylint: disable=unused-argument
        return make_response(jsonify(error="Not found!"), 404)

    return app


if __name__ == "__main__":
    create_app().run(debug=True)
