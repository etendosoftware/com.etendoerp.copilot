from fastapi import FastAPI, Request
from starlette.responses import RedirectResponse

from langsmith import traceable


from copilot.core import api_router
from copilot.core.threadcontext import request_context
from copilot.handlers import register_error_handlers

app: FastAPI = FastAPI(title="Copilot API")

app.include_router(api_router)

register_error_handlers(app)


@traceable
@app.middleware("http")
async def add_request_context(request: Request, call_next):
    token = request_context.set({})
    try:
        response = await call_next(request)
    finally:
        request_context.reset(token)
    return response

@traceable
@app.get("/", include_in_schema=False)
def get_root(request: Request):
    return RedirectResponse(url="/docs")
