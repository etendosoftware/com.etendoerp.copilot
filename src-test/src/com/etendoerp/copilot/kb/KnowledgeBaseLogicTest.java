package com.etendoerp.copilot.kb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.fail;
import java.util.List;
import org.junit.Test;

public class KnowledgeBaseLogicTest {
  @Test
  public void chunksTextAtBoundariesAndHonorsOverlap() {
    List<String> chunks = KnowledgeBaseChunker.split("alpha beta gamma delta", "txt", false, 12, 3);
    assertEquals(List.of("alpha beta", "gamma delta"), chunks);
  }

  @Test
  public void skipsSplittingAndDropsBlankInput() {
    assertEquals(List.of(" one two "), KnowledgeBaseChunker.split(" one two ", "txt", true, 1, 0));
    assertTrue(KnowledgeBaseChunker.split(" \n ", "txt", false, 5, 0).isEmpty());
  }

  @Test
  public void validatesChunkOptions() {
    expectThrows(IllegalArgumentException.class,
        () -> KnowledgeBaseChunker.split("abc", "txt", false, 4, 4));
  }

  @Test
  public void jsonBoundaryDoesNotSplitInsideQuotedString() {
    String input = "{\"value\":\"a,b,c,d,e,f\",\"other\":1}";
    List<String> chunks = KnowledgeBaseChunker.split(input, "json", false, 12, 0);
    assertEquals(input, String.join("", chunks));
  }

  @Test
  public void scopesSearchToSharedAndCurrentClient() {
    KnowledgeBaseSql.Scope scope = KnowledgeBaseSql.scope("KB_app", "client-1");
    assertEquals("KB_app", scope.appId());
    assertEquals(List.of("0", "client-1"), scope.clientIds());
    assertEquals(List.of("0"), KnowledgeBaseSql.scope("KB_app", "0").clientIds());
    String sql = KnowledgeBaseSql.searchSql(2);
    assertTrue(sql.contains("ad_client_id IN (?,?)"));
    assertTrue(sql.contains("embedding_model=? AND embedding_dims=?"));
    assertTrue(sql.contains("vector_dims(embedding)=2"));
    assertTrue(sql.contains("embedding::vector(2)"));
  }


  @Test
  public void storeSearchBuildsScopedParameterizedQuery() {
    RecordingExecutor executor = new RecordingExecutor();
    KnowledgeBaseStore store = new KnowledgeBaseStore(executor);
    store.search("KB_agent", "client-7", "KB_agent", new float[] {0.25f, 0.5f}, 4, "openai/embed-small");
    assertTrue(executor.sql.contains("embedding_model=? AND embedding_dims=?"));
    assertEquals("KB_agent", executor.values[1]);
    assertEquals("KB_agent", executor.values[2]);
    assertEquals("0", executor.values[3]);
    assertEquals("client-7", executor.values[4]);
    assertEquals("openai/embed-small", executor.values[5]);
    assertEquals(2, executor.values[6]);
    assertEquals(4, executor.values[8]);
  }

  @Test
  public void compatibilityMatrixAllowsEmptyAndAllMatchingAndRejectsNone() {
    KnowledgeBaseStore empty = new KnowledgeBaseStore(new GroupExecutor(List.of()));
    assertTrue(empty.search("app", "client", "KB", new float[] {1, 2}, 3, "openai/model-a").isEmpty());

    KnowledgeBaseStore matching = new KnowledgeBaseStore(new GroupExecutor(
        List.<Object[]>of(new Object[] {"openai/model-a", 2, 5L})));
    assertEquals(1, matching.search("app", "client", "KB", new float[] {1, 2}, 3, "openai/model-a").size());

    KnowledgeBaseIncompatibleException differentDimensions = expectThrows(KnowledgeBaseIncompatibleException.class,
        () -> new KnowledgeBaseStore(new GroupExecutor(List.<Object[]>of(new Object[] {"openai/model-a", 3, 2L})))
            .search("app", "client", "KB", new float[] {1, 2}, 3, "openai/model-a"));
    assertTrue(differentDimensions.getMessage().contains("openai/model-a/3"));
    assertTrue(differentDimensions.getMessage().contains("incompatible"));
    assertTrue(differentDimensions.getMessage().contains("re-index"));

    expectThrows(KnowledgeBaseIncompatibleException.class,
        () -> new KnowledgeBaseStore(new GroupExecutor(List.<Object[]>of(new Object[] {"openai/model-b", 2, 2L})))
            .search("app", "client", "KB", new float[] {1, 2}, 3, "openai/model-a"));
  }

