package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

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

  @Before
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

  @After
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
   * Test doExecute with CSV file successfully creates tasks.
   */
  @Test
  public void testDoExecuteWithCsvFileSuccess() throws Exception {
    // Given
    String csvContent = "header1,header2\nvalue1,value2\nvalue3,value4";
    ByteArrayInputStream fileInputStream = new ByteArrayInputStream(csvContent.getBytes());

    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put("fileName", "test.csv");
    fileMap.put("content", fileInputStream);

    String paramValues = String.format(
        "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"%s\",\"separator\":\",\"}}",
        TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("paramValues", paramValues);
    parameters.put("file", fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have responseActions", result.has("responseActions"));
  }

  /**
   * Test doExecute with null separator defaults to comma.
   */
  @Test
  public void testDoExecuteWithNullSeparatorDefaultsToComma() throws Exception {
    // Given
    String csvContent = "header1,header2\nvalue1,value2";
    ByteArrayInputStream fileInputStream = new ByteArrayInputStream(csvContent.getBytes());

    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put("fileName", "test.csv");
    fileMap.put("content", fileInputStream);

    String paramValues = String.format(
        "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"%s\",\"separator\":\"null\"}}",
        TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("paramValues", paramValues);
    parameters.put("file", fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
  }

  /**
   * Test doExecute with ZIP file successfully creates tasks.
   */
  @Test
  public void testDoExecuteWithZipFileSuccess() throws Exception {
    // Given
    File tempZip = File.createTempFile("test", ".zip");
    tempZip.deleteOnExit();

    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip.toPath()))) {
      ZipEntry entry = new ZipEntry("test.txt");
      zos.putNextEntry(entry);
      zos.write("test content".getBytes());
      zos.closeEntry();
    }

    byte[] zipBytes = Files.readAllBytes(tempZip.toPath());
    ByteArrayInputStream fileInputStream = new ByteArrayInputStream(zipBytes);

    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put("fileName", "test.zip");
    fileMap.put("content", fileInputStream);

    String paramValues = String.format(
        "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"%s\"}}",
        TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("paramValues", paramValues);
    parameters.put("file", fileMap);

    // Mock RestServiceUtil.handleFile
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile(any(File.class), anyString()))
        .thenReturn("http://test-url.com/file");

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have responseActions", result.has("responseActions"));
  }

  /**
   * Test doExecute with unsupported file type returns error.
   */
  @Test
  public void testDoExecuteWithUnsupportedFileType() throws Exception {
    // Given
    String content = "test content";
    ByteArrayInputStream fileInputStream = new ByteArrayInputStream(content.getBytes());

    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put("fileName", "test.txt");
    fileMap.put("content", fileInputStream);

    String paramValues = String.format(
        "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"%s\"}}",
        TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("paramValues", paramValues);
    parameters.put("file", fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should contain error message", result.has("message"));
    assertEquals("error", result.getString("severity"));
  }

  /**
   * Test doExecute with missing file content returns error.
   */
  @Test
  public void testDoExecuteWithMissingFileContent() throws Exception {
    // Given
    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put("fileName", "test.csv");
    // No content

    String paramValues = String.format(
        "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"%s\"}}",
        TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("paramValues", paramValues);
    parameters.put("file", fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should contain error message", result.has("message"));
  }

  /**
   * Test doExecute with no file parameter returns error.
   */
  @Test
  public void testDoExecuteWithNoFileParameter() throws Exception {
    // Given
    String paramValues = String.format(
        "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"%s\"}}",
        TEST_AGENT_ID, TEST_QUESTION, TEST_GROUP);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("paramValues", paramValues);
    // No file parameter

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should contain error message", result.has("message"));
  }

  /**
   * Test doExecute uses filename as group when group param is empty.
   */
  @Test
  public void testDoExecuteUsesFilenameAsGroup() throws Exception {
    // Given
    String csvContent = "header1,header2\nvalue1,value2";
    ByteArrayInputStream fileInputStream = new ByteArrayInputStream(csvContent.getBytes());

    Map<String, Object> fileMap = new HashMap<>();
    fileMap.put("fileName", "myfile.csv");
    fileMap.put("content", fileInputStream);

    String paramValues = String.format(
        "{\"_params\":{\"agentid\":\"%s\",\"question\":\"%s\",\"group\":\"\"}}",
        TEST_AGENT_ID, TEST_QUESTION);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("paramValues", paramValues);
    parameters.put("file", fileMap);

    // When
    JSONObject result = addBulkTasks.doExecute(parameters, "");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
  }

  /**
   * Test readCsvFile with valid CSV.
   */
  @Test
  public void testReadCsvFileValid() throws Exception {
    // Given
    File tempFile = File.createTempFile("test", ".csv");
    tempFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write("name,age\n");
      writer.write("John,25\n");
      writer.write("Jane,30\n");
    }

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ",");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertEquals("Should have 2 records", 2, result.length);
    assertTrue("First record should contain name", result[0].contains("John"));
    assertTrue("Second record should contain name", result[1].contains("Jane"));
  }

  /**
   * Test readCsvFile with empty file.
   */
  @Test
  public void testReadCsvFileEmpty() throws Exception {
    // Given
    File tempFile = File.createTempFile("test", ".csv");
    tempFile.deleteOnExit();

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ",");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertEquals("Should have 0 records", 0, result.length);
  }

  /**
   * Test readCsvFile with custom separator.
   */
  @Test
  public void testReadCsvFileWithCustomSeparator() throws Exception {
    // Given
    File tempFile = File.createTempFile("test", ".csv");
    tempFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write("name;age\n");
      writer.write("John;25\n");
    }

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ";");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertEquals("Should have 1 record", 1, result.length);
    assertTrue("Record should contain name", result[0].contains("John"));
  }

  /**
   * Test readCsvFile with missing values.
   */
  @Test
  public void testReadCsvFileWithMissingValues() throws Exception {
    // Given
    File tempFile = File.createTempFile("test", ".csv");
    tempFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write("name,age,city\n");
      writer.write("John,25\n");
    }

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ",");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertEquals("Should have 1 record", 1, result.length);
    assertTrue("Record should contain name", result[0].contains("John"));
  }

  /**
   * Test readCsvFile converts to JSON correctly.
   */
  @Test
  public void testReadCsvFileJsonFormat() throws Exception {
    // Given
    File tempFile = File.createTempFile("test", ".csv");
    tempFile.deleteOnExit();

    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write("name,age\n");
      writer.write("Alice,28\n");
    }

    // When
    String[] result = AddBulkTasks.readCsvFile(tempFile, ",");

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertEquals("Should have 1 record", 1, result.length);
    assertTrue("Should be JSON format", result[0].contains("{"));
    assertTrue("Should contain name field", result[0].contains("\"name\""));
  }

  /**
   * Test unzipFile with valid ZIP.
   */
  @Test
  public void testUnzipFileValid() throws Exception {
    // Given
    File tempZip = File.createTempFile("test", ".zip");
    tempZip.deleteOnExit();

    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip.toPath()))) {
      ZipEntry entry = new ZipEntry("test.txt");
      zos.putNextEntry(entry);
      zos.write("test content".getBytes());
      zos.closeEntry();
    }

    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile(any(File.class), anyString()))
        .thenReturn("http://test-url.com/file");

    // When
    String[] result = AddBulkTasks.unzipFile(tempZip);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have at least 1 file", result.length >= 1);
    assertEquals("Should return mocked URL", "http://test-url.com/file", result[0]);
  }

  /**
   * Test unzipFile processes both normal and __MACOSX files.
   * Note: __MACOSX filtering happens in doExecute, not in unzipFile.
   */
  @Test
  public void testUnzipFileProcessesMacOSXFiles() throws Exception {
    // Given
    File tempZip = File.createTempFile("test", ".zip");
    tempZip.deleteOnExit();

    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip.toPath()))) {
      ZipEntry normalEntry = new ZipEntry("test.txt");
      zos.putNextEntry(normalEntry);
      zos.write("test content".getBytes());
      zos.closeEntry();

      ZipEntry macEntry = new ZipEntry("__MACOSX/._test.txt");
      zos.putNextEntry(macEntry);
      zos.write("mac metadata".getBytes());
      zos.closeEntry();
    }

    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile(any(File.class), anyString()))
        .thenReturn("http://test-url.com/file1", "http://test-url.com/file2");

    // When
    String[] result = AddBulkTasks.unzipFile(tempZip);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertEquals("Should have 2 files (unzipFile doesn't filter __MACOSX)", 2, result.length);
  }

  /**
   * Test unzipFile handles exceptions.
   */
  @Test
  public void testUnzipFileHandlesException() throws Exception {
    // Given
    File tempZip = File.createTempFile("test", ".zip");
    tempZip.deleteOnExit();

    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip.toPath()))) {
      ZipEntry entry = new ZipEntry("test.txt");
      zos.putNextEntry(entry);
      zos.write("test content".getBytes());
      zos.closeEntry();
    }

    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile(any(File.class), anyString()))
        .thenThrow(new RuntimeException("Upload error"));

    // When
    String[] result = AddBulkTasks.unzipFile(tempZip);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have at least 1 entry", result.length >= 1);
    assertTrue("Should contain error message", result[0].contains("Error:"));
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
    assertNotNull("Status should not be null", result);
    assertEquals("Should return the mocked status", mockStatus, result);
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
    assertNotNull("TaskType should not be null", result);
    assertEquals("Should return the mocked task type", mockTaskType, result);
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
    assertNotNull("TaskType should not be null", result);
  }
}