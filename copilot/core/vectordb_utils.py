import base64
import os
import shutil
import tempfile
import time
import zipfile

import fitz
from chromadb import Settings
from langchain.text_splitter import MarkdownTextSplitter, CharacterTextSplitter
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter, Language

from copilot.core.utils import copilot_debug


def get_embedding():
    return OpenAIEmbeddings(disallowed_special=(), show_progress_bar=True)


def get_vector_db_path(vector_db_id):
    copilot_debug(f"Retrieving vector db path for {vector_db_id}, the current working directory is {os.getcwd()}")
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


def handle_zip_file(zip_file_path):
    temp_zip = zip_file_path
    temp_dir = tempfile.mkdtemp()
    with zipfile.ZipFile(temp_zip, 'r') as zip_ref:
        zip_ref.extractall(temp_dir)

    texts = process_directory(temp_dir)

    # Remove the entire temporary directory
    shutil.rmtree(temp_dir)

    return texts


def handle_other_formats(extension, text):
    if extension == "pdf":
        pdf_data = base64.b64decode(text)
        return process_pdf(pdf_data)
    else:
        return text


def process_directory(directory):
    texts = []
    extensions = ["pdf", "txt", "md", "markdown", "java", "js", "py", "xml"]
    for item in os.listdir(directory):
        item_path = os.path.join(directory, item)
        if os.path.isdir(item_path):
            texts.extend(process_directory(item_path))
        else:
            ext = item.split('.')[-1].lower()
            if ext in extensions:
                copilot_debug(f"Processing file {item_path}")
                try:
                    file_content = process_file(item_path, ext)
                    document = Document(page_content=file_content)
                    text_splitter = get_text_splitter(ext)
                    texts.extend(text_splitter.split_documents([document]))
                except Exception as e:
                    copilot_debug(f"Error processing file {item_path}: {e}")
    return texts


def get_text_splitter(ext):
    if ext in ["md", "markdown"]:
        return MarkdownTextSplitter()
    elif ext in ["txt", "pdf", "xml"]:
        return CharacterTextSplitter()
    elif ext in ["java"]:
        return RecursiveCharacterTextSplitter.from_language(
            language=Language.JAVA
        )
    elif ext in ["js"]:
        return RecursiveCharacterTextSplitter.from_language(
            language=Language.JS
        )
    elif ext in ["py"]:
        return RecursiveCharacterTextSplitter.from_language(
            language=Language.PYTHON
        )
    else:
        raise ValueError(f"Unsupported file extension: {ext}")


def process_file(file_path, ext):
    with open(file_path, 'rb') as file:
        file_data = file.read()

    if ext == "pdf":
        return process_pdf(file_data)
    else:
        return file_data.decode('utf-8')


def process_pdf(pdf_data):
    temp_pdf = '/tmp/temp' + str(round(time.time())) + '.pdf'
    with open(temp_pdf, 'wb') as f:
        f.write(pdf_data)
    doc = fitz.open(temp_pdf)
    content = ''
    for page in doc:
        content += page.get_text()
    return content
