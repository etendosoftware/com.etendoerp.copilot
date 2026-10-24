package com.etendoerp.copilot.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class KnowledgeBaseSearchTest {
  @Test
  public void exposesOnlyTheQueryArgument() {
    var specification = new KnowledgeBaseSearch().specification();
    assertEquals("KnowledgeBaseSearch", specification.name());
    assertTrue(specification.parameters().properties().containsKey("query"));
    assertTrue(specification.parameters().required().contains("query"));
    assertEquals(1, specification.parameters().properties().size());
  }
}
