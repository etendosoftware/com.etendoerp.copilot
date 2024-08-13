"""
This module contains the main routes for the Copilot API.

The routes are responsible for handling the incoming requests and returning the responses.

"""
import asyncio
import json
import logging
import os
import shutil
import threading

import chromadb
from fastapi import APIRouter
from langchain.schema import Document
from langchain.vectorstores import Chroma
from langsmith import traceable
from starlette.responses import StreamingResponse



from copilot.core import utils
from copilot.core.agent import AgentResponse, copilot_agents, AgentEnum
from copilot.core.agent.agent import AssistantResponse
from copilot.core.agent.assistant_agent import AssistantAgent
from copilot.core.agent.langgraph_agent import LanggraphAgent
from copilot.core.exceptions import UnsupportedAgent
from copilot.core.local_history import ChatHistory, local_history_recorder
from copilot.core.schemas import QuestionSchema, GraphQuestionSchema, TextToVectorDBSchema, VectorDBInputSchema
from copilot.core.threadcontext import ThreadContext
from copilot.core.utils import copilot_debug, copilot_info
from copilot.core.vectordb_utils import get_embedding, get_vector_db_path, get_chroma_settings, handle_zip_file, \
    handle_other_formats, get_text_splitter

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

core_router = APIRouter()

current_agent = None


@traceable
def select_copilot_agent(copilot_type: str):
    if copilot_type not in copilot_agents:
        raise UnsupportedAgent()
    return copilot_agents[copilot_type]


@traceable
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


@traceable
async def gather_responses(agent, question, queue):
    try:
        async for agent_response in agent.aexecute(question):
            await queue.put(agent_response)
    except Exception as e:
        await queue.put(e)
    await queue.put(None)  # Signal that processing is done


@traceable
def _serve_question_sync(question: QuestionSchema):
    """Copilot endpoint for answering questions synchronously."""
    agent_type, copilot_agent = _initialize_agent(question)
    response = None
    try:
        response = _execute_agent(copilot_agent, question)
        local_history_recorder.record_chat(chat_question=question.question, chat_answer=response)
    except Exception as e:
        response = _handle_exception(e)
    return {"answer": response}


@traceable
def _initialize_agent(question: QuestionSchema):
    """Initialize and return the copilot agent."""
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
    ThreadContext.set_data('extra_info', question.extra_info)
    return agent_type, copilot_agent


@traceable
def _execute_agent(copilot_agent, question: QuestionSchema):
    """Execute the agent and return the response."""
    agent_response: AgentResponse = copilot_agent.execute(question)
    return agent_response.output


@traceable
def _handle_exception(e: Exception):
    """Handle exceptions and return an error response."""
    logger.exception(e)
    copilot_debug("  Exception: " + str(e))
    if hasattr(e, "response"):
        content = e.response.content
        error_message = json.loads(content).get('error').get('message')
    else:
        error_message = str(e)

    return {"error": {
        "code": e.response.status_code if hasattr(e, "response") else 500,
        "message": error_message}
    }


@traceable
@core_router.post("/graph")
def serve_graph(question: GraphQuestionSchema):
    """Copilot main endpdoint to answering questions."""
    copilot_agent = LanggraphAgent()
    copilot_info("  Current agent loaded: " + copilot_agent.__class__.__name__)
    copilot_debug("/question endpoint):")
    copilot_info("  Question: " + question.question)
    copilot_debug("  conversation_id: " + str(question.conversation_id))

    try:
        copilot_debug(
            "Thread " + str(threading.get_ident()) + " Saving extra info:" +
            str(ThreadContext.identifier_data()))
        ThreadContext.set_data('extra_info', question.extra_info)
        agent_response: AgentResponse = copilot_agent.execute(question)
        response = agent_response.output
        local_history_recorder.record_chat(chat_question=question.question,
                                           chat_answer=agent_response.output)
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


@traceable
def event_stream_graph(question: GraphQuestionSchema):
    responses = _serve_agraph(question)
    for response in responses:
        yield response


