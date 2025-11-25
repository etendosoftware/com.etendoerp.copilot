"""
Unit tests for vectordb_utils module.
Comprehensive coverage of all functions in copilot/core/vectordb_utils.py.
"""

import base64
import hashlib

# Ensure fastembed is importable for tests that patch it
import sys
import types
import zipfile
from pathlib import Path
from unittest.mock import MagicMock, Mock, mock_open, patch

import pytest
from chromadb import Settings
from copilot.core.schemas import SplitterConfig
from copilot.core.vectordb_utils import (
    ALLOWED_EXTENSIONS,
    IMAGE_EXTENSIONS,
    IMAGES_COLLECTION_NAME,
    LANGCHAIN_DEFAULT_COLLECTION_NAME,
    calculate_file_md5,
    calculate_sha256_from_file_path,
    debug_metadata,
    filter_by_distance,
    find_similar_reference,
    get_chroma_settings,
    get_embedding,
    get_image_collection_name,
    get_plain_text_splitter,
    get_sim_threshold,
    get_sim_threshold_with_ignore,
    get_text_splitter,
    get_vector_db_path,
    handle_zip_file,
    hash_splitter_config,
    image_to_base64,
    index_file,
    index_image_file,
    load_chroma_collection_from_path,
    log_image_origin,
    process_file,
    process_pdf,
)
from langchain_core.documents import Document

if "fastembed" not in sys.modules:
    fake_fastembed = types.ModuleType("fastembed")

    class _DummyEmbed:
        def __init__(self, *args, **kwargs):
            # No-op constructor: stub for tests, real implementation not required
            # The presence of this no-op is intentional to allow patching in tests.
            return None

        def embed(self, items):
            class _R:
                def __init__(self, vals):
                    self._vals = vals

                def tolist(self):
                    return self._vals

            return [_R([0.1, 0.2, 0.3]) for _ in items]

    fake_fastembed.ImageEmbedding = _DummyEmbed
    sys.modules["fastembed"] = fake_fastembed


class TestGetEmbedding:
    """Tests for get_embedding function."""

    @patch("copilot.core.vectordb_utils.get_proxy_url")
    @patch("copilot.core.vectordb_utils.OpenAIEmbeddings")
    def test_get_embedding_creates_embeddings_with_proxy(self, mock_embeddings, mock_get_proxy_url):
        """Test that get_embedding creates OpenAIEmbeddings with correct config."""
        mock_get_proxy_url.return_value = "http://proxy.example.com"

        result = get_embedding()

        mock_embeddings.assert_called_once_with(
            disallowed_special=(), show_progress_bar=True, base_url="http://proxy.example.com"
        )
        assert result == mock_embeddings.return_value

    @patch("copilot.core.vectordb_utils.get_proxy_url")
    @patch("copilot.core.vectordb_utils.OpenAIEmbeddings")
    def test_get_embedding_uses_proxy_url(self, mock_embeddings, mock_get_proxy_url):
        """Test that proxy URL is properly fetched and used."""
        expected_url = "http://test-proxy.local:8080"
        mock_get_proxy_url.return_value = expected_url

        get_embedding()

        assert mock_embeddings.call_args[1]["base_url"] == expected_url


class TestGetVectorDbPath:
    """Tests for get_vector_db_path function."""

    def test_get_vector_db_path_in_docker(self, tmp_path, monkeypatch):
        """Test vector DB path when running in Docker environment."""
        # Create a fake /app directory
        app_dir = tmp_path / "app"
        app_dir.mkdir()

        with patch("os.path.exists") as mock_exists:
            # /app exists, but /app/vectordbs doesn't exist yet
            mock_exists.side_effect = lambda path: path == "/app"
            with patch("os.makedirs") as mock_makedirs:
                result = get_vector_db_path("test_db_id")

                assert result == "/app/vectordbs/test_db_id.db"
                mock_makedirs.assert_called_once_with("/app/vectordbs", exist_ok=True)

    def test_get_vector_db_path_local(self, tmp_path, monkeypatch):
        """Test vector DB path in local environment."""
        with patch("os.path.exists") as mock_exists:
            mock_exists.return_value = False
            with patch("os.makedirs") as mock_makedirs:
                result = get_vector_db_path("local_db_id")

                assert result == "./vectordbs/local_db_id.db"
                mock_makedirs.assert_called_once_with("./vectordbs", exist_ok=True)

    def test_get_vector_db_path_creates_directory(self, tmp_path, monkeypatch):
        """Test that vectordbs directory is created if it doesn't exist."""
        monkeypatch.chdir(tmp_path)

        with patch("os.path.exists") as mock_exists:
            mock_exists.side_effect = lambda path: path != "/app" and not path.endswith("vectordbs")

            with patch("os.makedirs") as mock_makedirs:
                get_vector_db_path("new_db")

                mock_makedirs.assert_called_once()


