import hashlib
import os
import shutil
import tempfile
import time
import zipfile

import chromadb
import pymupdf
from chromadb import Settings
from copilot.core.splitters import CopilotRecursiveJsonSplitter
from copilot.core.utils import copilot_debug
from langchain.text_splitter import CharacterTextSplitter, MarkdownTextSplitter
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings
from langchain_text_splitters import Language, RecursiveCharacterTextSplitter

ALLOWED_EXTENSIONS = ["pdf", "txt", "md", "markdown", "java", "js", "py", "xml", "json"]

LANGCHAIN_DEFAULT_COLLECTION_NAME = "langchain"


def get_embedding():
    return OpenAIEmbeddings(disallowed_special=(), show_progress_bar=True)


def get_vector_db_path(vector_db_id):
    copilot_debug(
        f"Retrieving vector db path for {vector_db_id}, the current working directory is {os.getcwd()}"
    )
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


def handle_zip_file(zip_file_path, chroma_client):
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
                    acum_texts.extend(index_file(ext, file_path, chroma_client))
                except Exception as e:
                    copilot_debug(f"Error processing file {file_path}: {e}")
    # Remove the entire temporary directory
    shutil.rmtree(temp_dir)
    return acum_texts


def load_chroma_collection_from_path(db_path):
    # Initialize the Chroma client with the database path
    client = chromadb.Client(chromadb.config.Settings(persist_directory=db_path))

    # Load the collection by its name
    collection = client.get_or_create_collection(LANGCHAIN_DEFAULT_COLLECTION_NAME)
    return collection


def index_file(ext, item_path, chroma_client):
    # Process the file and get its content and MD5
    file_content, md5 = process_file(item_path, ext)

    collection = chroma_client.get_or_create_collection(LANGCHAIN_DEFAULT_COLLECTION_NAME)
    copilot_debug(f"Coleccion id {collection.id}")
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
        text_splitter = get_text_splitter(ext)

        # Split the document and add it to the collection
        copilot_debug(f"File with md5 {md5} added to index with 'purge': False.")
        documents = text_splitter.split_documents([document])

    return documents


def get_text_splitter(ext):
    if ext in ["md", "markdown"]:
        return MarkdownTextSplitter()
    elif ext in ["txt", "pdf", "xml"]:
        return CharacterTextSplitter()
    elif ext in ["json"]:
        return CopilotRecursiveJsonSplitter(max_chunk_size=300)
    elif ext in ["java"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.JAVA)
    elif ext in ["js"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.JS)
    elif ext in ["py"]:
        return RecursiveCharacterTextSplitter.from_language(language=Language.PYTHON)
    else:
        raise ValueError(f"Unsupported file extension: {ext}")


def process_file(file_path, ext):
    sha256 = calculate_sha256_from_file_path(file_path)
    copilot_debug(f"Processing file {file_path} with sha256 {sha256}")
    # get the document name, that is the file path.
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
    :param chunk_size: Size of the blocks that will be read from the file. The default value is 4096 bytes.
    :return: SHA-256 hash of the file in hexadecimal format.
    """
    sha256_hash = hashlib.sha256()

    # Open the file in binary mode
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(chunk_size), b""):
            sha256_hash.update(byte_block)

    return sha256_hash.hexdigest()


def process_pdf(pdf_data):
    temp_pdf = "/tmp/temp" + str(round(time.time())) + ".pdf"
    with open(temp_pdf, "wb") as f:
        f.write(pdf_data)
    doc = pymupdf.open(temp_pdf)
    content = ""
    for page in doc:
        content += page.get_text()
    return content
