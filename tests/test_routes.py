import os
from http import HTTPStatus
from typing import Dict, Type
from unittest import mock
from unittest.mock import MagicMock, patch

import pytest
from copilot.core import core_router
from copilot.core.agent.agent import AssistantResponse
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper
from fastapi import FastAPI
from fastapi.testclient import TestClient
from pytest import fixture

app = FastAPI()
app.include_router(core_router)

client = TestClient(app)


@fixture
def mocked_agent_response() -> AssistantResponse:
    return AssistantResponse(response="Mocked agent response", conversation_id="mocked_conversation_id")


@fixture
def mocked_agent(mocked_agent_response, monkeypatch):
    from copilot.core.agent import AgentResponse

    mocked_agent_executor = mock.MagicMock()
    mocked_agent_executor.execute = mock.MagicMock(
        return_value=AgentResponse(input="fake", output=mocked_agent_response)
    )

    with monkeypatch.context() as patch_context:
        patch_context.setenv("OPENAI_API_KEY", os.getenv("OPENAI_API_KEY"))
        patch_context.setenv("AGENT_TYPE", "langchain")
        from copilot.core import routes

        routes.select_copilot_agent = mocked_agent_executor


def test_copilot_question_with_wrong_payload(client):
    response = client.post("/question", json={})
    assert response.status_code == HTTPStatus.BAD_REQUEST
    assert response.json()["detail"][0]["message"] == "Field required"


def test_copilot_question_with_valid_payload(client, mocked_agent, mocked_agent_response):
    response = client.post(
        "/question", json={"question": "What is Etendo?", "provider": "langchain", "model": "gpt-4o"}
    )
    assert response.status_code == HTTPStatus.OK
    assert "answer" in response.json()
    assert response.json()["answer"] == {}


@fixture
def mock_langchain_agent():
    mock_agent = MagicMock()
    tools = []

    class DummyInput(ToolInput):
        query: str = ToolField(description="query to look up")

    class Tool1(ToolWrapper):
        name: str = "HelloWorldTool"
        description: str = "This is the classic HelloWorld tool implementation."
        args_schema: Type[ToolInput] = DummyInput
        return_direct: bool = False

        def run(self, input_params: Dict = None, *args, **kwarg) -> str:
            return {"message": "a"}

    tools.append(Tool1())
    mock_agent.get_tools.return_value = tools
    return mock_agent


@fixture
def mock_chat_history():
    return {"messages": ["Hello", "How are you?"]}


@patch("copilot.core.routes.select_copilot_agent")
def test_serve_tools(mock_select_copilot_agent, mock_langchain_agent):
    mock_select_copilot_agent.return_value = mock_langchain_agent
    response = client.get("/tools")
    assert response.status_code == 200
    response_json = response.json()

    answer = response_json.get("answer", {})
    assert "HelloWorldTool" in answer


@patch("copilot.core.routes.chromadb.Client")
@patch("copilot.core.routes.get_chroma_settings")
@patch("copilot.core.routes.get_vector_db_path")
def test_reset_vector_db_success(mock_get_path, mock_get_settings, mock_chromadb_client):
    """Test successful reset of VectorDB with both document and image collections."""
    # Setup mocks
    kb_vectordb_id = "test_kb_id"
    db_path = "/fake/path/to/db"
    mock_get_path.return_value = db_path
    mock_get_settings.return_value = MagicMock()

    # Mock collection with documents
    mock_collection = MagicMock()
    mock_collection.name = "langchain"
    mock_collection.get.return_value = {
        "ids": ["doc1", "doc2", "doc3"],
        "metadatas": [{"source": "file1.txt"}, {"source": "file2.txt"}, None],
    }

    # Mock images collection with documents
    mock_images_collection = MagicMock()
    mock_images_collection.name = "images"
    mock_images_collection.get.return_value = {
        "ids": ["img1", "img2"],
        "metadatas": [{"source": "image1.png"}, {"source": "image2.jpg"}],
    }

    mock_db_client_instance = MagicMock()
    mock_db_client_instance.get_or_create_collection.side_effect = [mock_collection, mock_images_collection]
    mock_chromadb_client.return_value = mock_db_client_instance

    # Make request
    response = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_vectordb_id})

    # Assertions
    assert response.status_code == 200
    assert "answer" in response.json()
    assert "5 documents marked" in response.json()["answer"]

    # Verify mocks were called correctly
    mock_get_path.assert_called_once_with(kb_vectordb_id)
    mock_get_settings.assert_called_once_with(db_path)
    mock_chromadb_client.assert_called_once()

    # Verify both collections were processed
    assert mock_db_client_instance.get_or_create_collection.call_count == 2

    # Verify update was called for both collections
    assert mock_collection.update.called
    assert mock_images_collection.update.called

    # Verify cache was cleared
    mock_db_client_instance.clear_system_cache.assert_called_once()


