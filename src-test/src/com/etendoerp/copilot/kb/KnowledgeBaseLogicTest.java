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
    assertTrue(KnowledgeBaseSql.searchSql().contains("ad_client_id IN (?,?)"));
  }


  @Test
  public void storeSearchBuildsScopedParameterizedQuery() {
    RecordingExecutor executor = new RecordingExecutor();
    KnowledgeBaseStore store = new KnowledgeBaseStore(executor);
    store.search("KB_agent", "client-7", "KB_agent", new float[] {0.25f, 0.5f}, 4);
    assertTrue(executor.sql.contains("ad_client_id IN (?,?)"));
    assertEquals("KB_agent", executor.values[1]);
    assertEquals("KB_agent", executor.values[2]);
    assertEquals("0", executor.values[3]);
    assertEquals("client-7", executor.values[4]);
    assertEquals(4, executor.values[6]);
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
