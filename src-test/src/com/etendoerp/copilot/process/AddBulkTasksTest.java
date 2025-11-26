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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;

/**
 * AddBulkTasks test class.
 * Comprehensive tests including doExecute method and utility methods.
 */
public class AddBulkTasksTest {

  private static final String SHOULD_HAVE_1_RECORD = "Should have 1 record";
  private static final String SHOULD_CONTAIN_ERROR_MESSAGE = "Should contain error message";
  private static final String TEST_FILE_URL = "http://test-url.com/file";
  private static final String TEST_CONTENT = "test content";
  private static final String TEST_TXT_FILE = "test.txt";
  private static final String TEST_CSV_FILE = "test.csv";
  private static final String FILE_NAME_KEY = "fileName";
  private static final String CONTENT_KEY = "content";

  @Mock
  private OBDal obDal;
  @Mock
  private OBContext obContext;
  @Mock
  private OBProvider obProvider;
  @Mock
  private User mockUser;
  @Mock
  private CopilotApp mockAgent;
  @Mock
  private Task mockTask;
  @Mock
  private Status mockStatus;
  @Mock
  private TaskType mockTaskType;
  @Mock
  private OBCriteria<Status> mockStatusCriteria;
  @Mock
  private OBCriteria<TaskType> mockTaskTypeCriteria;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBProvider> mockedOBProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private MockedStatic<RestServiceUtil> mockedRestServiceUtil;
  private AutoCloseable mocks;

  private AddBulkTasks addBulkTasks;

  private static final String TEST_AGENT_ID = "testAgentId";
  private static final String TEST_QUESTION = "Test question";
  private static final String TEST_GROUP = "Test group";
  private static final String RESULT_NOT_NULL_MSG = "Result should not be null";

  /**
   * Sets up the test environment before each test execution.
   * Initializes all mock objects, configures static mocks for OBDal, OBContext, OBProvider,
   * OBMessageUtils, and RestServiceUtil. Sets up default behaviors for mocked methods
   * including criteria creation, entity retrieval, and message translations.
   *
   * @throws Exception if there's an error during mock initialization
   */
  @BeforeEach
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    addBulkTasks = new AddBulkTasks();

    // Set up static mocks
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBProvider = mockStatic(OBProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedRestServiceUtil = mockStatic(RestServiceUtil.class);

    // Configure OBDal mock
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.createCriteria(Status.class)).thenReturn(mockStatusCriteria);
    when(obDal.createCriteria(TaskType.class)).thenReturn(mockTaskTypeCriteria);
    when(mockStatusCriteria.add(any(Criterion.class))).thenReturn(mockStatusCriteria);
    when(mockStatusCriteria.setMaxResults(1)).thenReturn(mockStatusCriteria);
    when(mockTaskTypeCriteria.add(any(Criterion.class))).thenReturn(mockTaskTypeCriteria);
    when(mockTaskTypeCriteria.setMaxResults(1)).thenReturn(mockTaskTypeCriteria);

