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
 * Tests for RemoteFileHook focusing on utility methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class RemoteFileHookNewTest {

  private RemoteFileHook hook;

  @Before
  public void setUp() {
    hook = new RemoteFileHook();
  }

  // --- typeCheck tests ---

  @Test
  public void testTypeCheckRF() {
    assertTrue(hook.typeCheck("RF"));
  }

  @Test
  public void testTypeCheckOtherType() {
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
    assertFalse(hook.typeCheck("rf"));
  }

  // --- getFinalName tests ---

  @Test
  public void testGetFinalNameWithCustomNameAndExtension() throws Exception {
    URL url = new URL("https://example.com/data/report.pdf");
    assertEquals("custom.xlsx", RemoteFileHook.getFinalName("custom.xlsx", url));
  }

  @Test
  public void testGetFinalNameWithCustomNameNoExtension() throws Exception {
    URL url = new URL("https://example.com/data/report.pdf");
    assertEquals("custom.pdf", RemoteFileHook.getFinalName("custom", url));
  }

  @Test
  public void testGetFinalNameWithNullCustomName() throws Exception {
    URL url = new URL("https://example.com/data/report.pdf");
    assertEquals("report.pdf", RemoteFileHook.getFinalName(null, url));
  }

  @Test
  public void testGetFinalNameWithEmptyCustomName() throws Exception {
    URL url = new URL("https://example.com/data/report.pdf");
    assertEquals("report.pdf", RemoteFileHook.getFinalName("", url));
  }

  @Test
  public void testGetFinalNameNoExtensionInUrl() throws Exception {
    URL url = new URL("https://example.com/data/report");
    assertEquals("report", RemoteFileHook.getFinalName(null, url));
  }

  @Test
  public void testGetFinalNameCustomNameNoExtUrlNoExt() throws Exception {
    URL url = new URL("https://example.com/data/report");
    assertEquals("myname", RemoteFileHook.getFinalName("myname", url));
  }

  @Test
  public void testGetFinalNameWithQueryParams() throws Exception {
    URL url = new URL("https://example.com/data/report.csv?v=2");
    // getFile() returns /data/report.csv?v=2
    // lastIndexOf('/') gives us report.csv?v=2
    // lastIndexOf('.') gives us .csv?v=2
    String result = RemoteFileHook.getFinalName(null, url);
    assertTrue(result.contains("report"));
  }

  // --- getPriority test ---

  @Test
  public void testGetPriorityDefault() {
    assertEquals(100, hook.getPriority());
  }
}
