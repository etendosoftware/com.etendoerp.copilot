"""Blueprint for the core module."""
import logging
from flask import Blueprint, render_template

core = Blueprint("core", __name__)

# pylint: disable=wrong-import-position
# This pylint error is disabled because the routes module needs to import the
# blueprint defined in this module.
from . import routes