  @Test
  public void partialReindexReturnsMatchesAndEnsuresIndexSkipsAboveLimit() {
    GroupExecutor executor = new GroupExecutor(List.<Object[]>of(
        new Object[] {"openai/model-a", 2, 4L}, new Object[] {"openai/model-b", 2, 3L}));
    KnowledgeBaseStore store = new KnowledgeBaseStore(executor);
    List<KnowledgeBaseStore.Match> matches = store.search("app", "client", "KB", new float[] {1, 2}, 3,
        "openai/model-a");
    assertEquals(1, matches.size());
    assertTrue(matches.get(0).warning().contains("3 chunks"));
    store.ensureIndex(2001);
    assertFalse(executor.updated);
  }

  @Test
  public void sourceReplacementMarksThenPurgesOnlyStaleScopedChunks() {
    RecordingExecutor executor = new RecordingExecutor();
    KnowledgeBaseStore store = new KnowledgeBaseStore(executor);
    store.markSourcePurge("app", "client", "KB", "source");
    assertTrue(executor.sql.contains("SET purge=true"));
    store.purgeSource("app", "client", "KB", "source");
    assertTrue(executor.sql.contains("DELETE FROM etcop_kb_chunk"));
    assertTrue(executor.sql.contains("source_id=? AND purge=true"));
  }

  private static final class GroupExecutor extends RecordingExecutor {
    private final List<Object[]> groups;
    private boolean updated;
    private GroupExecutor(List<Object[]> groups) { this.groups = groups; }
    @Override public int update(String sql, Object... values) { updated = true; return 0; }
    @Override public List<Object[]> query(String sql, Object... values) {
      this.sql = sql;
      this.values = values;
      if (sql.contains("GROUP BY embedding_model")) return groups;
      return groups.isEmpty() ? List.of() : List.<Object[]>of(new Object[] {"text", "source", "id", 0.1});
    }
  }

  private static class RecordingExecutor implements KnowledgeBaseStore.SqlExecutor {
    String sql;
    Object[] values;
    @Override public boolean hasPgVector() { return true; }
    @Override public boolean hasTable() { return true; }
    @Override public int update(String sql, Object... values) { this.sql = sql; this.values = values; return 0; }
    @Override public List<Object[]> query(String sql, Object... values) {
      this.sql = sql;
      this.values = values;
      return List.of();
    }
  }

  @Test
  public void runtimeGuardDoesNotIssueDdlWhenSchemaIsUnavailable() {
    KnowledgeBaseStore.SqlExecutor executor = new RecordingExecutor() {
      @Override public boolean hasPgVector() { return false; }
      @Override public boolean hasTable() { return false; }
    };
    org.openbravo.base.exception.OBException error = expectThrows(org.openbravo.base.exception.OBException.class,
        () -> new KnowledgeBaseStore(executor).ensureReady());
    assertTrue(error.getMessage().contains("run update.database"));
  }
  @Test
  public void rejectsMissingScope() {
    expectThrows(IllegalArgumentException.class, () -> KnowledgeBaseSql.scope("", "client"));
  }

  private static <T extends Throwable> T expectThrows(Class<T> expected, Runnable action) {
    try {
      action.run();
    } catch (Throwable failure) {
      if (expected.isInstance(failure)) return expected.cast(failure);
      throw new AssertionError("Unexpected exception type: " + failure.getClass().getName(), failure);
    }
    fail("Expected " + expected.getName());
    throw new AssertionError("unreachable");
  }
}
