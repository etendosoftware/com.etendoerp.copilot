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
 * Tests for OpenAPISpecFlowFile focusing on utility methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class OpenAPISpecFlowFileNewTest {
  private static final String SPEC_URL = "https://example.com/api/spec.yaml";
  private static final String NO_EXT_URL = "https://example.com/api/spec";
  private static final String EXPECTED_PRIORITY = "Expected default priority to be 100";
  private static final int DEFAULT_PRIORITY_VALUE = 100;

  private OpenAPISpecFlowFile hook;

  /** Set up the OpenAPISpecFlowFile instance for each test. */
  @Before
  public void setUp() {
    hook = initFlowFileHook();
  }

  private OpenAPISpecFlowFile initFlowFileHook() {
    return new OpenAPISpecFlowFile();
  }

  // --- typeCheck tests ---

  /** Test type check f l o w. */
  @Test
  public void testTypeCheckFLOW() {
    assertTrue("FLOW type should be accepted", hook.typeCheck("FLOW"));
  }

  /** Test type check other. */
  @Test
  public void testTypeCheckOther() {
    assertFalse(hook.typeCheck("TXT"));
  }

  /** Test type check null. */
  @Test
  public void testTypeCheckNull() {
    boolean result = hook.typeCheck(null);
    assertFalse("Null input should return false", result);
  }

  /** Test type check empty. */
  @Test
  public void testTypeCheckEmpty() {
    assertFalse(hook.typeCheck(""));
  }

  /** Test type check case insensitive fails. */
  @Test
  public void testTypeCheckCaseInsensitiveFails() {
    assertFalse(hook.typeCheck("flow"));
  }

  /** Test type check r f. */
  @Test
  public void testTypeCheckRF() {
    assertFalse(hook.typeCheck("RF"));
  }

  // --- getFinalName tests ---

  /**
   * Builds a URL and asserts that getFinalName returns the expected result
   * for the given custom name.
   */
  private void verifyFinalName(String expected, String customName,
      String urlString) throws Exception {
    URL parsedUrl = new URL(urlString);
    String actual = OpenAPISpecFlowFile.getFinalName(customName, parsedUrl);
    assertEquals(expected, actual);
  }

  /**
   * Test get final name with custom name having extension.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithCustomNameHavingExtension() throws Exception {
    verifyFinalName("custom.json", "custom.json", SPEC_URL);
  }

  /**
   * Test get final name with custom name no extension.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithCustomNameNoExtension() throws Exception {
    verifyFinalName("custom.yaml", "custom", SPEC_URL);
  }

  /**
   * Test get final name with null custom name.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithNullCustomName() throws Exception {
    verifyFinalName("spec.yaml", null, SPEC_URL);
  }

  /**
   * Test get final name with empty custom name.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameWithEmptyCustomName() throws Exception {
    verifyFinalName("spec.yaml", "", SPEC_URL);
  }

  /**
   * Test get final name no ext in url.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameNoExtInUrl() throws Exception {
    verifyFinalName("spec", null, NO_EXT_URL);
  }

  /**
   * Test get final name custom name no ext url no ext.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetFinalNameCustomNameNoExtUrlNoExt() throws Exception {
    verifyFinalName("myfile", "myfile", NO_EXT_URL);
  }

  // --- constants tests ---

  /** Test copilot file tab id. */
  @Test
  public void testCopilotFileTabId() {
    assertEquals("09F802E423924081BC2947A64DDB5AF5", OpenAPISpecFlowFile.COPILOT_FILE_TAB_ID);
  }

  /** Test copilot file ad table id. */
  @Test
  public void testCopilotFileAdTableId() {
    assertEquals("6B246B1B3A6F4DE8AFC208E07DB29CE2", OpenAPISpecFlowFile.COPILOT_FILE_AD_TABLE_ID);
  }

  // --- getPriority test ---

  /** Test get priority default. */
  @Test
  public void testGetPriorityDefault() {
    assertEquals(EXPECTED_PRIORITY, DEFAULT_PRIORITY_VALUE, hook.getPriority());
  }
}