class TestGetChromaSettings:
    """Tests for get_chroma_settings function."""

    def test_get_chroma_settings_with_db_path(self):
        """Test ChromaDB settings creation with database path."""
        db_path = "/tmp/test_chroma_db"

        settings = get_chroma_settings(db_path)

        assert isinstance(settings, Settings)
        assert settings.persist_directory == db_path
        assert settings.is_persistent is True
        assert settings.allow_reset is True

    def test_get_chroma_settings_without_db_path(self):
        """Test ChromaDB settings creation without database path."""
        settings = get_chroma_settings(None)

        assert isinstance(settings, Settings)
        assert settings.is_persistent is True
        assert settings.allow_reset is True


class TestHashSplitterConfig:
    """Tests for hash_splitter_config function."""

    def test_hash_splitter_config_basic(self):
        """Test hashing of basic SplitterConfig."""
        config = SplitterConfig(max_chunk_size=1000, chunk_overlap=200)

        hash_value = hash_splitter_config(config)

        assert isinstance(hash_value, str)
        assert len(hash_value) == 64  # SHA-256 produces 64 hex characters

    def test_hash_splitter_config_consistency(self):
        """Test that same config produces same hash."""
        config1 = SplitterConfig(max_chunk_size=500, chunk_overlap=100)
        config2 = SplitterConfig(max_chunk_size=500, chunk_overlap=100)

        hash1 = hash_splitter_config(config1)
        hash2 = hash_splitter_config(config2)

        assert hash1 == hash2

    def test_hash_splitter_config_different(self):
        """Test that different configs produce different hashes."""
        config1 = SplitterConfig(max_chunk_size=500, chunk_overlap=100)
        config2 = SplitterConfig(max_chunk_size=1000, chunk_overlap=200)

        hash1 = hash_splitter_config(config1)
        hash2 = hash_splitter_config(config2)

        assert hash1 != hash2

    def test_hash_splitter_config_with_skip_splitting(self):
        """Test hashing with skip_splitting parameter."""
        config1 = SplitterConfig(skip_splitting=True)
        config2 = SplitterConfig(skip_splitting=False)

        hash1 = hash_splitter_config(config1)
        hash2 = hash_splitter_config(config2)

        assert hash1 != hash2


class TestGetTextSplitter:
    """Tests for get_text_splitter function."""

    def test_get_text_splitter_markdown(self):
        """Test text splitter for markdown files."""
        from langchain.text_splitter import MarkdownTextSplitter

        config = SplitterConfig()
        splitter = get_text_splitter("md", config)

        assert isinstance(splitter, MarkdownTextSplitter)

    def test_get_text_splitter_markdown_extension(self):
        """Test text splitter for .markdown extension."""
        from langchain.text_splitter import MarkdownTextSplitter

        config = SplitterConfig()
        splitter = get_text_splitter("markdown", config)

        assert isinstance(splitter, MarkdownTextSplitter)

    def test_get_text_splitter_plain_text(self):
        """Test text splitter for plain text files."""
        from langchain.text_splitter import CharacterTextSplitter

        config = SplitterConfig()
        splitter = get_text_splitter("txt", config)

        assert isinstance(splitter, CharacterTextSplitter)

    def test_get_text_splitter_pdf(self):
        """Test text splitter for PDF files."""
        from langchain.text_splitter import CharacterTextSplitter

        config = SplitterConfig()
        splitter = get_text_splitter("pdf", config)

        assert isinstance(splitter, CharacterTextSplitter)

    def test_get_text_splitter_xml(self):
        """Test text splitter for XML files."""
        from langchain.text_splitter import CharacterTextSplitter

        config = SplitterConfig()
        splitter = get_text_splitter("xml", config)

        assert isinstance(splitter, CharacterTextSplitter)

    def test_get_text_splitter_json(self):
        """Test text splitter for JSON files."""
        from langchain_text_splitters import RecursiveJsonSplitter

        config = SplitterConfig(max_chunk_size=2000)
        splitter = get_text_splitter("json", config)

        assert isinstance(splitter, RecursiveJsonSplitter)
        assert splitter.max_chunk_size == 2000

    def test_get_text_splitter_java(self):
        """Test text splitter for Java files."""
        from langchain_text_splitters import RecursiveCharacterTextSplitter

        config = SplitterConfig()
        splitter = get_text_splitter("java", config)

        assert isinstance(splitter, RecursiveCharacterTextSplitter)

    def test_get_text_splitter_javascript(self):
        """Test text splitter for JavaScript files."""
        from langchain_text_splitters import RecursiveCharacterTextSplitter

        config = SplitterConfig()
        splitter = get_text_splitter("js", config)

        assert isinstance(splitter, RecursiveCharacterTextSplitter)

    def test_get_text_splitter_python(self):
        """Test text splitter for Python files."""
        from langchain_text_splitters import RecursiveCharacterTextSplitter

        config = SplitterConfig()
        splitter = get_text_splitter("py", config)

        assert isinstance(splitter, RecursiveCharacterTextSplitter)

    def test_get_text_splitter_unsupported_extension(self):
        """Test error handling for unsupported file extension."""
        config = SplitterConfig()

        with pytest.raises(ValueError, match="Unsupported file extension"):
            get_text_splitter("unsupported", config)


