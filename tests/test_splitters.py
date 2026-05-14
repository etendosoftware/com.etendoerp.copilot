from types import SimpleNamespace
from unittest.mock import Mock, patch

from copilot.core import splitters
from copilot.core.splitters import CopilotRecursiveJsonSplitter


def test_copilot_recursive_json_splitter_delegates_json_content_and_metadata():
    splitter_instance = Mock()
    splitter_instance.create_documents.return_value = ["chunk-1", "chunk-2"]

    with patch.object(splitters, "RecursiveJsonSplitter", return_value=splitter_instance) as splitter_cls:
        splitter = CopilotRecursiveJsonSplitter(max_chunk_size=123)

    document = SimpleNamespace(
        page_content='{"name": "Etendo", "items": [1, 2]}', metadata={"source": "unit"}
    )

    result = splitter.split_documents([document])

    splitter_cls.assert_called_once_with(max_chunk_size=123)
    splitter_instance.create_documents.assert_called_once_with(
        texts=[{"name": "Etendo", "items": [1, 2]}], metadatas=[{"source": "unit"}]
    )
    assert result == ["chunk-1", "chunk-2"]


def test_copilot_recursive_json_splitter_accumulates_chunks_from_multiple_documents():
    splitter = CopilotRecursiveJsonSplitter(max_chunk_size=10)
    splitter.json_splitter = Mock()
    splitter.json_splitter.create_documents.side_effect = [["first"], ["second", "third"]]

    documents = [
        SimpleNamespace(page_content='{"id": 1}', metadata={"index": 1}),
        SimpleNamespace(page_content='{"id": 2}', metadata={"index": 2}),
    ]

    assert splitter.split_documents(documents) == ["first", "second", "third"]
