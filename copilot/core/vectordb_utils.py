from chromadb import Settings
from langchain_openai import OpenAIEmbeddings


def get_embedding():
    return OpenAIEmbeddings(disallowed_special=(), show_progress_bar=True)


def get_vector_db_path(vector_db_id):
    return "./vectordbs/" + vector_db_id + ".db"


def get_chroma_settings(db_path=None):
    settings = Settings()
    if db_path is not None:
        settings.persist_directory = db_path
    settings.is_persistent = True
    settings.allow_reset = True
    return settings
