from typing import List, Optional, Literal

from langchain_openai import ChatOpenAI
from langchain_openai_api_bridge.core.create_agent_dto import CreateAgentDto
from langchain_openai_api_bridge.fastapi import LangchainOpenaiApiBridgeFastAPI
from pydantic import BaseModel, Field  # Changed import from onnxruntime to pydantic

from copilot.core import api_router, core_router
from copilot.core.threadcontext import request_context
from copilot.core.tool_input import ToolInput
from copilot.handlers import register_error_handlers
from fastapi import FastAPI, Request
from starlette.responses import RedirectResponse

app: FastAPI = FastAPI(title="Copilot API")


class ToolInput(BaseModel):
    title: str
    kind: Literal["code"]
    content: str


class OutputResult(BaseModel):
    type: Literal["tool_use"]
    name: Literal["createDocument"]
    id: str; input: ToolInput


class Data(BaseModel):
    content: List[OutputResult]

class LlmOutput(BaseModel):
    type: Literal["ai"]
    data: Data

class FunctionArg(BaseModel):
    name: Literal["viewUsage"]
    arguments: str

class FunctionCall(BaseModel):
    function: FunctionArg
    id: str
    index: int
    type: Literal["function"]


class UXOutput(BaseModel):
    kind: Literal["text-delta"]
    id: str
    tool_calls: List[FunctionCall]

def create_agent(dto: CreateAgentDto):
    llm = ChatOpenAI(
        #temperature=dto.temperature or 0.7,
        #model=dto.model,
        model="gpt-4o",
        #max_tokens=dto.max_tokens,
        #api_key=dto.api_key,
    )

    structured_llm = llm.with_structured_output(UXOutput, method="json_schema")

    return structured_llm


bridge = LangchainOpenaiApiBridgeFastAPI(app=app, agent_factory_provider=create_agent)
#bridge.bind_openai_chat_completion()

@app.middleware("http")
async def add_request_context(request: Request, call_next):
    token = request_context.set({})
    try:
        response = await call_next(request)
    finally:
        request_context.reset(token)
    return response


@app.get("/", include_in_schema=False)
def get_root(request: Request):
    return RedirectResponse(url="/docs")


app.include_router(api_router)
register_error_handlers(app)
