from copilot.core import exceptions
from fastapi import FastAPI, HTTPException, Request, status
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse


def pydanctic_validation_handler(request: Request, exc: RequestValidationError):
    for error in exc.errors():
        error["message"] = error.pop("msg")

    return JSONResponse(
        content=jsonable_encoder({"detail": exc.errors()}), status_code=status.HTTP_400_BAD_REQUEST
    )


def application_error_handler(request: Request, exc: HTTPException):
    return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST, content={"message": str(exc)})


def internal_error_handler(request: Request, exc: HTTPException):
    return JSONResponse(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, content={"message": str(exc)})


def register_error_handlers(app: FastAPI):
    app.add_exception_handler(RequestValidationError, pydanctic_validation_handler)
    app.add_exception_handler(exceptions.ApplicationError, application_error_handler)
    app.add_exception_handler(Exception, internal_error_handler)
