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
import uuid
from pathlib import Path

import requests

import chromadb
from fastapi import APIRouter, UploadFile, File, Form
from langchain.vectorstores import Chroma
from langsmith import traceable
from starlette.responses import StreamingResponse
from fastapi import Header, HTTPException
from fastapi.responses import JSONResponse


from copilot.core import utils, etendo_utils
from copilot.core.agent import AgentResponse, copilot_agents, AgentEnum
from copilot.core.agent.agent import AssistantResponse
from copilot.core.agent.assistant_agent import AssistantAgent
from copilot.core.agent.langgraph_agent import LanggraphAgent
from copilot.core.exceptions import UnsupportedAgent
from copilot.core.local_history import ChatHistory, local_history_recorder
from copilot.core.schemas import (
    GraphQuestionSchema,
    QuestionSchema,
    VectorDBInputSchema,
)
from copilot.core.threadcontext import ThreadContext
from copilot.core.utils import copilot_debug, copilot_info
from copilot.core.vectordb_utils import (
    LANGCHAIN_DEFAULT_COLLECTION_NAME,
    get_chroma_settings,
    get_embedding,
    get_vector_db_path,
    handle_zip_file,
    index_file,
)
from fastapi import APIRouter, File, Form, UploadFile
from langchain_community.vectorstores import Chroma
from langsmith import traceable
from starlette.responses import StreamingResponse

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
        json_value = json.dumps(
            {
                "answer": {
                    "response": response.response,
                    "conversation_id": response.conversation_id,
                    "role": response.role,
                }
            }
        )
    else:
        json_value = json.dumps(
            {
                "answer": {
                    "response": response.output.response,
                    "conversation_id": response.output.conversation_id,
                    "role": response.output.role,
                }
            }
        )
    copilot_debug("data: " + json_value)
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
    ThreadContext.set_data("extra_info", question.extra_info)
    ThreadContext.set_data("conversation_id", question.conversation_id)
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
        error_message = json.loads(content).get("error").get("message")
    else:
        error_message = str(e)

    return {
        "error": {"code": e.response.status_code if hasattr(e, "response") else 500, "message": error_message}
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
            "Thread "
            + str(threading.get_ident())
            + " Saving extra info:"
            + str(ThreadContext.identifier_data())
        )
        ThreadContext.set_data("extra_info", question.extra_info)
        agent_response: AgentResponse = copilot_agent.execute(question)
        response = agent_response.output
        local_history_recorder.record_chat(chat_question=question.question, chat_answer=agent_response.output)
    except Exception as e:
        logger.exception(e)
        copilot_debug("  Exception: " + str(e))
        if hasattr(e, "response"):
            content = e.response.content
            # content has the json error message
            error_message = json.loads(content).get("error").get("message")
        else:
            error_message = str(e)

        response = {
            "error": {
                "code": e.response.status_code if hasattr(e, "response") else 500,
                "message": error_message,
            }
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
        ThreadContext.set_data("extra_info", question.extra_info)
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
            error_message = json.loads(content).get("error").get("message")
        else:
            error_message = str(e)

        response_error = AssistantResponse(
            response=error_message, conversation_id=question.conversation_id, role="error"
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
    try:
        kb_vectordb_id = body.kb_vectordb_id
        db_path = get_vector_db_path(kb_vectordb_id)

        # Initialize the database client
        db_client = chromadb.Client(settings=get_chroma_settings(db_path))
        # Fetch the collection, creating it if it doesn't exist
        collection = db_client.get_or_create_collection(LANGCHAIN_DEFAULT_COLLECTION_NAME)
        # Retrieve all documents from the collection
        documents = collection.get()
        document_ids = documents["ids"]
        metadatas = documents["metadatas"]
        # Ensure all documents have a 'purge': True in their metadata
        updated_metadatas = []
        for metadata in metadatas:
            if metadata is None:
                metadata = {}
            metadata["purge"] = True
            updated_metadatas.append(metadata)
        # Perform a single batch update with all document IDs and their updated metadata
        # Check if there are documents to update
        if updated_metadatas:
            collection.update(ids=document_ids, metadatas=updated_metadatas)
            copilot_debug("All documents were successfully updated with 'purge': True in the metadata.")
            db_client.clear_system_cache()
    except Exception as e:
        copilot_debug(f"Error resetting VectorDB: {e}")
        raise e
    return {"answer": "VectorDB marked for purge successfully."}


@traceable
@core_router.post("/addToVectorDB")
def process_text_to_vector_db(
    kb_vectordb_id: str = Form(...),
    filename: str = Form(None),
    extension: str = Form(...),
    overwrite: bool = Form(False),
    file: UploadFile = File(None),
):
    db_path = get_vector_db_path(kb_vectordb_id)

    try:
        if overwrite and os.path.exists(db_path):
            os.remove(db_path)
        import tempfile

        # Create a temporary directory using tempfile
        with tempfile.TemporaryDirectory() as temp_dir:
            # Define the file path inside the temporary directory
            file_path = Path(temp_dir) / file.filename
            with file_path.open("wb") as buffer:
                shutil.copyfileobj(file.file, buffer)
            chroma_client = chromadb.Client(settings=get_chroma_settings(db_path))
            if extension == "zip":
                # Process the ZIP file
                texts = handle_zip_file(file_path, chroma_client)

            else:
                texts = index_file(extension, file_path, chroma_client)
                # Remove the temporary file after use

            copilot_debug(f"Adding {len(texts)} documents to VectorDb.")
            if len(texts) > 0:
                Chroma.from_documents(
                    texts, get_embedding(), persist_directory=db_path, client_settings=get_chroma_settings()
                )
            success = True
            message = f"Database {kb_vectordb_id} created and loaded successfully."
            copilot_debug(message)
    except Exception as e:
        success = False
        message = f"Error processing text to VectorDb: {e}"
        copilot_debug(message)
        db_path = ""

    return {"answer": message, "success": success, "db_path": db_path}


@traceable
@core_router.post("/purgeVectorDB")
def purge_vectordb(body: VectorDBInputSchema):
    try:
        kb_vectordb_id = body.kb_vectordb_id
        db_path = get_vector_db_path(kb_vectordb_id)

        db_client = chromadb.Client(settings=get_chroma_settings(db_path))
        collection = db_client.get_or_create_collection(LANGCHAIN_DEFAULT_COLLECTION_NAME)
        copilot_debug(f"Collection: {collection.id}")
        # count the number of documents to be purged
        documents = collection.get(where={"purge": True})
        num_docs = len(documents["ids"])
        copilot_debug(f"Number of documents to be purged: {num_docs}")
        # Get all the documents from the collection that purged
        collection.delete(where={"purge": True})

        db_client.clear_system_cache()
    except Exception as e:
        copilot_debug(f"Error purging VectorDB: {e}")
        raise e
    return {"answer": "Documents marked for purge have been removed."}


@traceable
@core_router.get("/runningCheck")
def running_check():
    return {"answer": "docker" if utils.is_docker() else "pycharm"}


@traceable
@core_router.post("/attachFile")
def attach_file(file: UploadFile = File(...)):
    # save the file inside /tmp and return the path
    temp_file_path = Path(f"/copilotAttachedFiles/{uuid.uuid4()}/{file.filename}")
    temp_file_path.parent.mkdir(parents=True, exist_ok=True)
    with temp_file_path.open("wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
    return {"answer": str(temp_file_path)}

@traceable
@core_router.post("/checkCopilotHost")
def check_copilot_host(authorization: str = Header(None)):
    try:
        etendo_host_docker = etendo_utils.get_etendo_host()

        if not authorization or not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Authorization token is missing or invalid")

        if not etendo_host_docker:
            print("Error: ETENDO_HOST_DOCKER environment variable is not set")
            return

        url = f"{etendo_host_docker}/sws/copilot/configcheck"
        headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'Authorization': authorization
        }

        print(f"Connecting to {url}...")
        response = requests.post(url, headers=headers, json={})

        if response.status_code == 200:
            print("ETENDO_HOST_DOCKER successfully verified.")
            return {"status": "success", "message": "ETENDO_HOST_DOCKER successfully verified."}
        else:
            print(f"Error verifying ETENDO_HOST_DOCKER: code response {response.status_code}")
            return {"status": "success", "message": "Error verifying ETENDO_HOST_DOCKER."}


    except requests.exceptions.RequestException as e:
        print(f"Error verifying ETENDO_HOST_DOCKER: {str(e)}")