class TestGetPlainTextSplitter:
    """Tests for get_plain_text_splitter function."""

    def test_get_plain_text_splitter_default(self):
        """Test plain text splitter with default config."""
        from langchain.text_splitter import CharacterTextSplitter

        config = SplitterConfig()
        splitter = get_plain_text_splitter(config)

        assert isinstance(splitter, CharacterTextSplitter)

    def test_get_plain_text_splitter_with_chunk_size(self):
        """Test plain text splitter with custom chunk size."""
        config = SplitterConfig(max_chunk_size=500)
        splitter = get_plain_text_splitter(config)

        assert splitter._chunk_size == 500

    def test_get_plain_text_splitter_with_overlap(self):
        """Test plain text splitter with custom overlap."""
        config = SplitterConfig(chunk_overlap=100)
        splitter = get_plain_text_splitter(config)

        assert splitter._chunk_overlap == 100

    def test_get_plain_text_splitter_with_both_params(self):
        """Test plain text splitter with both chunk size and overlap."""
        config = SplitterConfig(max_chunk_size=800, chunk_overlap=150)
        splitter = get_plain_text_splitter(config)

        assert splitter._chunk_size == 800
        assert splitter._chunk_overlap == 150

    def test_get_plain_text_splitter_none_config(self):
        """Test plain text splitter with None config."""
        splitter = get_plain_text_splitter(None)

        assert splitter is not None


class TestProcessPdf:
    """Tests for process_pdf function."""

    @patch("copilot.core.vectordb_utils.pymupdf")
    def test_process_pdf_extracts_text(self, mock_pymupdf):
        """Test PDF text extraction."""
        # Mock PDF document and pages
        mock_page1 = Mock()
        mock_page1.get_text.return_value = "Page 1 content\n"
        mock_page2 = Mock()
        mock_page2.get_text.return_value = "Page 2 content\n"

        mock_doc = MagicMock()
        mock_doc.__iter__.return_value = iter([mock_page1, mock_page2])
        mock_pymupdf.open.return_value = mock_doc

        pdf_data = b"fake pdf binary data"

        result = process_pdf(pdf_data)

        assert result == "Page 1 content\nPage 2 content\n"
        mock_page1.get_text.assert_called_once()
        mock_page2.get_text.assert_called_once()

    @patch("copilot.core.vectordb_utils.pymupdf")
    @patch("builtins.open", new_callable=mock_open)
    def test_process_pdf_creates_temp_file(self, mock_file, mock_pymupdf):
        """Test that process_pdf creates temporary file."""
        mock_doc = MagicMock()
        mock_doc.__iter__.return_value = iter([])
        mock_pymupdf.open.return_value = mock_doc

        pdf_data = b"test pdf data"

        process_pdf(pdf_data)

        mock_file.assert_called_once()
        assert mock_file.call_args[0][0].startswith("/tmp/temp")
        assert mock_file.call_args[0][0].endswith(".pdf")


class TestCalculateSha256FromFilePath:
    """Tests for calculate_sha256_from_file_path function."""

    def test_calculate_sha256_from_file_path(self, tmp_path):
        """Test SHA-256 calculation from file."""
        test_file = tmp_path / "test.txt"
        test_content = b"Hello, World!"
        test_file.write_bytes(test_content)

        result = calculate_sha256_from_file_path(str(test_file))

        expected = hashlib.sha256(test_content).hexdigest()
        assert result == expected

    def test_calculate_sha256_from_file_path_large_file(self, tmp_path):
        """Test SHA-256 calculation with large file."""
        test_file = tmp_path / "large.bin"
        test_content = b"X" * 10000  # 10KB file
        test_file.write_bytes(test_content)

        result = calculate_sha256_from_file_path(str(test_file))

        expected = hashlib.sha256(test_content).hexdigest()
        assert result == expected

    def test_calculate_sha256_different_chunk_size(self, tmp_path):
        """Test SHA-256 with different chunk sizes."""
        test_file = tmp_path / "test.bin"
        test_content = b"Test content for chunking"
        test_file.write_bytes(test_content)

        result1 = calculate_sha256_from_file_path(str(test_file), chunk_size=1024)
        result2 = calculate_sha256_from_file_path(str(test_file), chunk_size=64)

        # Same hash regardless of chunk size
        assert result1 == result2


