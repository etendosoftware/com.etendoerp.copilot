import asyncio
import json
import logging
import threading

from fastapi import APIRouter
from fsspec.asyn import loop
from starlette.responses import StreamingResponse
import threading

from . import utils
from .agent import AgentResponse, copilot_agents, AgentEnum
from .agent.agent import AssistantResponse
from .agent.assistant_agent import AssistantAgent
from .agent.langgraph_agent import LanggraphAgent
from .exceptions import UnsupportedAgent
from .local_history import ChatHistory, local_history_recorder
from .schemas import QuestionSchema, GraphQuestionSchema
from .threadcontext import ThreadContext
from .utils import copilot_debug, copilot_info

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

core_router = APIRouter()

current_agent = None


def select_copilot_agent(copilot_type: str):
    if copilot_type not in copilot_agents:
        raise UnsupportedAgent()
    return copilot_agents[copilot_type]

def _response(response: AssistantResponse):
    if type(response) == AssistantResponse:
        json_value = json.dumps({"answer": {
            "response": response.response,
            "conversation_id": response.conversation_id,
            "role": response.role
        }})
    else:
        json_value = json.dumps({"answer": {
            "response": response.output.response,
            "conversation_id": response.output.conversation_id,
            "role": response.output.role
        }})
    copilot_debug('data: ' + json_value)
    return "data: " + json_value + "\n"

async def gather_responses(agent, question, queue):
    try:
        async for agent_response in agent.aexecute(question):
            await queue.put(agent_response)
    except Exception as e:
        await queue.put(e)
    await queue.put(None)  # Signal that processing is done

def _serve_question(question: QuestionSchema):
    """Copilot main endpdoint to answering questions."""
    agent_type = question.type
    if agent_type is None:
        agent_type = utils.read_optional_env_var("AGENT_TYPE", AgentEnum.LANGCHAIN.value)
    copilot_agent = select_copilot_agent(agent_type)
    copilot_info("  Current agent loaded: " + copilot_agent.__class__.__name__)
    copilot_debug("/question endpoint):")
    copilot_info("  Question: " + question.question)
    copilot_debug("  agent_type: " + str(agent_type))
    copilot_debug("  assistant_id: " + str(question.assistant_id))
    copilot_debug("  conversation_id: " + str(question.conversation_id))
    copilot_debug("  file_ids: " + str(question.file_ids))

    response = None
    try:
        copilot_debug(
            "Thread " + str(threading.get_ident()) + " Saving extra info:" + str(ThreadContext.identifier_data()))
        ThreadContext.set_data('extra_info', question.extra_info)
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        queue = asyncio.Queue()
        async def main():
            await asyncio.gather(
                gather_responses(copilot_agent, question, queue),
            )
        task = loop.create_task(main())
        while True:
            response = loop.run_until_complete(queue.get())
            if response is None:
                break
            if isinstance(response, Exception):
                copilot_debug(f"Error: {str(response)}")
                continue
            yield _response(response)
        loop.run_until_complete(task)
        loop.close()
    except Exception as e:
        logger.exception(e)
        copilot_debug("  Exception: " + str(e))
        if hasattr(e, "response"):
            content = e.response.content
            # content has the json error message
            error_message = json.loads(content).get('error').get('message')
        else:
            error_message = str(e)

        response = {"error": {
            "code": e.response.status_code if hasattr(e, "response") else 500,
            "message": error_message}
        }

    return {"answer": response}


@core_router.post("/graph")
def serve_question(question: GraphQuestionSchema):
    """Copilot main endpdoint to answering questions."""
    copilot_agent = LanggraphAgent()
    copilot_info("  Current agent loaded: " + copilot_agent.__class__.__name__)
    copilot_debug("/question endpoint):")
    copilot_info("  Question: " + question.question)
    copilot_debug("  conversation_id: " + str(question.conversation_id))

    response = None
    try:
        copilot_debug(
            "Thread " + str(threading.get_ident()) + " Saving extra info:" + str(ThreadContext.identifier_data()))
        ThreadContext.set_data('extra_info', question.extra_info)
        agent_response: AgentResponse = copilot_agent.execute(question)
        response = agent_response.output
        local_history_recorder.record_chat(chat_question=question.question, chat_answer=agent_response.output)
    except Exception as e:
        logger.exception(e)
        copilot_debug("  Exception: " + str(e))
        if hasattr(e, "response"):
            content = e.response.content
            # content has the json error message
            error_message = json.loads(content).get('error').get('message')
        else:
            error_message = str(e)

        response = {"error": {
            "code": e.response.status_code if hasattr(e, "response") else 500,
            "message": error_message}
        }
        yield "data: {\"answer\": " + json.dumps(response) + "}\n"

@core_router.post("/question")
def serve_question(question: QuestionSchema):
    return _serve_question(question)

def event_stream(question: QuestionSchema):
    responses = _serve_question(question)
    for response in responses:
        yield response

@core_router.post("/aquestion")
def serve_async_question(question: QuestionSchema):
    return StreamingResponse(event_stream(question), media_type="text/event-stream")

@core_router.get("/tools")
def serve_tools():
    """Show tools available, with their information."""
    langchain_agent = select_copilot_agent(AgentEnum.LANGCHAIN.value)
    tool_list = langchain_agent.get_tools()
    tool_dict = {}
    for tool in tool_list:
        tool_dict[tool.name] = {
            "description": tool.description,
            "parameters": tool.args,
        }
    return {"answer": tool_dict}


@core_router.get("/history")
def get_chat_history():
    chat_history: ChatHistory = local_history_recorder.get_chat_history()
    return chat_history


@core_router.get("/assistant")
def serve_assistant():
    if not isinstance(current_agent, AssistantAgent):
        raise Exception("Copilot is not using AssistantAgent")

    return {"assistant_id": current_agent.get_assistant_id()}
