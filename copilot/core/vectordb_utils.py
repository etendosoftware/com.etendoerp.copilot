import hashlib
import os
import shutil
import tempfile
import time
import zipfile

import chromadb
import pymupdf
from chromadb import Settings
from copilot.core.schemas import SplitterConfig
from copilot.core.utils import copilot_debug, get_proxy_url
from langchain.text_splitter import CharacterTextSplitter, MarkdownTextSplitter
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings
from langchain_text_splitters import (
    Language,
    RecursiveCharacterTextSplitter,
    RecursiveJsonSplitter,
)

ALLOWED_EXTENSIONS = ["pdf", "txt", "md", "markdown", "java", "js", "py", "xml", "json"]

LANGCHAIN_DEFAULT_COLLECTION_NAME = "langchain"


def get_embedding():
    return OpenAIEmbeddings(disallowed_special=(), show_progress_bar=True, base_url=get_proxy_url())


def get_vector_db_path(vector_db_id):
    try:
        cwd = os.getcwd()
    except Exception:
        cwd = "unknown"
    copilot_debug(f"Retrieving vector db path for {vector_db_id}, the current working directory is {cwd}")
    # check if exists /app
    if os.path.exists("/app"):
        vectordb_folder = "/app/vectordbs"
    else:
        vectordb_folder = "./vectordbs"
    if not os.path.exists(vectordb_folder):
        os.makedirs(vectordb_folder, exist_ok=True)
    return vectordb_folder + "/" + vector_db_id + ".db"


def get_chroma_settings(db_path=None):
    settings = Settings()
    if db_path is not None:
        settings.persist_directory = db_path
    settings.is_persistent = True
    settings.allow_reset = True
    return settings


def handle_zip_file(zip_file_path, chroma_client, splitter_config: SplitterConfig):
    temp_dir = tempfile.mkdtemp()
    acum_texts = []
    with zipfile.ZipFile(zip_file_path, "r") as zip_ref:
        zip_ref.extractall(temp_dir)
    # walk through the directory and index the files
    for root, _dirs, files in os.walk(temp_dir):
        for file in files:
            file_path = os.path.join(root, file)
            ext = file.split(".")[-1].lower()
            if ext in ALLOWED_EXTENSIONS:
                try:
                    copilot_debug(f"Processing file {file_path}")
                    acum_texts.extend(index_file(ext, file_path, chroma_client, splitter_config))
                except Exception as e:
                    copilot_debug(f"Error processing file {file_path}: {e}")
    # Remove the entire temporary directory
    shutil.rmtree(temp_dir)
    return acum_texts


def load_chroma_collection_from_path(db_path):
    """
    Loads a Chroma collection from the specified database path.

    Args:
        db_path (str): The path to the database directory where the Chroma collection is stored.

    Returns:
        Collection: The Chroma collection object loaded from the database.

    Steps:
        1. Initializes a Chroma client with the provided database path.
        2. Retrieves or creates a collection with the default name defined by `LANGCHAIN_DEFAULT_COLLECTION_NAME`.
    """
    # Initialize the Chroma client with the database path
    client = chromadb.Client(chromadb.config.Settings(persist_directory=db_path))

    # Load the collection by its name
    collection = client.get_or_create_collection(LANGCHAIN_DEFAULT_COLLECTION_NAME)
    return collection


def hash_splitter_config(config: SplitterConfig) -> str:
    """
    Generates a unique hash for a given SplitterConfig object.

    Args:
        config (SplitterConfig): The configuration object to be hashed.

    Returns:
        str: A SHA-256 hash of the JSON representation of the configuration.

    Notes:
        - The JSON representation of the configuration is serialized using the `model_dump_json` method,
          which excludes unset fields.
        - The resulting hash can be used to identify changes in the configuration.
    """
    json_repr = config.model_dump_json(exclude_unset=True)
    return hashlib.sha256(json_repr.encode("utf-8")).hexdigest()


def index_file(ext, item_path, chroma_client, splitter_config: SplitterConfig):
    # Process the file and get its content and MD5
    file_content, md5 = process_file(item_path, ext)
    # If there is a splitter config, we need to add the config to the md5, so
    # we can identify changes in the config, to reindex the file.
    if splitter_config is not None:
        md5 = md5 + hash_splitter_config(splitter_config)

    collection = chroma_client.get_or_create_collection(LANGCHAIN_DEFAULT_COLLECTION_NAME)
    copilot_debug(f"Collection id {collection.id}")
    # Search for the document based on the MD5 hash
    result = collection.get(where={"md5": md5})

    if result and len(result["ids"]) > 0:
        # If the document with the same MD5 exists, unmark it for purge
        copilot_debug(f"The file with md5 {md5} is already indexed. Marking 'purge' as False.")
        collection.update(ids=result["ids"], metadatas=[{"purge": False} for _ in result["metadatas"]])
        return []
    else:
        # If the document with this MD5 doesn't exist, add it as a new document
        document = Document(page_content=file_content, metadata={"md5": md5, "purge": False})
        text_splitter = get_text_splitter(ext, splitter_config)

        # Split the document and add it to the collection
        copilot_debug(f"File with md5 {md5} added to index with 'purge': False.")
        documents: list[Document] = [document]
        if text_splitter and not splitter_config.skip_splitting:
            documents = text_splitter.split_documents(documents)

    return documents