class TestProcessFile:
    """Tests for process_file function."""

    def test_process_file_text(self, tmp_path):
        """Test processing text file."""
        test_file = tmp_path / "test.txt"
        test_content = "Hello, World!"
        test_file.write_text(test_content)

        content, sha256 = process_file(str(test_file), "txt")

        assert content == test_content
        assert len(sha256) == 64  # SHA-256 hex length

    @patch("copilot.core.vectordb_utils.process_pdf")
    def test_process_file_pdf(self, mock_process_pdf, tmp_path):
        """Test processing PDF file."""
        mock_process_pdf.return_value = "Extracted PDF text"

        test_file = tmp_path / "test.pdf"
        test_file.write_bytes(b"fake pdf data")

        content, _ = process_file(str(test_file), "pdf")

        assert content == "Extracted PDF text"
        mock_process_pdf.assert_called_once_with(b"fake pdf data")

    def test_process_file_returns_consistent_hash(self, tmp_path):
        """Test that same file produces same hash."""
        test_file = tmp_path / "consistent.txt"
        test_file.write_text("Consistent content")

        _, hash1 = process_file(str(test_file), "txt")
        _, hash2 = process_file(str(test_file), "txt")

        assert hash1 == hash2


class TestIndexFile:
    """Tests for index_file function."""

    @patch("copilot.core.vectordb_utils.process_file")
    @patch("copilot.core.vectordb_utils.get_text_splitter")
    def test_index_file_new_document(self, mock_get_splitter, mock_process_file, tmp_path):
        """Test indexing a new file."""
        mock_process_file.return_value = ("File content", "abc123")
        mock_splitter = Mock()
        mock_splitter.split_documents.return_value = [
            Document(page_content="Chunk 1"),
            Document(page_content="Chunk 2"),
        ]
        mock_get_splitter.return_value = mock_splitter

        # Create mock ChromaDB client
        mock_collection = Mock()
        mock_collection.get.return_value = {"ids": []}
        mock_client = Mock()
        mock_client.get_or_create_collection.return_value = mock_collection

        config = SplitterConfig(max_chunk_size=100)

        result = index_file("txt", str(tmp_path / "test.txt"), mock_client, config)

        assert len(result) == 2
        assert result[0].page_content == "Chunk 1"
        assert result[1].page_content == "Chunk 2"

    @patch("copilot.core.vectordb_utils.process_file")
    def test_index_file_existing_document(self, mock_process_file, tmp_path):
        """Test indexing a file that already exists."""
        mock_process_file.return_value = ("File content", "existing_hash")

        # Mock collection with existing document
        mock_collection = Mock()
        mock_collection.get.return_value = {"ids": ["doc1"], "metadatas": [{"md5": "existing_hash"}]}
        mock_client = Mock()
        mock_client.get_or_create_collection.return_value = mock_collection

        config = SplitterConfig()

        result = index_file("txt", str(tmp_path / "test.txt"), mock_client, config)

        assert result == []
        mock_collection.update.assert_called_once()

    @patch("copilot.core.vectordb_utils.process_file")
    @patch("copilot.core.vectordb_utils.get_text_splitter")
    def test_index_file_skip_splitting(self, mock_get_splitter, mock_process_file, tmp_path):
        """Test indexing with skip_splitting enabled."""
        mock_process_file.return_value = ("File content", "hash123")
        mock_get_splitter.return_value = Mock()

        mock_collection = Mock()
        mock_collection.get.return_value = {"ids": []}
        mock_client = Mock()
        mock_client.get_or_create_collection.return_value = mock_collection

        config = SplitterConfig(skip_splitting=True)

        result = index_file("txt", str(tmp_path / "test.txt"), mock_client, config)

        # Should return single document without splitting
        assert len(result) == 1
        assert result[0].page_content == "File content"


class TestHandleZipFile:
    """Tests for handle_zip_file function."""

    @patch("copilot.core.vectordb_utils.index_file")
    def test_handle_zip_file_basic(self, mock_index_file, tmp_path):
        """Test basic ZIP file handling."""
        # Create a test ZIP file
        zip_path = tmp_path / "test.zip"
        with zipfile.ZipFile(zip_path, "w") as zf:
            zf.writestr("file1.txt", "Content 1")
            zf.writestr("file2.py", "print('hello')")

        mock_index_file.return_value = [Document(page_content="test")]
        mock_client = Mock()
        config = SplitterConfig()

        result = handle_zip_file(str(zip_path), mock_client, config)

        assert len(result) == 2
        assert mock_index_file.call_count == 2

    @patch("copilot.core.vectordb_utils.index_file")
    def test_handle_zip_file_filters_extensions(self, mock_index_file, tmp_path):
        """Test that ZIP handler only processes allowed extensions."""
        zip_path = tmp_path / "test.zip"
        with zipfile.ZipFile(zip_path, "w") as zf:
            zf.writestr("file.txt", "Content")
            zf.writestr("file.exe", "Binary")  # Should be skipped
            zf.writestr("file.pdf", "PDF content")

        mock_index_file.return_value = [Document(page_content="test")]
        mock_client = Mock()
        config = SplitterConfig()

        handle_zip_file(str(zip_path), mock_client, config)

        # Should only index txt and pdf, not exe
        assert mock_index_file.call_count == 2

    @patch("copilot.core.vectordb_utils.index_file")
    def test_handle_zip_file_continues_on_error(self, mock_index_file, tmp_path):
        """Test that ZIP handler continues processing after error."""
        zip_path = tmp_path / "test.zip"
        with zipfile.ZipFile(zip_path, "w") as zf:
            zf.writestr("file1.txt", "Content 1")
            zf.writestr("file2.txt", "Content 2")
            zf.writestr("file3.txt", "Content 3")

        # First call fails, others succeed
        mock_index_file.side_effect = [
            Exception("Error"),
            [Document(page_content="doc2")],
            [Document(page_content="doc3")],
        ]
        mock_client = Mock()
        config = SplitterConfig()

        result = handle_zip_file(str(zip_path), mock_client, config)

        # Should have 2 documents (error one is skipped)
        assert len(result) == 2


