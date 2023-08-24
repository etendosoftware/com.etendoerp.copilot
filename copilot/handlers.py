from typing import Tuple

from copilot.core import exceptions
from flask import Flask, Response, jsonify, request
from pydantic import ValidationError


def make_error_response(e: BaseException, message: str, status_code: int) -> Tuple[Response, int]:
    return (
        jsonify(
            error=e.__class__.__name__,
            description=message,
            status_code=status_code,
            request_id=request.headers.get("request-id"),
        ),
        status_code,
    )


def validation_error_handler(e):
    return make_error_response(e, str(e) or "Invalid request.", 400)


def application_error_handler(e):
    return make_error_response(e, str(e), 400)


def internal_error_handler(e):
    return make_error_response(e, str(e), 500)


def register_error_handlers(app: Flask):
    app.register_error_handler(ValidationError, validation_error_handler)
    app.register_error_handler(exceptions.ApplicationError, application_error_handler)
    app.register_error_handler(Exception, internal_error_handler)