@traceable
@core_router.post("/agraph")
def serve_async_graph(question: GraphQuestionSchema):
    return StreamingResponse(event_stream_graph(question), media_type="text/event-stream")


@traceable
def _serve_agraph(question: GraphQuestionSchema):
    """Copilot main endpdoint to answering questions."""
    copilot_agent = LanggraphAgent()
    copilot_info("  Current agent loaded: " + copilot_agent.__class__.__name__)
    copilot_debug("/question endpoint):")
    copilot_info("  Question: " + question.question)
    copilot_debug("  conversation_id: " + str(question.conversation_id))

    try:
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
            elif isinstance(response, Exception):
                copilot_debug(f"Error ({str(type(response))}): {str(response)}")
                raise response
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

        response_error = AssistantResponse(
            response=error_message,
            conversation_id=question.conversation_id,
            role="error"
        )
        yield _response(response_error)


@traceable
@core_router.post("/question")
def serve_question(question: QuestionSchema):
    return _serve_question_sync(question)


@traceable
async def _serve_question_async(question: QuestionSchema):
    """Copilot endpoint for answering questions asynchronously."""
    agent_type, copilot_agent = _initialize_agent(question)
    response = None
    try:
        queue = asyncio.Queue()

        async def main():
            await asyncio.gather(
                gather_responses(copilot_agent, question, queue),
            )

        task = asyncio.create_task(main())
        while True:
            response = await queue.get()
            if response is None:
                break
            if isinstance(response, Exception):
                copilot_debug(f"Error: {str(response)}")
                continue
            yield _response(response)
        await task
    except Exception as e:
        response = _handle_exception(e)
        yield {"answer": response}


@traceable
async def event_stream(question: QuestionSchema):
    async for response in _serve_question_async(question):
        yield response


@traceable
@core_router.post("/aquestion")
async def serve_async_question(question: QuestionSchema):
    return StreamingResponse(event_stream(question), media_type="text/event-stream")


@traceable
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


@traceable
@core_router.get("/history")
def get_chat_history():
    chat_history: ChatHistory = local_history_recorder.get_chat_history()
    return chat_history


@traceable
@core_router.get("/assistant")
def serve_assistant():
    if not isinstance(current_agent, AssistantAgent):
        raise Exception("Copilot is not using AssistantAgent")

    return {"assistant_id": current_agent.get_assistant_id()}


@traceable
@core_router.post("/ResetVectorDB")
def resetVectorDB(body: VectorDBInputSchema):
    # Delete the VectorDB db if exists and create a new one
    kb_vectordb_id = body.kb_vectordb_id

    db_path = get_vector_db_path(kb_vectordb_id)

    db_client = chromadb.Client(settings=get_chroma_settings(db_path))
    db_client.reset()  # this will delete the db
    db_client.clear_system_cache()
    db_client = None
    if os.path.exists(db_path):
        shutil.rmtree(db_path)

    return {"answer": "VectorDB reset successfully."}


@traceable
@core_router.post("/addToVectorDB")
def processTextToVectorDB(body: TextToVectorDBSchema):
    kb_vectordb_id = body.kb_vectordb_id
    text = body.text
    extension = body.extension
    overwrite = body.overwrite

    db_path = get_vector_db_path(kb_vectordb_id)

    try:
        if overwrite and os.path.exists(db_path):
            os.remove(db_path)
        if extension == "zip":
            texts = handle_zip_file(text)
        else:
            parsed_document = handle_other_formats(extension, text)
            document = Document(page_content=parsed_document)
            text_splitter = get_text_splitter(extension)
            texts = text_splitter.split_documents([document])

        Chroma.from_documents(
            texts,
            get_embedding(),
            persist_directory=db_path,
            client_settings=get_chroma_settings()
        )

        success = True
        message = f"Database {kb_vectordb_id} created and loaded successfully."
    except Exception as e:
        success = False
        message = f"Error processing text to VectorDb: {e}"
        db_path = ""

    return {"answer": message, "success": success, "db_path": db_path}