class TestLoadChromaCollectionFromPath:
    """Tests for load_chroma_collection_from_path function."""

    @patch("copilot.core.vectordb_utils.chromadb.Client")
    def test_load_chroma_collection_from_path(self, mock_client_class):
        """Test loading Chroma collection from path."""
        mock_collection = Mock()
        mock_client = Mock()
        mock_client.get_or_create_collection.return_value = mock_collection
        mock_client_class.return_value = mock_client

        db_path = "/tmp/test_db"

        result = load_chroma_collection_from_path(db_path)

        assert result == mock_collection
        mock_client.get_or_create_collection.assert_called_once_with(LANGCHAIN_DEFAULT_COLLECTION_NAME)


class TestImageToBase64:
    """Tests for image_to_base64 function."""

    def test_image_to_base64(self, tmp_path):
        """Test converting image to base64."""
        test_image = tmp_path / "test.png"
        test_content = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR"  # PNG header
        test_image.write_bytes(test_content)

        result = image_to_base64(str(test_image))

        # Verify it's valid base64
        decoded = base64.b64decode(result)
        assert decoded == test_content

    def test_image_to_base64_returns_string(self, tmp_path):
        """Test that result is a string."""
        test_image = tmp_path / "test.jpg"
        test_image.write_bytes(b"fake image data")

        result = image_to_base64(str(test_image))

        assert isinstance(result, str)


class TestCalculateFileMd5:
    """Tests for calculate_file_md5 function."""

    def test_calculate_file_md5(self, tmp_path):
        """Test MD5 calculation."""
        test_file = tmp_path / "test.txt"
        test_content = b"Test content for MD5"
        test_file.write_bytes(test_content)

        result = calculate_file_md5(str(test_file))

        expected = hashlib.md5(test_content).hexdigest()
        assert result == expected

    def test_calculate_file_md5_consistency(self, tmp_path):
        """Test MD5 consistency across multiple calls."""
        test_file = tmp_path / "test.bin"
        test_file.write_bytes(b"Consistent data")

        result1 = calculate_file_md5(str(test_file))
        result2 = calculate_file_md5(str(test_file))

        assert result1 == result2

    def test_calculate_file_md5_different_chunk_sizes(self, tmp_path):
        """Test MD5 with different chunk sizes."""
        test_file = tmp_path / "test.bin"
        test_file.write_bytes(b"Test data" * 1000)

        result1 = calculate_file_md5(str(test_file), chunk_size=1024)
        result2 = calculate_file_md5(str(test_file), chunk_size=512)

        assert result1 == result2


