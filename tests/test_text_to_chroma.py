import io
import os
import shutil
import zipfile
from dotenv import load_dotenv
from fastapi.testclient import TestClient

from copilot.core.routes import core_router


dotenv_path = os.path.join(os.path.dirname(__file__), '..', '.env')
load_dotenv(dotenv_path)

client = TestClient(core_router)

def test_process_text_to_vector_db_zip_file():
    kb_vectordb_id = "test_kb_id"

    # If the directory exists, delete it
    db_path = "./vectordbs/test_kb_id.db"
    if os.path.exists(db_path):
        shutil.rmtree(db_path)

    # Set the input data
    filename = "test.zip"
    extension = "zip"
    overwrite = True

    # Create a ZIP file in memory
    zip_buffer = io.BytesIO()
    with zipfile.ZipFile(zip_buffer, 'w') as zip_file:
        zip_file.writestr("dummy.txt", "Dummy content inside zip file")
    zip_buffer.seek(0)

    # Simulate the upload of a ZIP file
    response = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_vectordb_id,
            "filename": filename,
            "extension": extension,
            "overwrite": str(overwrite).lower()  # Convert to string
        },
        files={"file": (filename, zip_buffer, "application/zip")}
    )

    # Check a successful response
    assert response.status_code == 200
    assert response.json()["success"] is True


def test_process_text_to_vector_db_without_file():
    kb_vectordb_id = "test_kb_id_2"

    # If the directory exists, delete it
    db_path = "./vectordbs/test_kb_id_2.db"
    if os.path.exists(db_path):
        shutil.rmtree(db_path)

    extension = "txt"
    overwrite = False
    filename = "filename"

    # Simulate the upload without a file
    response = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_vectordb_id,
            "filename": filename,
            "extension": extension,
            "overwrite": str(overwrite).lower()  # Convert to string
        }
    )

    # Check a successful response
    assert response.status_code == 200
    assert response.json()["success"] is False
    assert "Error processing text to VectorDb:" in response.json()["answer"]