@patch("copilot.core.routes.chromadb.Client")
@patch("copilot.core.routes.get_chroma_settings")
@patch("copilot.core.routes.get_vector_db_path")
def test_reset_vector_db_empty_collections(mock_get_path, mock_get_settings, mock_chromadb_client):
    """Test reset of VectorDB with empty collections."""
    # Setup mocks
    kb_vectordb_id = "empty_kb_id"
    db_path = "/fake/path/to/empty_db"
    mock_get_path.return_value = db_path
    mock_get_settings.return_value = MagicMock()

    # Mock empty collections
    mock_collection = MagicMock()
    mock_collection.name = "langchain"
    mock_collection.get.return_value = {"ids": [], "metadatas": []}

    mock_images_collection = MagicMock()
    mock_images_collection.name = "images"
    mock_images_collection.get.return_value = {"ids": [], "metadatas": []}

    mock_db_client_instance = MagicMock()
    mock_db_client_instance.get_or_create_collection.side_effect = [mock_collection, mock_images_collection]
    mock_chromadb_client.return_value = mock_db_client_instance

    # Make request
    response = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_vectordb_id})

    # Assertions
    assert response.status_code == 200
    assert "0 documents marked" in response.json()["answer"]

    # Verify update was not called for empty collections
    assert not mock_collection.update.called
    assert not mock_images_collection.update.called


@patch("copilot.core.routes.chromadb.Client")
@patch("copilot.core.routes.get_chroma_settings")
@patch("copilot.core.routes.get_vector_db_path")
def test_reset_vector_db_large_batch(mock_get_path, mock_get_settings, mock_chromadb_client):
    """Test reset of VectorDB with large number of documents requiring batching."""
    # Setup mocks
    kb_vectordb_id = "large_kb_id"
    db_path = "/fake/path/to/large_db"
    mock_get_path.return_value = db_path
    mock_get_settings.return_value = MagicMock()

    # Mock collection with large number of documents (> 5000)
    num_docs = 12000
    mock_collection = MagicMock()
    mock_collection.name = "langchain"
    mock_collection.get.return_value = {
        "ids": [f"doc{i}" for i in range(num_docs)],
        "metadatas": [{"source": f"file{i}.txt"} for i in range(num_docs)],
    }

    mock_images_collection = MagicMock()
    mock_images_collection.name = "images"
    mock_images_collection.get.return_value = {"ids": [], "metadatas": []}

    mock_db_client_instance = MagicMock()
    mock_db_client_instance.get_or_create_collection.side_effect = [mock_collection, mock_images_collection]
    mock_chromadb_client.return_value = mock_db_client_instance

    # Make request
    response = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_vectordb_id})

    # Assertions
    assert response.status_code == 200
    assert f"{num_docs} documents marked" in response.json()["answer"]

    # Verify update was called multiple times for batching (12000/5000 = 3 batches)
    assert mock_collection.update.call_count == 3


@patch("copilot.core.routes.chromadb.Client")
@patch("copilot.core.routes.get_chroma_settings")
@patch("copilot.core.routes.get_vector_db_path")
def test_reset_vector_db_error_handling(mock_get_path, mock_get_settings, mock_chromadb_client):
    """Test error handling in reset VectorDB."""
    # Setup mocks to raise an exception
    kb_vectordb_id = "error_kb_id"
    mock_get_path.side_effect = Exception("Database connection error")

    # Make request and expect error - FastAPI will raise the exception
    with pytest.raises(Exception, match="Database connection error"):
        client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_vectordb_id})


