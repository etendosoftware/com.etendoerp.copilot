import os

from chromadb import Settings
from langchain_openai import OpenAIEmbeddings

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
        os.makedirs(vectordb_folder)
    return vectordb_folder + "/" + vector_db_id + ".db"


def get_chroma_settings(db_path=None):
    settings = Settings()
    if db_path is not None:
        settings.persist_directory = db_path
    settings.is_persistent = True
    settings.allow_reset = True
    return settings