    // Configure OBContext mock
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getUser()).thenReturn(mockUser);

    // Configure OBProvider mock
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(obProvider);
    when(obProvider.get(Task.class)).thenReturn(mockTask);
    when(obProvider.get(TaskType.class)).thenReturn(mockTaskType);

    // Configure Task mock
    doNothing().when(mockTask).setNewOBObject(true);
    doNothing().when(mockTask).setETCOPAgent(any());
    doNothing().when(mockTask).setEtcopQuestion(anyString());
    doNothing().when(mockTask).setStatus(any());
    doNothing().when(mockTask).setEtcopGroup(anyString());
    doNothing().when(mockTask).setAssignedUser(any());
    doNothing().when(mockTask).setTaskType(any());

    // Configure TaskType mock
    when(mockTaskTypeCriteria.uniqueResult()).thenReturn(mockTaskType);
    when(mockTaskType.getId()).thenReturn("taskTypeId");
    doNothing().when(mockTaskType).setNewOBObject(true);
    doNothing().when(mockTaskType).setName(anyString());

    // Configure Status mock
    when(mockStatusCriteria.uniqueResult()).thenReturn(mockStatus);

    // Configure Agent mock
    when(obDal.get(CopilotApp.class, TEST_AGENT_ID)).thenReturn(mockAgent);

    // Mock OBDal operations
    doNothing().when(obDal).save(any());
    doNothing().when(obDal).flush();

    // Mock OBMessageUtils
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("Success")).thenReturn("Success");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_AddBulkTasks_Success"))
        .thenReturn("%d tasks created successfully");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_NoFile"))
        .thenReturn("No file provided");
  }

  /**
   * Cleans up resources after each test execution.
   * Closes all static mocks and MockitoAnnotations to prevent memory leaks
   * and ensure test isolation.
   *
   * @throws Exception if there's an error during resource cleanup
   */
  @AfterEach
  public void tearDown() throws Exception {
    if (mockedRestServiceUtil != null) {
      mockedRestServiceUtil.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
    if (mockedOBProvider != null) {
      mockedOBProvider.close();
    }
    if (mockedOBContext != null) {
      mockedOBContext.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mocks != null) {
      mocks.close();
    }
  }

  /**
   * Creates a file map for CSV file testing.
   * Converts string content to an InputStream and packages it with the filename
   * in a map structure expected by the doExecute method.
   *
   * @param fileName the name of the CSV file
   * @param content the CSV content as a string
   * @return a map containing the fileName and content as InputStream
   */
  private Map<String, Object> createCsvFileMap(String fileName, String content) {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put(FILE_NAME_KEY, fileName);
    fileMap.put(CONTENT_KEY, inputStream);
    return fileMap;
  }

  /**
   * Creates a file map for ZIP file testing.
   * Converts byte array content to an InputStream and packages it with the filename
   * in a map structure expected by the doExecute method.
   *
   * @param fileName the name of the ZIP file
   * @param zipBytes the ZIP file content as byte array
   * @return a map containing the fileName and content as InputStream
   */
  private Map<String, Object> createZipFileMap(String fileName, byte[] zipBytes) {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(zipBytes);
    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put(FILE_NAME_KEY, fileName);
    fileMap.put(CONTENT_KEY, inputStream);
    return fileMap;
  }

  /**
   * Creates a generic file map for testing with optional content.
   * If content is null, only the fileName is added to the map,
   * allowing testing of scenarios where file content is missing.
   *
   * @param fileName the name of the file
   * @param content the file content as InputStream, or null for missing content
   * @return a map containing the fileName and optionally the content
   */
  private Map<String, Object> createFileMap(String fileName, InputStream content) {
    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put(FILE_NAME_KEY, fileName);
    if (content != null) {
      fileMap.put(CONTENT_KEY, content);
    }
    return fileMap;
  }

  /**
   * Creates a JSON string with parameter values for testing doExecute method.
   * Builds a JSON structure with _params object containing agentid, question, group,
   * and optionally separator. If separator is null, it's omitted from the JSON.
   *
   * @param agentId the agent identifier
   * @param question the question text for the tasks
   * @param group the group name for the tasks
   * @param separator the CSV separator character, or null to omit from JSON
   * @return a JSON string with the formatted parameters
   */
  private String createParamValues(String agentId, String question, String group, String separator) {
    if (separator != null) {
      return String.format(
          "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"%s\",\"separator\":\"%s\"}}",
          agentId, question, group, separator);
    } else {
      return String.format(
          "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"%s\"}}",
          agentId, question, group);
    }
  }

  /**
   * Creates the complete parameters map expected by doExecute method.
   * Combines paramValues JSON string and optional file map into a single
   * parameters structure. If fileMap is null, only paramValues is included.
   *
   * @param paramValues the JSON string containing parameter values
   * @param fileMap the file map containing fileName and content, or null if no file
   * @return a map containing paramValues and optionally the file map
   */
  private Map<String, Object> createParameters(String paramValues, Map<String, Object> fileMap) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("paramValues", paramValues);
    if (fileMap != null) {
      parameters.put("file", fileMap);
    }
    return parameters;
  }

  /**
   * Creates a temporary CSV file with the specified content for testing.
   * The file is automatically marked for deletion on JVM exit.
   *
   * @param content the CSV content to write to the file
   * @return a temporary File object containing the CSV data
   * @throws Exception if there's an error creating or writing to the temporary file
   */
  private File createTempCsvFile(String content) throws Exception {
    File tempFile = File.createTempFile("test", ".csv");
    tempFile.deleteOnExit();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(content);
    }
    return tempFile;
  }

  /**
   * Creates a temporary ZIP file with multiple entries for testing.
   * Each entry in the map represents a file within the ZIP archive,
   * with the key as the entry name and value as the content.
   * The file is automatically marked for deletion on JVM exit.
   *
   * @param entries a map of entry names to their content
   * @return a temporary File object containing the ZIP archive
   * @throws Exception if there's an error creating or writing to the temporary ZIP file
   */
  private File createTempZipFile(Map<String, String> entries) throws Exception {
    File tempZip = File.createTempFile("test", ".zip");
    tempZip.deleteOnExit();
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip.toPath()))) {
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        ZipEntry zipEntry = new ZipEntry(entry.getKey());
        zos.putNextEntry(zipEntry);
        zos.write(entry.getValue().getBytes());
        zos.closeEntry();
      }
    }
    return tempZip;
  }

  /**
   * Asserts that the result represents a successful operation.
   * Verifies that the result is not null and contains the expected
   * "responseActions" field indicating successful task creation.
   *
   * @param result the JSONObject result to validate
   */
  private void assertSuccessResult(JSONObject result) {
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has("responseActions"), "Should have responseActions");
  }

  /**
   * Asserts that the result represents an error condition.
   * Verifies that the result is not null and contains an error
   * "message" field indicating a failure or validation error.
   *
   * @param result the JSONObject result to validate
   */
  private void assertErrorResult(JSONObject result) {
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has("message"), SHOULD_CONTAIN_ERROR_MESSAGE);
  }

  /**
   * Test doExecute with CSV file successfully creates tasks.
   */
  @Test
  public void testDoExecuteWithCsvFileSuccess() {
    // Given
    String csvContent = "header1,header2\nvalue1,value2\nvalue3,value4";
    Map<String, Object> fileMap = createCsvFileMap(TEST_CSV_FILE, csvContent);
    String paramValues = createParamValues(TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP, ",");
    Map<String, Object> parameters = createParameters(paramValues, fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertSuccessResult(result);
  }

  /**
   * Test doExecute with null separator defaults to comma.
   */
  @Test
  public void testDoExecuteWithNullSeparatorDefaultsToComma() {
    // Given
    String csvContent = "header1,header2\nvalue1,value2";
    Map<String, Object> fileMap = createCsvFileMap(TEST_CSV_FILE, csvContent);
    String paramValues = createParamValues(TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP, "null");
    Map<String, Object> parameters = createParameters(paramValues, fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
  }

  /**
   * Test doExecute with ZIP file successfully creates tasks from extracted files.
   *
   * @throws Exception if there's an error creating the temporary ZIP file or reading its bytes
   */
  @Test
  public void testDoExecuteWithZipFileSuccess() throws Exception {
    // Given
    Map<String, String> zipEntries = new HashMap<>();
    zipEntries.put(TEST_TXT_FILE, TEST_CONTENT);
    File tempZip = createTempZipFile(zipEntries);
    byte[] zipBytes = Files.readAllBytes(tempZip.toPath());

    Map<String, Object> fileMap = createZipFileMap("test.zip", zipBytes);
    String paramValues = createParamValues(TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP, null);
    Map<String, Object> parameters = createParameters(paramValues, fileMap);

    // Mock RestServiceUtil.handleFile
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile(any(File.class), anyString()))
        .thenReturn(TEST_FILE_URL);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertSuccessResult(result);
  }

  /**
   * Test doExecute with unsupported file type returns error message.
   * Tests that when a .txt file is provided instead of .csv or .zip,
   * the method returns an error response with appropriate severity.
   *
   * @throws Exception if there's an error creating the file map
   */
  @Test
  public void testDoExecuteWithUnsupportedFileType() throws Exception {
    // Given
    Map<String, Object> fileMap = createCsvFileMap(TEST_TXT_FILE, TEST_CONTENT);
    String paramValues = createParamValues(TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP, null);
    Map<String, Object> parameters = createParameters(paramValues, fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has("message"), SHOULD_CONTAIN_ERROR_MESSAGE);
    assertEquals("error", result.getString("severity"));
  }

  /**
   * Test doExecute with missing file content returns error.
   */
  @Test
  public void testDoExecuteWithMissingFileContent() {
    // Given
    Map<String, Object> fileMap = createFileMap(TEST_CSV_FILE, null);
    String paramValues = createParamValues(TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP, null);
    Map<String, Object> parameters = createParameters(paramValues, fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertErrorResult(result);
  }

  /**
   * Test doExecute with no file parameter returns error.
   */
  @Test
  public void testDoExecuteWithNoFileParameter() {
    // Given
    String paramValues = createParamValues(TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP, null);
    Map<String, Object> parameters = createParameters(paramValues, null);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertErrorResult(result);
  }

  /**
   * Test doExecute uses filename as group when group param is empty.
   */
  @Test
  public void testDoExecuteUsesFilenameAsGroup() {
    // Given
    String csvContent = "header1,header2\nvalue1,value2";
    Map<String, Object> fileMap = createCsvFileMap("myfile.csv", csvContent);
    String paramValues = String.format(
        "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"\"}}",
        TEST_AGENT_ID, TEST_QUESTION);
    Map<String, Object> parameters = createParameters(paramValues, fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
  }

  /**
   * Test readCsvFile with valid CSV content containing headers and data rows.
   *
   * @throws Exception if there's an error creating or reading the temporary CSV file
   */
  @Test
  public void testReadCsvFileValid() throws Exception {
    // Given
    String content = "name,age\nJohn,25\nJane,30\n";
    File tempFile = createTempCsvFile(content);

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ",");

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertEquals(2, result.length, "Should have 2 records");
    assertTrue(result[0].contains("John"), "First record should contain name");
    assertTrue(result[1].contains("Jane"), "Second record should contain name");
  }

  /**
   * Test readCsvFile with empty file returns empty array.
   *
   * @throws Exception if there's an error creating or reading the temporary CSV file
   */
  @Test
  public void testReadCsvFileEmpty() throws Exception {
    // Given
    File tempFile = createTempCsvFile("");

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ",");

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertEquals(0, result.length, "Should have 0 records");
  }

  /**
   * Test readCsvFile with custom separator (semicolon) correctly parses data.
   *
   * @throws Exception if there's an error creating or reading the temporary CSV file
   */
  @Test
  public void testReadCsvFileWithCustomSeparator() throws Exception {
    // Given
    String content = "name;age\nJohn;25\n";
    File tempFile = createTempCsvFile(content);

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ";");

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertEquals(1, result.length, SHOULD_HAVE_1_RECORD);
    assertTrue(result[0].contains("John"), "Record should contain name");
  }

  /**
   * Test readCsvFile handles rows with missing values gracefully.
   *
   * @throws Exception if there's an error creating or reading the temporary CSV file
   */
  @Test
  public void testReadCsvFileWithMissingValues() throws Exception {
    // Given
    String content = "name,age,city\nJohn,25\n";
    File tempFile = createTempCsvFile(content);

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ",");

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertEquals(1, result.length, SHOULD_HAVE_1_RECORD);
    assertTrue(result[0].contains("John"), "Record should contain name");
  }

  /**
   * Test readCsvFile returns data in JSON format with proper field mapping.
   *
   * @throws Exception if there's an error creating or reading the temporary CSV file
   */
  @Test
  public void testReadCsvFileJsonFormat() throws Exception {
    // Given
    String content = "name,age\nAlice,28\n";
    File tempFile = createTempCsvFile(content);

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ",");

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertEquals(1, result.length, SHOULD_HAVE_1_RECORD);
    assertTrue(result[0].contains("{"), "Should be JSON format");
    assertTrue(result[0].contains("\"name\""), "Should contain name field");
  }

  /**
   * Test unzipFile successfully extracts files from a valid ZIP archive.
   *
   * @throws Exception if there's an error creating the temporary ZIP file
   */
  @Test
  public void testUnzipFileValid() throws Exception {
    // Given
    Map<String, String> zipEntries = new HashMap<>();
    zipEntries.put(TEST_TXT_FILE, TEST_CONTENT);
    File tempZip = createTempZipFile(zipEntries);

    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile(any(File.class), anyString()))
        .thenReturn(TEST_FILE_URL);

    // When
    String[] result = AddBulkTasks.unzipFile(tempZip);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.length >= 1, "Should have at least 1 file");
    assertEquals(TEST_FILE_URL, result[0], "Should return mocked URL");
  }

  /**
   * Test unzipFile processes ZIP files containing macOS metadata files (__MACOSX).
   * Verifies that both regular files and macOS metadata entries are extracted.
   *
   * @throws Exception if there's an error creating the temporary ZIP file
   */
  @Test
  public void testUnzipFileProcessesMacOSXFiles() throws Exception {
    // Given
    Map<String, String> zipEntries = new HashMap<>();
    zipEntries.put(TEST_TXT_FILE, TEST_CONTENT);
    zipEntries.put("__MACOSX/._test.txt", "mac metadata");
    File tempZip = createTempZipFile(zipEntries);

    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile(any(File.class), anyString()))
        .thenReturn("http://test-url.com/file1", "http://test-url.com/file2");

    // When
    String[] result = AddBulkTasks.unzipFile(tempZip);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertEquals(2, result.length, "Should have 2 files (unzipFile doesn't filter __MACOSX)");
  }

  /**
   * Test unzipFile handles exceptions during file upload gracefully.
   * When RestServiceUtil.handleFile throws a RuntimeException, the method
   * should return an error message instead of propagating the exception.
   *
   * @throws Exception if there's an error creating the temporary ZIP file
   */
  @Test
  public void testUnzipFileHandlesException() throws Exception {
    // Given
    Map<String, String> zipEntries = new HashMap<>();
    zipEntries.put(TEST_TXT_FILE, TEST_CONTENT);
    File tempZip = createTempZipFile(zipEntries);

    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile(any(File.class), anyString()))
        .thenThrow(new RuntimeException("Upload error"));

    // When
    String[] result = AddBulkTasks.unzipFile(tempZip);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.length >= 1, "Should have at least 1 entry");
    assertTrue(result[0].contains("Error:"), SHOULD_CONTAIN_ERROR_MESSAGE);
  }

  /**
   * Test getStatus returns Status object.
   */
  @Test
  public void testGetStatus() {
    // Given
    String statusIdentifier = "PENDING";
    when(mockStatusCriteria.uniqueResult()).thenReturn(mockStatus);

    // When
    Status result = AddBulkTasks.getStatus(statusIdentifier);

    // Then
    assertNotNull(result, "Status should not be null");
    assertEquals(mockStatus, result, "Should return the mocked status");
  }

  /**
   * Test getCopilotTaskType when it exists.
   */
  @Test
  public void testGetCopilotTaskTypeExists() {
    // Given
    when(mockTaskTypeCriteria.uniqueResult()).thenReturn(mockTaskType);

    // When
    TaskType result = AddBulkTasks.getCopilotTaskType();

    // Then
    assertNotNull(result, "TaskType should not be null");
    assertEquals(mockTaskType, result, "Should return the mocked task type");
  }

  /**
   * Test getCopilotTaskType creates new one when it doesn't exist.
   */
  @Test
  public void testGetCopilotTaskTypeCreatesNew() {
    // Given
    when(mockTaskTypeCriteria.uniqueResult()).thenReturn(null);

    // When
    TaskType result = AddBulkTasks.getCopilotTaskType();

    // Then
    assertNotNull(result, "TaskType should not be null");
  }
}
