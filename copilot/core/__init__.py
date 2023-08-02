"""Blueprint for the core module."""
import logging

from flask import Blueprint, render_template

core = Blueprint("core", __name__)

# TODO: EML-346
# fmt: off
# pylint: disable=wrong-import-position
# This pylint error is disabled because the routes module needs to import the
# blueprint defined in this module.
from . import routes  # noqa:E402
# fmt: on