class TestIndexImageFile:
    """Tests for index_image_file function."""

    @patch("fastembed.ImageEmbedding")
    @patch("PIL.Image.open")
    @patch("copilot.core.vectordb_utils.image_to_base64")
    @patch("copilot.core.vectordb_utils.calculate_file_md5")
    def test_index_image_file_new_image(
        self, mock_calc_md5, mock_img_to_b64, mock_image_open, mock_sentence_transformer
    ):
        """Test indexing a new image using real Etendo logo."""
        # Use real Etendo image path
        test_image_path = Path(__file__).parent / "resources" / "images" / "etendo.png"

        mock_calc_md5.return_value = "image_md5_hash"
        mock_img_to_b64.return_value = "base64_image_data"

        # Mock the opened image
        mock_img = Mock()
        mock_image_open.return_value = mock_img

        mock_model = Mock()
        embed_res = Mock()
        embed_res.tolist.return_value = [0.1, 0.2, 0.3]
        mock_model.embed.return_value = [embed_res]
        mock_sentence_transformer.return_value = mock_model

        mock_collection = Mock()
        mock_collection.get.return_value = {"ids": []}
        mock_client = Mock()
        mock_client.get_or_create_collection.return_value = mock_collection

        result = index_image_file(str(test_image_path), mock_client, "test_collection")

        assert result["status"] == "added"
        assert result["md5"] == "image_md5_hash"
        mock_collection.add.assert_called_once()

    @patch("copilot.core.vectordb_utils.calculate_file_md5")
    def test_index_image_file_existing_image(self, mock_calc_md5):
        """Test indexing an existing image."""
        mock_calc_md5.return_value = "existing_md5"

        mock_collection = Mock()
        mock_collection.get.return_value = {"ids": ["existing_id"], "metadatas": [{"md5": "existing_md5"}]}
        mock_client = Mock()
        mock_client.get_or_create_collection.return_value = mock_collection

        result = index_image_file("test_image.png", mock_client, "test_collection")

        assert result["status"] == "exists"
        mock_collection.update.assert_called_once()

    @patch("copilot.core.vectordb_utils.calculate_file_md5")
    def test_index_image_file_import_error(self, mock_calc_md5):
        """Test handling of missing dependencies."""
        mock_calc_md5.return_value = "test_md5"

        # Mock the import to fail for fastembed
        with patch("builtins.__import__", side_effect=ImportError("fastembed not available")):
            mock_client = Mock()
            result = index_image_file("test_image.png", mock_client, "test_collection")

            assert result["status"] == "error"
            assert "Required libraries not available" in result["message"]


class TestGetImageCollectionName:
    """Tests for get_image_collection_name function."""

    def test_get_image_collection_name(self):
        """Test image collection name generation."""
        agent_id = "agent_123"

        result = get_image_collection_name(agent_id)

        assert result == "AGENT_agent_123_IMAGES"

    def test_get_image_collection_name_different_agents(self):
        """Test unique collection names for different agents."""
        result1 = get_image_collection_name("agent_1")
        result2 = get_image_collection_name("agent_2")

        assert result1 != result2


class TestFindSimilarReference:
    """Tests for find_similar_reference function."""

    @patch("copilot.core.vectordb_utils.get_chroma_settings")
    @patch("fastembed.ImageEmbedding")
    @patch("PIL.Image.open")
    @patch("copilot.core.vectordb_utils.chromadb.Client")
    @patch("copilot.core.vectordb_utils.get_vector_db_path")
    @patch("copilot.core.vectordb_utils.get_sim_threshold_with_ignore")
    def test_find_similar_reference_success(
        self,
        mock_get_threshold,
        mock_get_db_path,
        mock_client_class,
        mock_image_open,
        mock_sentence_transformer,
        mock_get_chroma_settings,
        tmp_path,
    ):
        """Test finding similar reference successfully using real Etendo images."""
        # Setup mocks
        mock_get_threshold.return_value = None
        mock_get_db_path.return_value = str(tmp_path / "test.db")
        mock_get_chroma_settings.return_value = Mock()

        # Use real Etendo image as query
        test_image = Path(__file__).parent / "resources" / "images" / "etendo.png"
        reference_image = Path(__file__).parent / "resources" / "images" / "etendo_edited.png"

        # Mock the opened image
        mock_img = Mock()
        mock_image_open.return_value = mock_img

        # Mock the CLIP model
        mock_model = Mock()
        mock_encode_result = Mock()
        mock_encode_result.tolist.return_value = [0.1, 0.2, 0.3]
        mock_model.embed.return_value = [mock_encode_result]
        mock_sentence_transformer.return_value = mock_model

        # Mock the collection and client
        mock_collection = Mock()
        mock_collection.query.return_value = {
            "ids": [["ref_id"]],
            "distances": [[0.25]],
            "metadatas": [[{"path": str(reference_image)}]],
            "documents": [["base64_ref_image"]],
        }
        mock_client = Mock()
        mock_client.get_collection.return_value = mock_collection
        mock_client_class.return_value = mock_client

        ref_path, ref_b64 = find_similar_reference(str(test_image), "agent_123")

        assert ref_path == str(reference_image)
        assert ref_b64 == "base64_ref_image"

    @patch("copilot.core.vectordb_utils.get_vector_db_path")
    def test_find_similar_reference_no_agent_id(self, mock_get_db_path, tmp_path):
        """Test with no agent_id."""
        test_image = tmp_path / "query.png"
        test_image.write_bytes(b"fake image")

        ref_path, ref_b64 = find_similar_reference(str(test_image), None)

        assert ref_path is None
        assert ref_b64 is None

    @patch("copilot.core.vectordb_utils.get_chroma_settings")
    @patch("fastembed.ImageEmbedding")
    @patch("PIL.Image.open")
    @patch("copilot.core.vectordb_utils.chromadb.Client")
    @patch("copilot.core.vectordb_utils.get_vector_db_path")
    @patch("copilot.core.vectordb_utils.get_sim_threshold_with_ignore")
    def test_find_similar_reference_exceeds_threshold(
        self,
        mock_get_threshold,
        mock_get_db_path,
        mock_client_class,
        mock_image_open,
        mock_sentence_transformer,
        mock_get_chroma_settings,
        tmp_path,
    ):
        """Test finding reference that exceeds similarity threshold using real Etendo image."""
        mock_get_threshold.return_value = 0.2  # Threshold
        mock_get_db_path.return_value = str(tmp_path / "test.db")
        mock_get_chroma_settings.return_value = Mock()

        # Use real Etendo image
        test_image = Path(__file__).parent / "resources" / "images" / "etendo.png"

        # Mock the opened image
        mock_img = Mock()
        mock_image_open.return_value = mock_img

        # Mock the CLIP model
        mock_model = Mock()
        mock_encode_result = Mock()
        mock_encode_result.tolist.return_value = [0.1, 0.2, 0.3]
        mock_model.embed.return_value = [mock_encode_result]
        mock_sentence_transformer.return_value = mock_model

        # Mock the collection and client
        mock_collection = Mock()
        mock_collection.query.return_value = {
            "ids": [["ref_id"]],
            "distances": [[0.5]],  # Distance exceeds threshold
            "metadatas": [[{"path": "reference.png"}]],
            "documents": [["base64_ref_image"]],
        }
        mock_client = Mock()
        mock_client.get_collection.return_value = mock_collection
        mock_client_class.return_value = mock_client

        ref_path, ref_b64 = find_similar_reference(str(test_image), "agent_123")

        assert ref_path is None
        assert ref_b64 is None


