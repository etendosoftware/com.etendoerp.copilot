package com.etendoerp.copilot.modulescript;

import java.util.List;

/** Optional, idempotent provisioning of Copilot's pgvector KB storage during update.database. */
public final class KnowledgeBaseSchemaProvisioning {
  public static final String TABLE = "etcop_kb_chunk";

  private KnowledgeBaseSchemaProvisioning() { }

  public static boolean provision(Executor executor) throws Exception {
    if (!executor.hasPgVector()) return false;
    for (String sql : schemaStatements()) executor.execute(sql);
    return true;
  }

  public static List<String> schemaStatements() {
    return List.of(
        "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + "chunk_id varchar(64) PRIMARY KEY, app_id varchar(32) NOT NULL, ad_client_id varchar(32) NOT NULL, "
            + "namespace varchar(64) NOT NULL, source_id varchar(32) NOT NULL, source_name text NOT NULL, "
            + "content text NOT NULL, content_hash varchar(64) NOT NULL, embedding vector NOT NULL, purge boolean NOT NULL DEFAULT false)",
        "CREATE INDEX IF NOT EXISTS etcop_kb_scope_idx ON " + TABLE + " (app_id, ad_client_id, namespace)",
        "CREATE INDEX IF NOT EXISTS etcop_kb_embedding_idx ON " + TABLE
            + " USING hnsw (embedding vector_cosine_ops)");
  }

  public interface Executor {
    boolean hasPgVector() throws Exception;
    void execute(String sql) throws Exception;
  }
}
