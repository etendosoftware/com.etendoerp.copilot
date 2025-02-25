import json
import os

import requests
from langchain_openai_api_bridge.core.create_agent_dto import CreateAgentDto
from langchain_openai_api_bridge.fastapi import LangchainOpenaiApiBridgeFastAPI
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver

from copilot.core import api_router
from copilot.core.agent import LangchainAgent
from copilot.core.agent.langgraph_agent import LanggraphAgent, setup_graph, SQLLITE_NAME
from copilot.core.routes import _serve_question_sync, _initialize_agent
from copilot.core.schemas import QuestionSchema, AssistantSchema, GraphQuestionSchema
from copilot.core.threadcontext import request_context, ThreadContext
from copilot.handlers import register_error_handlers
from fastapi import FastAPI, Request
from starlette.responses import RedirectResponse

app: FastAPI = FastAPI(title="Copilot API")

def create_agent(dto: CreateAgentDto):
    headers = {
        "Authorization": "Bearer " + os.environ.get("CLASSIC_TOKEN")
    }

    url = os.environ.get("ETENDO_HOST_DOCKER") + "/sws/copilot/structure?app_id=" + os.environ.get("DEFAULT_ASSISTANT")

    payload = {}

    response = requests.request("GET", url, headers=headers, data=payload, timeout=1000)

    json_data = json.loads(response.text)
    json_data["question"] = ""
    copilot_agent = None
    if json_data["type"] == "langgraph":
        question = GraphQuestionSchema.model_validate(json_data)
        lang_graph, _ = setup_graph(question, None)
        copilot_agent = lang_graph.get_graph()
    if json_data["type"] == "langchain":
        question = QuestionSchema.model_validate(json_data)
        _, copilot_agent = _initialize_agent(question)
    if copilot_agent is None:
        raise ValueError("Agent type not recognized")
    ThreadContext.set_data("extra_info", {})
    return copilot_agent

bridge = LangchainOpenaiApiBridgeFastAPI(app=app, agent_factory_provider=create_agent)
bridge.bind_openai_chat_completion()
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
