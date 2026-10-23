package com.etendoerp.copilot.engine.tool;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.openbravo.base.weld.test.WeldBaseTest;

/** Verifies cross-module named server tools are resolvable by CDI. */
public class ServerToolDiscoveryTest extends WeldBaseTest {

  @Test
  public void resolvesToolpackAndDevassistantBeansByEtcopToolName() {
    ServerToolRegistry registry = new ServerToolRegistry();
    assertTrue(registry.hasImplementation("ReadFileTool"));
    assertTrue(registry.hasImplementation("TestRunTool"));
  }
}
