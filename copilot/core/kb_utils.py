"""Knowledge Base utilities to avoid circular imports."""
import os

from langchain.tools.retriever import create_retriever_tool
from langchain_chroma.vectorstores import Chroma

from .schemas import AssistantSchema
from .vectordb_utils import get_chroma_settings, get_embedding, get_vector_db_path


def get_kb_tool(agent_config: AssistantSchema = None):
    """Get knowledge base search tool if available."""
    kb_tool = None
    kb_search_k = agent_config.kb_search_k if agent_config else 4
    kb_vectordb_id = agent_config.kb_vectordb_id if agent_config else None
    if (
        kb_vectordb_id is not None
        and os.path.exists(get_vector_db_path(kb_vectordb_id))
        and os.listdir(get_vector_db_path(kb_vectordb_id))
    ):
        db_path = get_vector_db_path(kb_vectordb_id)
        db = Chroma(
            persist_directory=db_path,
            embedding_function=get_embedding(),
            client_settings=get_chroma_settings(),
        )
        # check if the db is empty
        res = db.get(limit=1)
        if len(res["ids"]) > 0:
            retriever = db.as_retriever(
                search_kwargs={"k": kb_search_k},
            )
            kb_tool = create_retriever_tool(
                retriever,
                "KnowledgeBaseSearch",
                "Search in the knowledge base for a term or question.",
            )
    return kb_tool
