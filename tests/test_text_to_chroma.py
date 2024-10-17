import io
import os
import shutil
import zipfile
from dotenv import load_dotenv
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from httpx import AsyncClient

from copilot.core.routes import core_router

dotenv_path = os.path.join(os.path.dirname(__file__), '..', '.env')
load_dotenv(dotenv_path)

@pytest.mark.asyncio
async def test_process_text_to_vector_db_zip_file():
    app = FastAPI()
    app.include_router(core_router)

    async with AsyncClient(app=app, base_url="http://test") as client:
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
        response = await client.post(
            "/addToVectorDB",
            data={
                "kb_vectordb_id": kb_vectordb_id,
                "filename": filename,
                "extension": extension,
                "overwrite": str(overwrite).lower()
            },
            files={"file": (filename, zip_buffer, "application/zip")}
        )

        # Check a successful response
        assert response.status_code == 200
        assert response.json()["success"] is True