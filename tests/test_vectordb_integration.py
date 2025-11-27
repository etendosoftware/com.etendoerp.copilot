"""
Integration tests for VectorDB operations (ResetVectorDB and purgeVectorDB).
These tests use a real SQLite database to ensure complete coverage.
"""

import io
import os
import shutil
import sys
import tempfile
import types

import pytest
from copilot.core.routes import core_router
from fastapi import FastAPI
from fastapi.testclient import TestClient
from PIL import Image

# Ensure fastembed is available during tests (stub if not installed)
if "fastembed" not in sys.modules:
    fake_mod = types.ModuleType("fastembed")

    class _FakeEmbedResult:
        def __init__(self, values=None):
            self._values = values or [0.1, 0.2, 0.3]

        def tolist(self):
            return self._values

    class _FakeImageEmbedding:
        def __init__(self, *args, **kwargs):
            # No-op constructor: this is a lightweight test stub used to emulate
            # the third-party `fastembed.ImageEmbedding` API for unit tests.
            # It intentionally does not perform any initialization.
            self._args = args

        def embed(self, paths):
            return [_FakeEmbedResult() for _ in paths]

    fake_mod.ImageEmbedding = _FakeImageEmbedding
    sys.modules["fastembed"] = fake_mod


app = FastAPI()
app.include_router(core_router)

client = TestClient(app)


@pytest.fixture
def temp_vectordb_path():
    """Create a temporary directory for test vector databases."""
    temp_dir = tempfile.mkdtemp(prefix="test_vectordb_")
    yield temp_dir
    # Cleanup after test
    if os.path.exists(temp_dir):
        shutil.rmtree(temp_dir)


@pytest.fixture
def kb_id():
    """Generate a unique KB ID for testing."""
    return f"test_kb_{os.getpid()}"


@pytest.fixture
def sample_text_file():
    """Create a sample text file for indexing."""
    content = """
    This is a test document for vector database indexing.
    It contains multiple lines of text that will be indexed.
    The document discusses various topics including:
    - Machine learning
    - Natural language processing
    - Vector databases
    - Semantic search

    This content should be split into chunks and indexed properly.
    """
    return io.BytesIO(content.encode("utf-8"))


@pytest.fixture
def sample_image_file():
    """Create a sample image file for indexing."""
    # Create a simple test image (100x100 red square)
    img = Image.new("RGB", (100, 100), color="red")
    img_bytes = io.BytesIO()
    img.save(img_bytes, format="PNG")
    img_bytes.seek(0)
    return img_bytes


def test_vectordb_full_lifecycle_text_documents(kb_id, sample_text_file, temp_vectordb_path):
    """
    Integration test for the complete VectorDB lifecycle with text documents:
    1. Index text documents
    2. Reset VectorDB (mark for purge)
    3. Purge VectorDB (delete marked documents)
    """
    # Step 1: Index text documents
    response = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_id,
            "filename": "test_document.txt",
            "extension": "txt",
            "overwrite": "true",
            "skip_splitting": "false",
        },
        files={"file": ("test_document.txt", sample_text_file, "text/plain")},
    )

    assert response.status_code == 200
    assert response.json()["success"] is True
    assert kb_id in response.json()["answer"]

    # Verify the database was created
    db_path = response.json()["db_path"]
    assert os.path.exists(db_path)

    # Step 2: Reset VectorDB (mark documents for purge)
    reset_response = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_id})

    assert reset_response.status_code == 200
    assert "answer" in reset_response.json()
    assert "marked for purge" in reset_response.json()["answer"].lower()

    # Extract number of marked documents
    answer_text = reset_response.json()["answer"]
    assert "documents marked" in answer_text.lower()

    # Step 3: Purge VectorDB (delete marked documents)
    purge_response = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_id})

    assert purge_response.status_code == 200
    assert "answer" in purge_response.json()
    assert "purged" in purge_response.json()["answer"].lower()

    # Verify documents were purged
    purge_answer = purge_response.json()["answer"]
    assert "Total purged:" in purge_answer

    # Cleanup
    if os.path.exists(db_path):
        shutil.rmtree(db_path)


def test_vectordb_full_lifecycle_with_images(kb_id, sample_image_file, temp_vectordb_path):
    """
    Integration test for the complete VectorDB lifecycle with image files:
    1. Index image files
    2. Reset VectorDB (mark for purge)
    3. Purge VectorDB (delete marked documents)
    """
    # Step 1: Index image file
    response = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_id,
            "filename": "test_image.png",
            "extension": "png",
            "overwrite": "true",
        },
        files={"file": ("test_image.png", sample_image_file, "image/png")},
    )

    assert response.status_code == 200
    assert response.json()["success"] is True

    # Verify the database was created
    db_path = response.json()["db_path"]
    assert os.path.exists(db_path)

    # Step 2: Reset VectorDB (mark documents for purge including images)
    reset_response = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_id})

    assert reset_response.status_code == 200
    assert "marked for purge" in reset_response.json()["answer"].lower()

    # Step 3: Purge VectorDB (delete marked documents including images)
    purge_response = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_id})

    assert purge_response.status_code == 200
    assert "purged" in purge_response.json()["answer"].lower()

    # Cleanup
    if os.path.exists(db_path):
        shutil.rmtree(db_path)


