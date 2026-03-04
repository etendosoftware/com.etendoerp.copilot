package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

  // --- readCsvFile tests ---

  @Test
  public void testReadCsvFileBasic() throws Exception {
    File csvFile = File.createTempFile("test", ".csv");
    csvFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(csvFile)) {
      writer.write("name,age,city\n");
      writer.write("Alice,30,NYC\n");
      writer.write("Bob,25,LA\n");
    }

    String[] results = AddBulkTasks.readCsvFile(csvFile, ",");
    assertEquals(2, results.length);

    JSONObject row1 = new JSONObject(results[0]);
    assertEquals("Alice", row1.getString("name"));
    assertEquals("30", row1.getString("age"));
    assertEquals("NYC", row1.getString("city"));

    JSONObject row2 = new JSONObject(results[1]);
    assertEquals("Bob", row2.getString("name"));
    assertEquals("25", row2.getString("age"));
    assertEquals("LA", row2.getString("city"));
  }

  @Test
  public void testReadCsvFileWithSemicolonSeparator() throws Exception {
    File csvFile = File.createTempFile("test", ".csv");
    csvFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(csvFile)) {
      writer.write("name;value\n");
      writer.write("item1;100\n");
    }

    String[] results = AddBulkTasks.readCsvFile(csvFile, ";");
    assertEquals(1, results.length);

    JSONObject row = new JSONObject(results[0]);
    assertEquals("item1", row.getString("name"));
    assertEquals("100", row.getString("value"));
  }

  @Test
  public void testReadCsvFileEmptyFile() throws Exception {
    File csvFile = File.createTempFile("test", ".csv");
    csvFile.deleteOnExit();

    // Write nothing (empty file will have null headerLine)
    try (FileWriter writer = new FileWriter(csvFile)) {
      // empty
    }

    String[] results = AddBulkTasks.readCsvFile(csvFile, ",");
    assertEquals(0, results.length);
  }

  @Test
  public void testReadCsvFileHeaderOnly() throws Exception {
    File csvFile = File.createTempFile("test", ".csv");
    csvFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(csvFile)) {
      writer.write("name,age\n");
    }

    String[] results = AddBulkTasks.readCsvFile(csvFile, ",");
    assertEquals(0, results.length);
  }

  @Test
  public void testReadCsvFileMissingValues() throws Exception {
    File csvFile = File.createTempFile("test", ".csv");
    csvFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(csvFile)) {
      writer.write("a,b,c\n");
      writer.write("1\n");
    }

    String[] results = AddBulkTasks.readCsvFile(csvFile, ",");
    assertEquals(1, results.length);

    JSONObject row = new JSONObject(results[0]);
    assertEquals("1", row.getString("a"));
    assertEquals("", row.getString("b"));
    assertEquals("", row.getString("c"));
  }

  @Test
  public void testReadCsvFileTrimsValues() throws Exception {
    File csvFile = File.createTempFile("test", ".csv");
    csvFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(csvFile)) {
      writer.write(" name , value \n");
      writer.write(" Alice , 30 \n");
    }

    String[] results = AddBulkTasks.readCsvFile(csvFile, ",");
    assertEquals(1, results.length);

    JSONObject row = new JSONObject(results[0]);
    assertEquals("Alice", row.getString("name"));
    assertEquals("30", row.getString("value"));
  }

  // --- COPILOT constant test ---

  @Test
  public void testCopilotConstant() {
    assertEquals("Copilot", AddBulkTasks.COPILOT);
  }
}
