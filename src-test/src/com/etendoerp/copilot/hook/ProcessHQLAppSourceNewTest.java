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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

/**
 * Tests for ProcessHQLAppSource focusing on testable static/utility methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProcessHQLAppSourceNewTest {
  private static final String ADDALIASESFORCOLUMNS = "addAliasesForColumns";
  private static final String MYFILE_CSV = "myfile.csv";
  private static final String PRINTOBJECT = "printObject";
  private static final String VALUE1 = "value1";


  private CopilotAppSource createMockSource(String filename, String name) {
    CopilotAppSource source = mock(CopilotAppSource.class);
    CopilotFile file = mock(CopilotFile.class);
    when(source.getFile()).thenReturn(file);
    lenient().when(file.getFilename()).thenReturn(filename);
    lenient().when(file.getName()).thenReturn(name);
    return source;
  }

  // --- getFileName tests ---

  /** Test get file name with filename. */
  @Test
  public void testGetFileNameWithFilename() {
    CopilotAppSource source = createMockSource(MYFILE_CSV, "MyFile");
    assertEquals(MYFILE_CSV, ProcessHQLAppSource.getFileName(source));
  }

  /** Test get file name with name only. */
  @Test
  public void testGetFileNameWithNameOnly() {
    CopilotAppSource source = createMockSource("", "My Report");
    assertEquals("My_Report.csv", ProcessHQLAppSource.getFileName(source));
  }

  /** Test get file name with name containing dot. */
  @Test
  public void testGetFileNameWithNameContainingDot() {
    CopilotAppSource source = createMockSource("", "report.txt");
    assertEquals("report.txt", ProcessHQLAppSource.getFileName(source));
  }

  /** Test get file name with special chars. */
  @Test
  public void testGetFileNameWithSpecialChars() {
    CopilotAppSource source = createMockSource("", "My$Report@2024.csv");
    assertEquals("MyReport2024.csv", ProcessHQLAppSource.getFileName(source));
  }

  /** Test get file name with null filename and null name. */
  @Test
  public void testGetFileNameWithNullFilenameAndNullName() {
    CopilotAppSource source = createMockSource(null, null);
    String result = ProcessHQLAppSource.getFileName(source);
    assertTrue(result.startsWith("result"));
    assertTrue(result.endsWith(".csv"));
  }

  /** Test get file name with empty filename and empty name. */
  @Test
  public void testGetFileNameWithEmptyFilenameAndEmptyName() {
    CopilotAppSource source = createMockSource("", "");
    String result = ProcessHQLAppSource.getFileName(source);
    assertTrue(result.startsWith("result"));
    assertTrue(result.endsWith(".csv"));
  }

  /** Test get file name with spaces. */
  @Test
  public void testGetFileNameWithSpaces() {
    CopilotAppSource source = createMockSource("", "my file name.xlsx");
    assertEquals("my_file_name.xlsx", ProcessHQLAppSource.getFileName(source));
  }

  /** Test get file name with no extension. */
  @Test
  public void testGetFileNameWithNoExtension() {
    CopilotAppSource source = createMockSource("", "myfile");
    assertEquals(MYFILE_CSV, ProcessHQLAppSource.getFileName(source));
  }

  /** Test get file name with filename containing extension. */
  @Test
  public void testGetFileNameWithFilenameContainingExtension() {
    CopilotAppSource source = createMockSource("data.json", "Name");
    assertEquals("data.json", ProcessHQLAppSource.getFileName(source));
  }

  // --- addAliasesForColumns tests (via reflection) ---

  /**
   * Test add aliases for columns basic.
   * @throws Exception if an error occurs
   */
  @Test
  public void testAddAliasesForColumnsBasic() throws Exception {
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(
        ADDALIASESFORCOLUMNS, List.class, String[].class);
    method.setAccessible(true);

    List<String> values = new ArrayList<>();
    values.add(VALUE1);
    values.add("value2");
    String[] headers = {"col1", "col2"};

    method.invoke(null, values, headers);
    assertEquals("col1: value1", values.get(0));
    assertEquals("col2: value2", values.get(1));
  }

  /**
   * Test add aliases for columns with missing headers.
   * @throws Exception if an error occurs
   */
  @Test
  public void testAddAliasesForColumnsWithMissingHeaders() throws Exception {
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(
        ADDALIASESFORCOLUMNS, List.class, String[].class);
    method.setAccessible(true);

    List<String> values = new ArrayList<>();
    values.add(VALUE1);
    values.add("value2");
    values.add("value3");
    String[] headers = {"col1"};

    method.invoke(null, values, headers);
    assertEquals("col1: value1", values.get(0));
    assertEquals("?: value2", values.get(1));
    assertEquals("?: value3", values.get(2));
  }

  /**
   * Test add aliases for columns with empty headers.
   * @throws Exception if an error occurs
   */
  @Test
  public void testAddAliasesForColumnsWithEmptyHeaders() throws Exception {
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(
        ADDALIASESFORCOLUMNS, List.class, String[].class);
    method.setAccessible(true);

    List<String> values = new ArrayList<>();
    values.add(VALUE1);
    String[] headers = {""};

    method.invoke(null, values, headers);
    assertEquals("?: value1", values.get(0));
  }

  /**
   * Test add aliases for columns empty values.
   * @throws Exception if an error occurs
   */
  @Test
  public void testAddAliasesForColumnsEmptyValues() throws Exception {
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(
        ADDALIASESFORCOLUMNS, List.class, String[].class);
    method.setAccessible(true);

    List<String> values = new ArrayList<>();
    String[] headers = {"col1", "col2"};

    method.invoke(null, values, headers);
    assertTrue(values.isEmpty());
  }

  // --- printObject tests (via reflection) ---

  /**
   * Test print object null.
   * @throws Exception if an error occurs
   */
  @Test
  public void testPrintObjectNull() throws Exception {
    ProcessHQLAppSource instance = ProcessHQLAppSource.getInstance();
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(PRINTOBJECT, Object.class);
    method.setAccessible(true);

    String result = (String) method.invoke(instance, (Object) null);
    assertEquals("NULL", result);
  }

  /**
   * Test print object string.
   * @throws Exception if an error occurs
   */
  @Test
  public void testPrintObjectString() throws Exception {
    ProcessHQLAppSource instance = ProcessHQLAppSource.getInstance();
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(PRINTOBJECT, Object.class);
    method.setAccessible(true);

    String result = (String) method.invoke(instance, "hello");
    assertEquals("hello", result);
  }

  /**
   * Test print object integer.
   * @throws Exception if an error occurs
   */
  @Test
  public void testPrintObjectInteger() throws Exception {
    ProcessHQLAppSource instance = ProcessHQLAppSource.getInstance();
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(PRINTOBJECT, Object.class);
    method.setAccessible(true);

    String result = (String) method.invoke(instance, 42);
    assertEquals("42", result);
  }

  // --- getInstance test ---

  /** Test get instance. */
  @Test
  public void testGetInstance() {
    ProcessHQLAppSource instance1 = ProcessHQLAppSource.getInstance();
    ProcessHQLAppSource instance2 = ProcessHQLAppSource.getInstance();
    assertNotNull(instance1);
    assertEquals(instance1, instance2);
  }
}
