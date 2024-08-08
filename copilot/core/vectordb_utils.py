from langchain_openai import OpenAIEmbeddings


def get_embedding():
    return OpenAIEmbeddings(disallowed_special=(), show_progress_bar=True)


def get_vector_db_path(vector_db_id):
    return "./vectordbs/" + vector_db_id + ".db"