class TestFilterByDistance:
    """Tests for filter_by_distance function."""

    def test_filter_by_distance_no_results(self):
        """Test filtering with no results."""
        results = {"ids": [[]], "distances": [[]], "metadatas": [[]], "documents": [[]]}

        ref_path, ref_b64 = filter_by_distance(results, None)

        assert ref_path is None
        assert ref_b64 is None

    def test_filter_by_distance_below_threshold(self):
        """Test filtering with distance below threshold."""
        results = {
            "ids": [["ref1"]],
            "distances": [[0.15]],
            "metadatas": [[{"path": "/path/to/ref.png"}]],
            "documents": [["base64_data"]],
        }

        ref_path, ref_b64 = filter_by_distance(results, 0.2)

        assert ref_path == "/path/to/ref.png"
        assert ref_b64 == "base64_data"

    def test_filter_by_distance_above_threshold(self):
        """Test filtering with distance above threshold."""
        results = {
            "ids": [["ref1"]],
            "distances": [[0.5]],
            "metadatas": [[{"path": "/path/to/ref.png"}]],
            "documents": [["base64_data"]],
        }

        ref_path, ref_b64 = filter_by_distance(results, 0.3)

        assert ref_path is None
        assert ref_b64 is None

    def test_filter_by_distance_no_threshold(self):
        """Test filtering without threshold."""
        results = {
            "ids": [["ref1"]],
            "distances": [[0.9]],
            "metadatas": [[{"path": "/path/to/ref.png"}]],
            "documents": [["base64_data"]],
        }

        ref_path, ref_b64 = filter_by_distance(results, None)

        assert ref_path == "/path/to/ref.png"
        assert ref_b64 == "base64_data"


class TestLogImageOrigin:
    """Tests for log_image_origin function."""

    @patch("copilot.core.vectordb_utils.copilot_debug")
    def test_log_image_origin_with_base64(self, mock_debug):
        """Test logging when image has base64 data."""
        log_image_origin("base64_data_here")

        mock_debug.assert_called_once()
        assert "ChromaDB" in mock_debug.call_args[0][0]

    @patch("copilot.core.vectordb_utils.copilot_debug")
    def test_log_image_origin_without_base64(self, mock_debug):
        """Test logging when image has no base64 data."""
        log_image_origin(None)

        mock_debug.assert_called_once()
        assert "disk" in mock_debug.call_args[0][0]


class TestDebugMetadata:
    """Tests for debug_metadata function."""

    @patch("copilot.core.vectordb_utils.copilot_debug")
    @patch("copilot.core.vectordb_utils.is_debug_enabled")
    def test_debug_metadata_with_debug_enabled(self, mock_is_debug, mock_debug):
        """Test debug metadata output when debug is enabled."""
        mock_is_debug.return_value = True

        results = {
            "ids": [["id1", "id2"]],
            "metadatas": [[{"key": "value1"}, {"key": "value2"}]],
        }

        debug_metadata(results)

        assert mock_debug.call_count >= 3  # At least intro + 2 items

    @patch("copilot.core.vectordb_utils.copilot_debug")
    @patch("copilot.core.vectordb_utils.is_debug_enabled")
    def test_debug_metadata_with_debug_disabled(self, mock_is_debug, mock_debug):
        """Test debug metadata output when debug is disabled."""
        mock_is_debug.return_value = False

        results = {
            "ids": [["id1"]],
            "metadatas": [[{"key": "value"}]],
        }

        debug_metadata(results)

        # Should still call intro message
        assert mock_debug.call_count >= 1

    @patch("copilot.core.vectordb_utils.copilot_debug")
    @patch("copilot.core.vectordb_utils.is_debug_enabled")
    def test_debug_metadata_empty_results(self, mock_is_debug, mock_debug):
        """Test debug metadata with empty results."""
        mock_is_debug.return_value = True

        results = {"ids": [[]], "metadatas": [[]]}

        debug_metadata(results)

        mock_debug.assert_called_once()