@patch("copilot.core.routes.chromadb.Client")
@patch("copilot.core.routes.get_chroma_settings")
@patch("copilot.core.routes.get_vector_db_path")
def test_purge_vectordb_success(mock_get_path, mock_get_settings, mock_chromadb_client):
    """Test successful purge of VectorDB documents marked for deletion."""
    # Setup mocks
    kb_vectordb_id = "test_kb_id"
    db_path = "/fake/path/to/db"
    mock_get_path.return_value = db_path
    mock_get_settings.return_value = MagicMock()

    # Mock collection with documents marked for purge
    mock_collection = MagicMock()
    mock_collection.name = "langchain"
    mock_collection.get.return_value = {
        "ids": ["doc1", "doc2", "doc3"],
        "metadatas": [{"purge": True}, {"purge": True}, {"purge": True}],
    }

    # Mock images collection with documents marked for purge
    mock_images_collection = MagicMock()
    mock_images_collection.name = "images"
    mock_images_collection.get.return_value = {
        "ids": ["img1", "img2"],
        "metadatas": [{"purge": True}, {"purge": True}],
    }

    mock_db_client_instance = MagicMock()
    mock_db_client_instance.get_or_create_collection.side_effect = [mock_collection, mock_images_collection]
    mock_chromadb_client.return_value = mock_db_client_instance

    # Make request
    response = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_vectordb_id})

    # Assertions
    assert response.status_code == 200
    assert "answer" in response.json()
    assert "Total purged: 5" in response.json()["answer"]

    # Verify delete was called for both collections
    expected_filter = {"$and": [{"purge": True}, {"ad_client_id": "0"}]}
    mock_collection.delete.assert_called_once_with(where=expected_filter)
    mock_images_collection.delete.assert_called_once_with(where=expected_filter)

    # Verify cache was cleared
    mock_db_client_instance.clear_system_cache.assert_called_once()


@patch("copilot.core.routes.chromadb.Client")
@patch("copilot.core.routes.get_chroma_settings")
@patch("copilot.core.routes.get_vector_db_path")
def test_purge_vectordb_no_documents_to_purge(mock_get_path, mock_get_settings, mock_chromadb_client):
    """Test purge when no documents are marked for deletion."""
    # Setup mocks
    kb_vectordb_id = "empty_purge_kb_id"
    db_path = "/fake/path/to/db"
    mock_get_path.return_value = db_path
    mock_get_settings.return_value = MagicMock()

    # Mock collections with no documents to purge
    mock_collection = MagicMock()
    mock_collection.name = "langchain"
    mock_collection.get.return_value = {"ids": [], "metadatas": []}

    mock_images_collection = MagicMock()
    mock_images_collection.name = "images"
    mock_images_collection.get.return_value = {"ids": [], "metadatas": []}

    mock_db_client_instance = MagicMock()
    mock_db_client_instance.get_or_create_collection.side_effect = [mock_collection, mock_images_collection]
    mock_chromadb_client.return_value = mock_db_client_instance

    # Make request
    response = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_vectordb_id})

    # Assertions
    assert response.status_code == 200
    assert "Total purged: 0" in response.json()["answer"]

    # Verify delete was not called
    assert not mock_collection.delete.called
    assert not mock_images_collection.delete.called


@patch("copilot.core.routes.chromadb.Client")
@patch("copilot.core.routes.get_chroma_settings")
@patch("copilot.core.routes.get_vector_db_path")
def test_purge_vectordb_partial_collection_failure(mock_get_path, mock_get_settings, mock_chromadb_client):
    """Test purge continues when one collection fails."""
    # Setup mocks
    kb_vectordb_id = "partial_fail_kb_id"
    db_path = "/fake/path/to/db"
    mock_get_path.return_value = db_path
    mock_get_settings.return_value = MagicMock()

    # First collection succeeds
    mock_collection = MagicMock()
    mock_collection.name = "langchain"
    mock_collection.get.return_value = {
        "ids": ["doc1", "doc2"],
        "metadatas": [{"purge": True}, {"purge": True}],
    }

    # Second collection fails
    mock_images_collection = MagicMock()
    mock_images_collection.name = "images"
    mock_images_collection.get.side_effect = Exception("Collection error")

    mock_db_client_instance = MagicMock()
    mock_db_client_instance.get_or_create_collection.side_effect = [mock_collection, mock_images_collection]
    mock_chromadb_client.return_value = mock_db_client_instance

    # Make request
    response = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_vectordb_id})

    # Should still succeed and purge what it can
    assert response.status_code == 200
    assert "Total purged: 2" in response.json()["answer"]

    # First collection should be purged
    expected_filter = {"$and": [{"purge": True}, {"ad_client_id": "0"}]}
    mock_collection.delete.assert_called_once_with(where=expected_filter)


@patch("copilot.core.routes.chromadb.Client")
@patch("copilot.core.routes.get_chroma_settings")
@patch("copilot.core.routes.get_vector_db_path")
def test_purge_vectordb_error_handling(mock_get_path, mock_get_settings, mock_chromadb_client):
    """Test error handling in purge VectorDB."""
    # Setup mocks to raise an exception
    kb_vectordb_id = "error_kb_id"
    mock_get_path.side_effect = Exception("Database connection error")

    # Make request and expect error - FastAPI will raise the exception
    with pytest.raises(Exception, match="Database connection error"):
        client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_vectordb_id})