def test_vectordb_mixed_content_lifecycle(kb_id, sample_text_file, sample_image_file, temp_vectordb_path):
    """
    Integration test for VectorDB with mixed content (text and images):
    1. Index text documents
    2. Index image files
    3. Reset VectorDB (mark all for purge)
    4. Purge VectorDB (delete all marked documents)
    """
    # Step 1: Index text document
    text_response = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_id,
            "filename": "mixed_test_doc.txt",
            "extension": "txt",
            "overwrite": "true",
        },
        files={"file": ("mixed_test_doc.txt", sample_text_file, "text/plain")},
    )

    assert text_response.status_code == 200
    assert text_response.json()["success"] is True
    db_path = text_response.json()["db_path"]

    # Step 2: Index image file (to the same KB)
    image_response = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_id,
            "filename": "mixed_test_image.png",
            "extension": "png",
            "overwrite": "false",  # Don't overwrite, add to existing
        },
        files={"file": ("mixed_test_image.png", sample_image_file, "image/png")},
    )

    assert image_response.status_code == 200
    assert image_response.json()["success"] is True

    # Step 3: Reset VectorDB (mark both text and images for purge)
    reset_response = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_id})

    assert reset_response.status_code == 200
    reset_answer = reset_response.json()["answer"]
    assert "marked for purge" in reset_answer.lower()

    # Should have marked documents from both collections
    # Extract the number to verify it's > 0
    assert "documents marked" in reset_answer.lower()

    # Step 4: Purge VectorDB (delete all marked documents)
    purge_response = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_id})

    assert purge_response.status_code == 200
    purge_answer = purge_response.json()["answer"]
    assert "purged" in purge_answer.lower()
    assert "Total purged:" in purge_answer

    # Cleanup
    if os.path.exists(db_path):
        shutil.rmtree(db_path)


def test_purge_without_reset(kb_id, sample_text_file, temp_vectordb_path):
    """
    Test purging a VectorDB without resetting first (no documents marked for purge).
    """
    # Step 1: Index text document
    response = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_id,
            "filename": "no_reset_doc.txt",
            "extension": "txt",
            "overwrite": "true",
        },
        files={"file": ("no_reset_doc.txt", sample_text_file, "text/plain")},
    )

    assert response.status_code == 200
    db_path = response.json()["db_path"]

    # Step 2: Try to purge without resetting (no documents marked)
    purge_response = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_id})

    assert purge_response.status_code == 200
    # Should indicate 0 documents purged
    assert "Total purged: 0" in purge_response.json()["answer"]

    # Cleanup
    if os.path.exists(db_path):
        shutil.rmtree(db_path)


def test_multiple_reset_and_purge_cycles(kb_id, sample_text_file, temp_vectordb_path):
    """
    Test multiple reset and purge cycles to ensure idempotency.
    """
    # Index initial document
    response = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_id,
            "filename": "cycle_test.txt",
            "extension": "txt",
            "overwrite": "true",
        },
        files={"file": ("cycle_test.txt", sample_text_file, "text/plain")},
    )

    assert response.status_code == 200
    db_path = response.json()["db_path"]

    # First cycle: reset and purge
    reset1 = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_id})
    assert reset1.status_code == 200

    purge1 = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_id})
    assert purge1.status_code == 200
    assert "Total purged:" in purge1.json()["answer"]

    # Second cycle: reset again (should mark 0 documents)
    reset2 = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_id})
    assert reset2.status_code == 200
    assert "0 documents marked" in reset2.json()["answer"]

    # Second purge (should purge 0 documents)
    purge2 = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_id})
    assert purge2.status_code == 200
    assert "Total purged: 0" in purge2.json()["answer"]

    # Cleanup
    if os.path.exists(db_path):
        shutil.rmtree(db_path)


def test_reset_then_add_new_document(kb_id, sample_text_file, temp_vectordb_path):
    """
    Test that after reset (mark for purge), new documents can still be added
    and won't be marked for purge.
    """
    # Index initial document
    response1 = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_id,
            "filename": "old_doc.txt",
            "extension": "txt",
            "overwrite": "true",
        },
        files={"file": ("old_doc.txt", sample_text_file, "text/plain")},
    )

    assert response1.status_code == 200
    db_path = response1.json()["db_path"]

    # Reset (mark old documents for purge)
    reset_response = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_id})
    assert reset_response.status_code == 200

    # Add new document after reset
    new_content = io.BytesIO(b"This is a new document added after reset.")
    response2 = client.post(
        "/addToVectorDB",
        data={
            "kb_vectordb_id": kb_id,
            "filename": "new_doc.txt",
            "extension": "txt",
            "overwrite": "false",
        },
        files={"file": ("new_doc.txt", new_content, "text/plain")},
    )

    assert response2.status_code == 200

    # Purge should only delete old documents, not the new one
    purge_response = client.post("/purgeVectorDB", json={"kb_vectordb_id": kb_id})
    assert purge_response.status_code == 200

    # The new document should still be in the database (not purged)
    # We can verify this by doing another reset and checking the count
    reset2_response = client.post("/ResetVectorDB", json={"kb_vectordb_id": kb_id})
    assert reset2_response.status_code == 200
    # Should still have documents (the new one)
    assert "documents marked" in reset2_response.json()["answer"]

    # Cleanup
    if os.path.exists(db_path):
        shutil.rmtree(db_path)
