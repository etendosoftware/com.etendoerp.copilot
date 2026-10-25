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
            + "namespace varchar(64) NOT NULL, source_id varchar(64) NOT NULL, source_name text NOT NULL, "
            + "content text NOT NULL, content_hash varchar(64) NOT NULL, embedding vector NOT NULL, "
            + "embedding_model varchar(200) NOT NULL DEFAULT 'legacy/unknown', embedding_dims int NOT NULL DEFAULT 0, "
            + "purge boolean NOT NULL DEFAULT false)",
        "ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS embedding_model varchar(200) NOT NULL DEFAULT 'legacy/unknown'",
        "ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS embedding_dims int NOT NULL DEFAULT 0",
        // Memory ids are UUIDs with dashes (36 chars): tables created with varchar(32) are widened.
        "ALTER TABLE " + TABLE + " ALTER COLUMN source_id TYPE varchar(64)",
        "CREATE INDEX IF NOT EXISTS etcop_kb_scope_idx ON " + TABLE + " (app_id, ad_client_id, namespace)",
        "CREATE INDEX IF NOT EXISTS etcop_kb_model_scope_idx ON " + TABLE
            + " (app_id, ad_client_id, namespace, embedding_dims)",
        "DROP INDEX IF EXISTS etcop_kb_embedding_idx");
  }

  public interface Executor {
    boolean hasPgVector() throws Exception;
    void execute(String sql) throws Exception;
  }
}
