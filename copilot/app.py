from copilot.core import api_router
from copilot.core.threadcontext import request_context
from copilot.core.tool_loader import ToolLoader
from copilot.handlers import register_error_handlers
from fastapi import FastAPI, Request
from starlette.responses import RedirectResponse

app: FastAPI = FastAPI(title="Copilot API")

app.include_router(api_router)

register_error_handlers(app)


@app.on_event("startup")
async def startup_event():
    """
    Startup event handler for the FastAPI application.

    This function is executed when the application starts up and is responsible
    for initializing critical components like the ToolLoader singleton.
    """
    # Clear any cached tools to ensure fresh loading
    ToolLoader._configured_tools = None
    ToolLoader._tools_module = None

    # Initialize ToolLoader singleton and load tools
    loader = ToolLoader()
    tools = loader.load_configured_tools()
    print(f"✅ ToolLoader singleton initialized with {len(tools)} tools loaded")

    # Print tool names for debugging
    for tool in tools:
        print(f"  - {tool.name}")


@app.middleware("http")
async def add_request_context(request: Request, call_next):
    """
    Middleware to add and manage a request-scoped context for each incoming request.

    This function sets up a new context for the current request using `request_context()`,
    ensuring that any context-specific data is isolated per request. It resets the context
    after the request is processed, regardless of whether an exception occurred.

    Args:
        request (Request): The incoming HTTP request object.
        call_next (Callable): The next middleware or route handler to call.

    Returns:
        Response: The HTTP response returned by the next handler.

    Raises:
        Any exception raised by downstream handlers is propagated after context reset.
    """
    token = request_context().set({})
    try:
        response = await call_next(request)
    finally:
        request_context().reset(token)
    return response


@app.get("/", include_in_schema=False)
def get_root(request: Request):
    """
    Handles the root endpoint by redirecting incoming requests to the documentation page.

    Args:
        request (Request): The incoming HTTP request object.

    Returns:
        RedirectResponse: A response object that redirects the client to the "/docs" URL.
    """
    return RedirectResponse(url="/docs")
