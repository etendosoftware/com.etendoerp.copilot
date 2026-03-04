package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for OpenAPISpecFlowFile focusing on utility methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class OpenAPISpecFlowFileNewTest {

  private OpenAPISpecFlowFile hook;

  @Before
  public void setUp() {
    hook = new OpenAPISpecFlowFile();
  }

  // --- typeCheck tests ---

  @Test
  public void testTypeCheckFLOW() {
    assertTrue(hook.typeCheck("FLOW"));
  }

  @Test
  public void testTypeCheckOther() {
    assertFalse(hook.typeCheck("TXT"));
  }

  @Test
  public void testTypeCheckNull() {
    assertFalse(hook.typeCheck(null));
  }

  @Test
  public void testTypeCheckEmpty() {
    assertFalse(hook.typeCheck(""));
  }

  @Test
  public void testTypeCheckCaseInsensitiveFails() {
    assertFalse(hook.typeCheck("flow"));
  }

  @Test
  public void testTypeCheckRF() {
    assertFalse(hook.typeCheck("RF"));
  }

  // --- getFinalName tests ---

  @Test
  public void testGetFinalNameWithCustomNameHavingExtension() throws Exception {
    URL url = new URL("https://example.com/api/spec.yaml");
    assertEquals("custom.json", OpenAPISpecFlowFile.getFinalName("custom.json", url));
  }

  @Test
  public void testGetFinalNameWithCustomNameNoExtension() throws Exception {
    URL url = new URL("https://example.com/api/spec.yaml");
    assertEquals("custom.yaml", OpenAPISpecFlowFile.getFinalName("custom", url));
  }

  @Test
  public void testGetFinalNameWithNullCustomName() throws Exception {
    URL url = new URL("https://example.com/api/spec.yaml");
    assertEquals("spec.yaml", OpenAPISpecFlowFile.getFinalName(null, url));
  }

  @Test
  public void testGetFinalNameWithEmptyCustomName() throws Exception {
    URL url = new URL("https://example.com/api/spec.yaml");
    assertEquals("spec.yaml", OpenAPISpecFlowFile.getFinalName("", url));
  }

  @Test
  public void testGetFinalNameNoExtInUrl() throws Exception {
    URL url = new URL("https://example.com/api/spec");
    assertEquals("spec", OpenAPISpecFlowFile.getFinalName(null, url));
  }

  @Test
  public void testGetFinalNameCustomNameNoExtUrlNoExt() throws Exception {
    URL url = new URL("https://example.com/api/spec");
    assertEquals("myfile", OpenAPISpecFlowFile.getFinalName("myfile", url));
  }

  // --- constants tests ---

  @Test
  public void testCopilotFileTabId() {
    assertEquals("09F802E423924081BC2947A64DDB5AF5", OpenAPISpecFlowFile.COPILOT_FILE_TAB_ID);
  }

  @Test
  public void testCopilotFileAdTableId() {
    assertEquals("6B246B1B3A6F4DE8AFC208E07DB29CE2", OpenAPISpecFlowFile.COPILOT_FILE_AD_TABLE_ID);
  }

  // --- getPriority test ---

  @Test
  public void testGetPriorityDefault() {
    assertEquals(100, hook.getPriority());
  }
}