def get_text_splitter(ext, splitter_config: SplitterConfig):
    """
    Returns an appropriate text splitter based on the file extension and optional parameters.

    Args:
        ext (str): The file extension indicating the type of file (e.g., 'md', 'txt', 'pdf', 'json', 'java', 'js', 'py').
        splitter_config (SplitterConfig): Configuration object containing optional parameters such as
            max_chunk_size and chunk_overlap.

    Returns:
        TextSplitter: An instance of a text splitter class appropriate for the given file extension.

    Raises:
        ValueError: If the file extension is unsupported.

    Notes:
        - For Markdown files ('md', 'markdown'), a `MarkdownTextSplitter` is returned.
        - For plain text, PDF, and XML files ('txt', 'pdf', 'xml'), a `CharacterTextSplitter` is returned,
          with optional customization for chunk size and overlap.
        - For JSON files ('json'), a `RecursiveJsonSplitter` is returned.
        - For programming languages like Java, JavaScript, and Python ('java', 'js', 'py'),
          a `RecursiveCharacterTextSplitter` is returned, configured for the respective language.
    """
    if ext in ["md", "markdown"]:
        return MarkdownTextSplitter()
    elif ext in ["txt", "pdf", "xml"]:
        return get_plain_text_splitter(splitter_config)
    elif ext in ["json"]:
        splitter = RecursiveJsonSplitter()
        if splitter_config is not None:
            if splitter_config.max_chunk_size is not None:
                splitter.max_chunk_size = splitter_config.max_chunk_size
        return splitter
    elif ext in ["java"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.JAVA)
    elif ext in ["js"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.JS)
    elif ext in ["py"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.PYTHON)
    else:
        raise ValueError(f"Unsupported file extension: {ext}")


def get_plain_text_splitter(splitter_config):
    """
    Creates and configures a plain text splitter based on the provided configuration.

    Args:
        splitter_config (SplitterConfig): Configuration object containing optional parameters such as
            max_chunk_size and chunk_overlap.

    Returns:
        CharacterTextSplitter: An instance of `CharacterTextSplitter` configured with the specified
        chunk size and overlap, if provided.

    Notes:
        - The `max_chunk_size` parameter determines the maximum size of each text chunk.
        - The `chunk_overlap` parameter specifies the number of overlapping characters between chunks.
    """
    splitter = CharacterTextSplitter()
    if splitter_config is not None:
        if splitter_config.max_chunk_size is not None:
            splitter._chunk_size = splitter_config.max_chunk_size
        if splitter_config.chunk_overlap is not None:
            splitter._chunk_overlap = splitter_config.chunk_overlap
    return splitter


def process_file(file_path, ext):
    """
    Processes a file based on its extension and calculates its SHA-256 hash.

    Args:
        file_path (str): The path to the file to be processed.
        ext (str): The file extension indicating the type of file (e.g., 'pdf', 'txt').

    Returns:
        tuple: A tuple containing:
            - str: The processed content of the file. For PDFs, the extracted text is returned.
                   For other file types, the content is decoded as a UTF-8 string.
            - str: The SHA-256 hash of the file in hexadecimal format.

    Steps:
        1. Calculates the SHA-256 hash of the file using its path.
        2. Reads the file in binary mode.
        3. If the file is a PDF, processes it to extract text content.
        4. For other file types, decodes the binary data as a UTF-8 string.
    """
    sha256 = calculate_sha256_from_file_path(file_path)
    copilot_debug(f"Processing file {file_path} with sha256 {sha256}")
    # Get the document name, which is the file path.
    with open(file_path, "rb") as file:
        file_data = file.read()

    if ext == "pdf":
        return process_pdf(file_data), sha256
    else:
        return file_data.decode("utf-8"), sha256


def calculate_sha256_from_file_path(file_path, chunk_size=4096):
    """
    Calculate the SHA-256 hash of a file given its path.

    :param file_path: Path of the file.
    :param chunk_size: Size of the blocks that will be read. The default value is 4096 bytes.
    :return: SHA-256 hash of the file in hexadecimal format.
    """
    sha256_hash = hashlib.sha256()

    # Open the file in binary mode
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(chunk_size), b""):
            sha256_hash.update(byte_block)

    return sha256_hash.hexdigest()


def process_pdf(pdf_data):
    """
    Processes a PDF file from its binary data and extracts its text content.

    Args:
        pdf_data (bytes): The binary data of the PDF file.

    Returns:
        str: The extracted text content from the PDF file.

    Steps:
        1. Creates a temporary PDF file in the `/tmp` directory with a unique name.
        2. Writes the binary PDF data to the temporary file.
        3. Opens the temporary PDF file using the `pymupdf` library.
        4. Iterates through each page of the PDF and extracts its text content.
        5. Returns the concatenated text content of all pages.
    """
    temp_pdf = "/tmp/temp" + str(round(time.time())) + ".pdf"
    with open(temp_pdf, "wb") as f:
        f.write(pdf_data)
    doc = pymupdf.open(temp_pdf)
    content = ""
    for page in doc:
        content += page.get_text()
    return content
