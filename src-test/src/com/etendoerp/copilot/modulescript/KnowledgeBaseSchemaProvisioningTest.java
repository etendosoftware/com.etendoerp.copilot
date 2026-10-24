package com.etendoerp.copilot.modulescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class KnowledgeBaseSchemaProvisioningTest {
  @Test
  public void doesNotIssueDdlWhenPgvectorIsAbsent() throws Exception {
    RecordingExecutor executor = new RecordingExecutor(false);
    assertFalse(KnowledgeBaseSchemaProvisioning.provision(executor));
    assertTrue(executor.statements.isEmpty());
  }

  @Test
  public void provisionsIdempotentTableAndIndexesWhenPgvectorExists() throws Exception {
    RecordingExecutor executor = new RecordingExecutor(true);
    assertTrue(KnowledgeBaseSchemaProvisioning.provision(executor));
    assertEquals(6, executor.statements.size());
    assertTrue(executor.statements.get(0).contains("CREATE TABLE IF NOT EXISTS etcop_kb_chunk"));
    assertTrue(executor.statements.get(0).contains("embedding_model varchar(200) NOT NULL"));
    assertTrue(executor.statements.get(0).contains("embedding_dims int NOT NULL"));
    assertTrue(executor.statements.get(1).contains("ADD COLUMN IF NOT EXISTS embedding_model"));
    assertTrue(executor.statements.get(2).contains("ADD COLUMN IF NOT EXISTS embedding_dims"));
    assertTrue(executor.statements.get(4).contains("embedding_dims)"));
    assertTrue(executor.statements.get(5).contains("DROP INDEX IF EXISTS etcop_kb_embedding_idx"));
  }

  private static final class RecordingExecutor implements KnowledgeBaseSchemaProvisioning.Executor {
    private final boolean available;
    private final List<String> statements = new ArrayList<>();
    private RecordingExecutor(boolean available) { this.available = available; }
    @Override public boolean hasPgVector() { return available; }
    @Override public void execute(String sql) { statements.add(sql); }
  }
}
