import json

from langchain_text_splitters import RecursiveJsonSplitter


class CopilotRecursiveJsonSplitter:
    """
    A class to handle the splitting of JSON documents into smaller chunks.

    Attributes:
    ----------
    json_splitter : RecursiveJsonSplitter
        An instance of RecursiveJsonSplitter used to split JSON documents.

    Methods:
    -------
    split_documents(documents):
        Splits a list of documents into smaller chunks.
    """

    def __init__(self, max_chunk_size):
        """
        Initializes the CopilotRecursiveJsonSplitter with a maximum chunk size.

        Parameters:
        ----------
        max_chunk_size : int
            The maximum size of each chunk.
        """
        self.json_splitter = RecursiveJsonSplitter(max_chunk_size=max_chunk_size)

    def split_documents(self, documents):
        """
        Splits a list of documents into smaller chunks.

        Parameters:
        ----------
        documents : list
            A list of Document objects to be split.

        Returns:
        -------
        list
            A list of split Document objects.
        """
        result = []
        for document in documents:
            document_content = document.page_content
            document_content = json.loads(document_content)
            metadata = document.metadata
            split_documents = self.json_splitter.create_documents(texts=[document_content], metadatas=[metadata])
            result.extend(split_documents)
        return result
