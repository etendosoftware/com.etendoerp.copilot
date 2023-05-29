"""Basic Flask server for serving the frontend.

This file contains the Flask server for serving the frontend. It is a very basic
"""

import logging
from flask import Flask, render_template

# Create a Flask app, using the static and template folders from a frontend build
app = Flask(
    __name__
)

# Define a route for the root URL, using the GET method
@app.route("/", methods=["GET"])
def serve_index():
    """Serves the home page of the website.

    Returns:
        str: The HTML content of the home page.
    """
    return render_template("index.html")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    app.run(debug=True, host="0.0.0.0", port=5000)
