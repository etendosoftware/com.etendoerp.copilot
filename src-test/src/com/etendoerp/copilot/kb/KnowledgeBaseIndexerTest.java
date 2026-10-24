package com.etendoerp.copilot.kb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

public class KnowledgeBaseIndexerTest {
  @Test
  public void indexesTextAsScopedChunksWithoutCallingPython() throws Exception {
    RecordingExecutor executor = new RecordingExecutor();
    EmbeddingModel embeddings = mock(EmbeddingModel.class);
    when(embeddings.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[] {0.25f, 0.75f})));
    KnowledgeBaseIndexer indexer = new KnowledgeBaseIndexer(new KnowledgeBaseStore(executor), embeddings,
        "test/provider-model");
    Path file = Files.createTempFile("copilot-kb-test", ".txt");
    try {
      Files.writeString(file, "alpha beta gamma");
      indexer.index("app-1", "source-1", "KB_app-1", "client-1", file, true, null, null);

      assertEquals(1, executor.upserts);
      assertEquals("app-1", executor.values[1]);
      assertEquals("client-1", executor.values[2]);
      assertEquals("KB_app-1", executor.values[3]);
      assertEquals("source-1", executor.values[4]);
      assertEquals("alpha beta gamma", executor.values[6]);
      assertTrue(executor.sql.contains("ON CONFLICT (chunk_id) DO UPDATE"));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static final class RecordingExecutor implements KnowledgeBaseStore.SqlExecutor {
    private int upserts;
    private String sql;
    private Object[] values;

    @Override public boolean hasPgVector() { return true; }
    @Override public boolean hasTable() { return true; }
    @Override public int update(String sql, Object... values) {
      if (sql.startsWith("INSERT")) {
        this.sql = sql;
        this.values = values;
        upserts++;
      }
      return 1;
    }
    @Override public List<Object[]> query(String sql, Object... values) { return List.of(); }
  }
}
