from starlette.responses import RedirectResponse
from fastapi import FastAPI, Request

from copilot.core import api_router
from copilot.handlers import register_error_handlers

app: FastAPI = FastAPI(title="Copilot API")

app.include_router(api_router)

register_error_handlers(app)


@app.get("/", include_in_schema=False)
def get_root(request: Request):
    return RedirectResponse(url="/docs")
