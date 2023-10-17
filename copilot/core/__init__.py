"""Blueprint for the core module."""

from fastapi import APIRouter, Depends

from .routes import core_router  # noqa:E402

api_router = APIRouter()

api_router.include_router(core_router, tags=["Copilot"])