class TestGetSimThreshold:
    """Tests for get_sim_threshold function."""

    @patch("copilot.core.vectordb_utils.read_optional_env_var")
    def test_get_sim_threshold_with_explicit_value(self, mock_read_env):
        """Test when explicit threshold is provided."""
        result = get_sim_threshold(0.5)

        assert result == pytest.approx(0.5)
        mock_read_env.assert_not_called()

    @patch("copilot.core.vectordb_utils.read_optional_env_var")
    def test_get_sim_threshold_from_env_var(self, mock_read_env):
        """Test reading threshold from environment variable."""
        mock_read_env.return_value = "0.3"

        result = get_sim_threshold(None)

        assert result == pytest.approx(0.3)
        mock_read_env.assert_called_once_with("COPILOT_REFERENCE_SIMILARITY_THRESHOLD", None)

    @patch("copilot.core.vectordb_utils.read_optional_env_var")
    def test_get_sim_threshold_invalid_env_var(self, mock_read_env):
        """Test handling of invalid environment variable."""
        mock_read_env.return_value = "invalid"

        result = get_sim_threshold(None)

        assert result is None

    @patch("copilot.core.vectordb_utils.read_optional_env_var")
    def test_get_sim_threshold_no_env_var(self, mock_read_env):
        """Test when no environment variable is set."""
        mock_read_env.return_value = None

        result = get_sim_threshold(None)

        assert result is None


class TestGetSimThresholdWithIgnore:
    """Tests for get_sim_threshold_with_ignore function."""

    @patch("copilot.core.vectordb_utils.read_optional_env_var")
    def test_get_sim_threshold_with_ignore_true(self, mock_read_env):
        """Test with ignore_env=True."""
        result = get_sim_threshold_with_ignore(0.5, ignore_env=True)

        assert result == pytest.approx(0.5)
        mock_read_env.assert_not_called()

    @patch("copilot.core.vectordb_utils.read_optional_env_var")
    def test_get_sim_threshold_with_ignore_false(self, mock_read_env):
        """Test with ignore_env=False."""
        mock_read_env.return_value = "0.4"

        result = get_sim_threshold_with_ignore(None, ignore_env=False)

        assert result == pytest.approx(0.4)
        mock_read_env.assert_called_once()

    @patch("copilot.core.vectordb_utils.read_optional_env_var")
    def test_get_sim_threshold_with_ignore_none_and_env(self, mock_read_env):
        """Test with None threshold and ignore_env=False."""
        mock_read_env.return_value = "0.25"

        result = get_sim_threshold_with_ignore(None, ignore_env=False)

        assert result == pytest.approx(0.25)

    def test_get_sim_threshold_with_ignore_none_true(self):
        """Test with None threshold and ignore_env=True."""
        result = get_sim_threshold_with_ignore(None, ignore_env=True)

        assert result is None


class TestConstants:
    """Tests for module-level constants."""

    def test_allowed_extensions(self):
        """Test ALLOWED_EXTENSIONS constant."""
        assert isinstance(ALLOWED_EXTENSIONS, list)
        assert "pdf" in ALLOWED_EXTENSIONS
        assert "txt" in ALLOWED_EXTENSIONS
        assert "py" in ALLOWED_EXTENSIONS

    def test_image_extensions(self):
        """Test IMAGE_EXTENSIONS constant."""
        assert isinstance(IMAGE_EXTENSIONS, list)
        assert "png" in IMAGE_EXTENSIONS
        assert "jpg" in IMAGE_EXTENSIONS
        assert "jpeg" in IMAGE_EXTENSIONS

    def test_langchain_default_collection_name(self):
        """Test LANGCHAIN_DEFAULT_COLLECTION_NAME constant."""
        assert isinstance(LANGCHAIN_DEFAULT_COLLECTION_NAME, str)
        assert len(LANGCHAIN_DEFAULT_COLLECTION_NAME) > 0

    def test_images_collection_name(self):
        """Test IMAGES_COLLECTION_NAME constant."""
        assert isinstance(IMAGES_COLLECTION_NAME, str)
        assert IMAGES_COLLECTION_NAME == "IMAGES"
