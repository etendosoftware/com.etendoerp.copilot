import os

import pytest
from dotenv import load_dotenv
from fastapi.testclient import TestClient

from copilot.core.routes import core_router
from copilot.core.schemas import TextToVectorDBSchema

dotenv_path = os.path.join(os.path.dirname(__file__), '..', '.env')
load_dotenv(dotenv_path)

client = TestClient(core_router)

body = TextToVectorDBSchema(kb_vectordb_id="test_db", text="Some text to process", overwrite=False, format="txt")


@pytest.fixture
def mock_os_path_exists(mocker):
    return mocker.patch("os.path.exists")


@pytest.fixture
def mock_chroma(mocker):
    return mocker.patch("copilot.core.routes.Chroma.from_documents")


def test_processTextToChromaDB_existing_db(mock_os_path_exists):
    mock_os_path_exists.return_value = True

    response = client.post("/addToVectorDB", json=body.dict())
    response_json = response.json()
    success = response_json["success"]
    message = response_json["answer"]
    db_path = response_json["db_path"]

    assert success == False
    assert message == "Database test_db already exists."
    assert db_path == "./vectordbs/test_db.db"


def test_processTextToChromaDB_overwrite(mock_os_path_exists, mock_chroma, mocker):
    mock_os_path_exists.return_value = True
    mock_remove = mocker.patch("os.remove")

    mock_chroma.return_value = None

    body_with_overwrite = body.copy(update={"overwrite": True})
    response = client.post("/addToVectorDB", json=body_with_overwrite.dict())
    response_json = response.json()
    success = response_json["success"]
    message = response_json["answer"]
    db_path = response_json["db_path"]

    assert success == True
    assert message == "Database test_db created and loaded successfully."
    assert db_path == "./vectordbs/test_db.db"
    mock_remove.assert_called_once_with(db_path)


def test_processTextToChromaDB_success(mock_os_path_exists, mock_chroma):
    mock_os_path_exists.return_value = False

    mock_chroma.return_value = None

    response = client.post("/addToVectorDB", json=body.dict())

    response_json = response.json()
    success = response_json["success"]
    message = response_json["answer"]
    db_path = response_json["db_path"]
    assert success == True
    assert message == "Database test_db created and loaded successfully."
    assert db_path == "./vectordbs/test_db.db"


def test_processTextToChromaDB_exception(mock_os_path_exists, mock_chroma):
    mock_os_path_exists.return_value = False

    mock_chroma.side_effect = Exception("Mocked exception")

    response = client.post("/addToVectorDB", json=body.dict())
    response_json = response.json()
    success = response_json["success"]
    message = response_json["answer"]
    db_path = response_json["db_path"]

    assert success == False
    assert "Error processing text to VectorDB" in message
    assert db_path == ""
