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
package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for AddBulkTasks focusing on utility methods that can be exercised without DAL.
 */
@RunWith(MockitoJUnitRunner.class)
public class AddBulkTasksNewTest {

  /**
   * Creates a temporary CSV file with the given content lines, reads it using
   * {@link AddBulkTasks#readCsvFile(File, String)}, asserts the expected row count,
   * and returns the parsed results.
   *
   * @param separator     the CSV separator
   * @param expectedRows  expected number of data rows in the result
   * @param lines         content lines to write (each element is one line, newline appended automatically)
   * @return the String array returned by {@code readCsvFile}
   * @throws IOException   if file I/O fails
   * @throws JSONException if JSON parsing fails
   */
  private String[] readCsvAndAssertRowCount(String separator, int expectedRows,
      String... lines) throws IOException, JSONException {
    File csvFile = Files.createTempFile("test", ".csv").toFile();
    csvFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(csvFile)) {
      for (String line : lines) {
        writer.write(line + "\n");
      }
    }

    String[] results = AddBulkTasks.readCsvFile(csvFile, separator);
    assertEquals(expectedRows, results.length);
    return results;
  }

  /**
   * Parses the given JSON string and asserts that every key in {@code expectedPairs}
   * maps to the corresponding value.
   *
   * @param jsonString    the JSON string to parse
   * @param expectedPairs alternating key/value pairs (key1, value1, key2, value2, ...)
   * @throws JSONException if JSON parsing fails
   */
  private void assertJsonFields(String jsonString,
      String... expectedPairs) throws JSONException {
    JSONObject row = new JSONObject(jsonString);
    for (int i = 0; i < expectedPairs.length; i += 2) {
      assertEquals(expectedPairs[i + 1], row.getString(expectedPairs[i]));
    }
  }

  // --- readCsvFile tests ---

  /**
   * Test read csv file basic.
   * @throws IOException if file I/O fails
   * @throws JSONException if JSON parsing fails
   */
  @Test
  public void testReadCsvFileBasic() throws IOException, JSONException {
    String[] results = readCsvAndAssertRowCount(",", 2,
        "name,age,city", "Alice,30,NYC", "Bob,25,LA");

    assertJsonFields(results[0], "name", "Alice", "age", "30", "city", "NYC");
    assertJsonFields(results[1], "name", "Bob", "age", "25", "city", "LA");
  }

  /**
   * Test read csv file with semicolon separator.
   * @throws IOException if file I/O fails
   * @throws JSONException if JSON parsing fails
   */
  @Test
  public void testReadCsvFileWithSemicolonSeparator() throws IOException, JSONException {
    String[] results = readCsvAndAssertRowCount(";", 1,
        "name;value", "item1;100");

    assertJsonFields(results[0], "name", "item1", "value", "100");
  }

  /**
   * Test read csv file empty file.
   * @throws IOException if file I/O fails
   * @throws JSONException if JSON parsing fails
   */
  @Test
  public void testReadCsvFileEmptyFile() throws IOException, JSONException {
    readCsvAndAssertRowCount(",", 0);
  }

  /**
   * Test read csv file header only.
   * @throws IOException if file I/O fails
   * @throws JSONException if JSON parsing fails
   */
  @Test
  public void testReadCsvFileHeaderOnly() throws IOException, JSONException {
    readCsvAndAssertRowCount(",", 0, "name,age");
  }

  /**
   * Test read csv file missing values.
   * @throws IOException if file I/O fails
   * @throws JSONException if JSON parsing fails
   */
  @Test
  public void testReadCsvFileMissingValues() throws IOException, JSONException {
    String[] results = readCsvAndAssertRowCount(",", 1,
        "a,b,c", "1");

    assertJsonFields(results[0], "a", "1", "b", "", "c", "");
  }

  /**
   * Test read csv file trims values.
   * @throws IOException if file I/O fails
   * @throws JSONException if JSON parsing fails
   */
  @Test
  public void testReadCsvFileTrimsValues() throws IOException, JSONException {
    String[] results = readCsvAndAssertRowCount(",", 1,
        " name , value ", " Alice , 30 ");

    assertJsonFields(results[0], "name", "Alice", "value", "30");
  }

  // --- COPILOT constant test ---

  /** Test copilot constant. */
  @Test
  public void testCopilotConstant() {
    assertEquals("Copilot", AddBulkTasks.COPILOT);
  }
}
