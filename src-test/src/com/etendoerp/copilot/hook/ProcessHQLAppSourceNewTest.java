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

  private CopilotAppSource createMockSource(String filename, String name) {
    CopilotAppSource source = mock(CopilotAppSource.class);
    CopilotFile file = mock(CopilotFile.class);
    when(source.getFile()).thenReturn(file);
    lenient().when(file.getFilename()).thenReturn(filename);
    lenient().when(file.getName()).thenReturn(name);
    return source;
  }

  // --- getFileName tests ---

  @Test
  public void testGetFileNameWithFilename() {
    CopilotAppSource source = createMockSource("myfile.csv", "MyFile");
    assertEquals("myfile.csv", ProcessHQLAppSource.getFileName(source));
  }

  @Test
  public void testGetFileNameWithNameOnly() {
    CopilotAppSource source = createMockSource("", "My Report");
    assertEquals("My_Report.csv", ProcessHQLAppSource.getFileName(source));
  }

  @Test
  public void testGetFileNameWithNameContainingDot() {
    CopilotAppSource source = createMockSource("", "report.txt");
    assertEquals("report.txt", ProcessHQLAppSource.getFileName(source));
  }

  @Test
  public void testGetFileNameWithSpecialChars() {
    CopilotAppSource source = createMockSource("", "My$Report@2024.csv");
    assertEquals("MyReport2024.csv", ProcessHQLAppSource.getFileName(source));
  }

  @Test
  public void testGetFileNameWithNullFilenameAndNullName() {
    CopilotAppSource source = createMockSource(null, null);
    String result = ProcessHQLAppSource.getFileName(source);
    assertTrue(result.startsWith("result"));
    assertTrue(result.endsWith(".csv"));
  }

  @Test
  public void testGetFileNameWithEmptyFilenameAndEmptyName() {
    CopilotAppSource source = createMockSource("", "");
    String result = ProcessHQLAppSource.getFileName(source);
    assertTrue(result.startsWith("result"));
    assertTrue(result.endsWith(".csv"));
  }

  @Test
  public void testGetFileNameWithSpaces() {
    CopilotAppSource source = createMockSource("", "my file name.xlsx");
    assertEquals("my_file_name.xlsx", ProcessHQLAppSource.getFileName(source));
  }

  @Test
  public void testGetFileNameWithNoExtension() {
    CopilotAppSource source = createMockSource("", "myfile");
    assertEquals("myfile.csv", ProcessHQLAppSource.getFileName(source));
  }

  @Test
  public void testGetFileNameWithFilenameContainingExtension() {
    CopilotAppSource source = createMockSource("data.json", "Name");
    assertEquals("data.json", ProcessHQLAppSource.getFileName(source));
  }

  // --- addAliasesForColumns tests (via reflection) ---

  @Test
  public void testAddAliasesForColumnsBasic() throws Exception {
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(
        "addAliasesForColumns", List.class, String[].class);
    method.setAccessible(true);

    List<String> values = new ArrayList<>();
    values.add("value1");
    values.add("value2");
    String[] headers = {"col1", "col2"};

    method.invoke(null, values, headers);
    assertEquals("col1: value1", values.get(0));
    assertEquals("col2: value2", values.get(1));
  }

  @Test
  public void testAddAliasesForColumnsWithMissingHeaders() throws Exception {
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(
        "addAliasesForColumns", List.class, String[].class);
    method.setAccessible(true);

    List<String> values = new ArrayList<>();
    values.add("value1");
    values.add("value2");
    values.add("value3");
    String[] headers = {"col1"};

    method.invoke(null, values, headers);
    assertEquals("col1: value1", values.get(0));
    assertEquals("?: value2", values.get(1));
    assertEquals("?: value3", values.get(2));
  }

  @Test
  public void testAddAliasesForColumnsWithEmptyHeaders() throws Exception {
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(
        "addAliasesForColumns", List.class, String[].class);
    method.setAccessible(true);

    List<String> values = new ArrayList<>();
    values.add("value1");
    String[] headers = {""};

    method.invoke(null, values, headers);
    assertEquals("?: value1", values.get(0));
  }

  @Test
  public void testAddAliasesForColumnsEmptyValues() throws Exception {
    Method method = ProcessHQLAppSource.class.getDeclaredMethod(
        "addAliasesForColumns", List.class, String[].class);
    method.setAccessible(true);

    List<String> values = new ArrayList<>();
    String[] headers = {"col1", "col2"};

    method.invoke(null, values, headers);
    assertTrue(values.isEmpty());
  }

  // --- printObject tests (via reflection) ---

  @Test
  public void testPrintObjectNull() throws Exception {
    ProcessHQLAppSource instance = ProcessHQLAppSource.getInstance();
    Method method = ProcessHQLAppSource.class.getDeclaredMethod("printObject", Object.class);
    method.setAccessible(true);

    String result = (String) method.invoke(instance, (Object) null);
    assertEquals("NULL", result);
  }

  @Test
  public void testPrintObjectString() throws Exception {
    ProcessHQLAppSource instance = ProcessHQLAppSource.getInstance();
    Method method = ProcessHQLAppSource.class.getDeclaredMethod("printObject", Object.class);
    method.setAccessible(true);

    String result = (String) method.invoke(instance, "hello");
    assertEquals("hello", result);
  }

  @Test
  public void testPrintObjectInteger() throws Exception {
    ProcessHQLAppSource instance = ProcessHQLAppSource.getInstance();
    Method method = ProcessHQLAppSource.class.getDeclaredMethod("printObject", Object.class);
    method.setAccessible(true);

    String result = (String) method.invoke(instance, 42);
    assertEquals("42", result);
  }

  // --- getInstance test ---

  @Test
  public void testGetInstance() {
    ProcessHQLAppSource instance1 = ProcessHQLAppSource.getInstance();
    ProcessHQLAppSource instance2 = ProcessHQLAppSource.getInstance();
    assertNotNull(instance1);
    assertEquals(instance1, instance2);
  }
}
