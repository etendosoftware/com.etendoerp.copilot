"""Knowledge Base utilities to avoid circular imports."""

import os

from copilot.core.schemas import AssistantSchema
from copilot.core.vectordb_utils import (
    get_chroma_settings,
    get_embedding,
    get_vector_db_path,
)
from langchain_chroma.vectorstores import Chroma


def get_kb_tool(agent_config: AssistantSchema = None):
    """Get knowledge base search tool if available."""
    from copilot.core.threadcontext import ThreadContext

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
            ad_client_id = ThreadContext.get_data("ad_client_id", "0")
            retriever = db.as_retriever(
                search_kwargs={
                    "k": kb_search_k,
                    "filter": {"ad_client_id": {"$in": ["0", ad_client_id]}},
                },
            )
            kb_tool = retriever.as_tool(
                name="KnowledgeBaseSearch",
                description="Search in the knowledge base for a term or question.",
            )
    return kb_tool
