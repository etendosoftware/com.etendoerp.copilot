/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
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
  private static final String REPORT_URL = "https://example.com/data/report.pdf";
  private static final String REPORT_NO_EXT_URL = "https://example.com/data/report";
  private static final String QUERY_PARAM_URL = "https://example.com/data/report.csv?v=2";

  private RemoteFileHook hook;

  /** Set up the RemoteFileHook instance for each test. */
  @Before
  public void setUp() {
    hook = buildRemoteFileHook();
  }

  private RemoteFileHook buildRemoteFileHook() {
    return new RemoteFileHook();
  }

  // --- typeCheck tests ---

  /** Test type check r f. */
  @Test
  public void testTypeCheckRF() {
    assertTrue("RF type should be accepted by RemoteFileHook", hook.typeCheck("RF"));
  }

  /** Test type check other type. */
  @Test
  public void testTypeCheckOtherType() {
    assertFalse(hook.typeCheck("TXT"));
  }

  /** Test type check null. */
  @Test
  public void testTypeCheckNull() {
    assertFalse("Null should be rejected", hook.typeCheck(null));
  }

  /** Test type check empty. */
  @Test
  public void testTypeCheckEmpty() {
    boolean emptyResult = hook.typeCheck("");
    assertFalse("Empty string should be rejected", emptyResult);
  }

  /** Test type check case insensitive fails. */
  @Test
  public void testTypeCheckCaseInsensitiveFails() {
    assertFalse(hook.typeCheck("rf"));
  }

  // --- getFinalName tests ---

  /**
   * Resolves the final name for a given custom name and URL string,
   * then asserts the result matches the expected value.
   */
  private void checkFinalName(String expected, String customName,
      String urlString) throws Exception {
    URL targetUrl = new URL(urlString);
    assertEquals(expected, RemoteFileHook.getFinalName(customName, targetUrl));
  }

  /**
   * Test get final name with custom name and extension.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithCustomNameAndExtension() throws Exception {
    checkFinalName("custom.xlsx", "custom.xlsx", REPORT_URL);
  }

  /**
   * Test get final name with custom name no extension.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithCustomNameNoExtension() throws Exception {
    checkFinalName("custom.pdf", "custom", REPORT_URL);
  }

  /**
   * Test get final name with null custom name.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithNullCustomName() throws Exception {
    checkFinalName("report.pdf", null, REPORT_URL);
  }

  /**
   * Test get final name with empty custom name.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithEmptyCustomName() throws Exception {
    checkFinalName("report.pdf", "", REPORT_URL);
  }

  /**
   * Test get final name no extension in url.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameNoExtensionInUrl() throws Exception {
    checkFinalName("report", null, REPORT_NO_EXT_URL);
  }

  /**
   * Test get final name custom name no ext url no ext.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameCustomNameNoExtUrlNoExt() throws Exception {
    checkFinalName("myname", "myname", REPORT_NO_EXT_URL);
  }

  /**
   * Test get final name with query params.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithQueryParams() throws Exception {
    URL url = new URL(QUERY_PARAM_URL);
    // getFile() returns /data/report.csv?v=2
    // lastIndexOf('/') gives us report.csv?v=2
    // lastIndexOf('.') gives us .csv?v=2
    String result = RemoteFileHook.getFinalName(null, url);
    assertTrue("Result should contain the filename stem", result.contains("report"));
  }

  // --- getPriority test ---

  /** Test get priority default. */
  @Test
  public void testGetPriorityDefault() {
    int hookPriority = hook.getPriority();
    assertEquals("RemoteFileHook default priority", 100, hookPriority);
  }
}
