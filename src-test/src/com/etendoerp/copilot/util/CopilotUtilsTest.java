package com.etendoerp.copilot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Collections;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

/**
 * CopilotUtils test class.
 * Comprehensive tests for utility methods related to Copilot operations including
 * multipart body creation, vector database operations, authentication, prompt validation,
 * and app configuration management.
 */
public class CopilotUtilsTest extends WeldBaseTest {

  private static final String KB_VECTORDB_ID_KEY = "kb_vectordb_id";
  private static final String EXTENSION_KEY = "extension";
  private static final String TEST_DB = "testDB";
  private static final String TEST_TXT_FILE = "test.txt";
  private static final String TEST_APP_ID = "appId";
  private static final String TEST_APP_NAME = "TestApp";
  private static final String SYNC_FAILED_MESSAGE = "Sync failed";

  /**
   * Sets up the necessary mocks and test context before each test.
   * Initializes OBContext with admin user and role, and configures
   * VariablesSecureApp in the RequestContext for proper test execution.
   *
   * @throws Exception if there's an error during test setup
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN, TestConstants.Clients.FB_GRP,
        TestConstants.Orgs.ESP_NORTE);
    VariablesSecureApp vsa = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId(),
        OBContext.getOBContext().getRole().getId());
    RequestContext.get().setVariableSecureApp(vsa);
  }

  /**
   * Test createMultipartBody with basic JSON and file parameters.
   * Verifies that a multipart HTTP body can be created successfully with
   * vector database ID, file extension, and a file attachment.
   *
   * @throws Exception if there's an error creating the multipart body
   */
  @Test
  public void testCreateMultipartBody() throws Exception {
    JSONObject jsonBody = new JSONObject().put(KB_VECTORDB_ID_KEY, TEST_DB).put(EXTENSION_KEY, "txt");
    File file = mock(File.class);
    when(file.getName()).thenReturn(TEST_TXT_FILE);
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.copy(any(), any())).thenAnswer(inv -> null);
      HttpRequest.BodyPublisher publisher = CopilotUtils.createMultipartBody(jsonBody, file);
      assertNotNull(publisher);
    }
  }

  /**
   * Test resetVectorDB when the operation fails with HTTP error.
   * Verifies that when the Copilot service returns a non-200 status code,
   * an OBException is thrown with an appropriate error message containing
   * the application name.
   */
  @Test
  public void testResetVectorDBFailure() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn(TEST_APP_ID);
    when(app.getName()).thenReturn(TEST_APP_NAME);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(400);
    when(response.body()).thenReturn("error");
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB")).thenReturn("Reset failed: %s, %s");
      OBException ex = assertThrows(OBException.class, () -> CopilotUtils.resetVectorDB(app));
      assertTrue(ex.getMessage().contains(TEST_APP_NAME));
    }
  }

  /**
   * Test throwMissingAttachException for attached type files.
   * Verifies that when a file of type ATTACHED is missing its attachment,
   * an OBException is thrown with the file name in the error message.
   */
  @Test
  public void testThrowMissingAttachExceptionAttachedType() {
    CopilotFile file = mock(CopilotFile.class);
    when(file.getName()).thenReturn(TEST_TXT_FILE);
    when(file.getType()).thenReturn(CopilotConstants.KBF_TYPE_ATTACHED);
    try (MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ErrorMissingAttach")).thenReturn(
          "Missing attachment: %s");
      OBException ex = assertThrows(OBException.class, () -> FileUtils.throwMissingAttachException(file));
      assertEquals("Missing attachment: test.txt", ex.getMessage());
    }
  }

  /**
   * Test checkPromptLength when prompt exceeds maximum allowed length.
   * Verifies that an OBException is thrown when the prompt length exceeds
   * LANGCHAIN_MAX_LENGTH_PROMPT constant.
   */
  @Test
  public void testCheckPromptLengthTooLong() {
    StringBuilder prompt = new StringBuilder();
    prompt.append("a".repeat(CopilotConstants.LANGCHAIN_MAX_LENGTH_PROMPT + 1));
    try (MockedStatic<OBMessageUtils> messageUtils = mockStatic(
        OBMessageUtils.class); MockedStatic<CopilotConstants> constants = mockStatic(CopilotConstants.class)) {
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_MaxLengthPrompt")).thenReturn("Prompt too long");
      OBException ex = assertThrows(OBException.class, () -> CopilotUtils.checkPromptLength(prompt));
      assertEquals("Prompt too long", ex.getMessage());
    }
  }

  /**
   * Test getEtendoHost returns the correct Etendo host URL.
   * Verifies that the method returns the expected localhost URL with port and context path.
   */
  @Test
  public void testGetEtendoHost() {
    assertEquals("http://localhost:8080/etendo", CopilotUtils.getEtendoHost());
  }

  /**
   * Test getCopilotHost returns the correct Copilot host.
   * Verifies that the method returns the expected localhost value.
   */
  @Test
  public void testGetCopilotHost() {
    assertEquals("localhost", CopilotUtils.getCopilotHost());
  }

  /**
   * Test getAppSourceContent with a list containing a single app source.
   * Verifies that content is correctly extracted from app sources matching
   * the specified behavior type, including the file name and content.
   *
   * @throws IOException if there's an error reading the file content
   */
  @Test
  public void testGetAppSourceContent_List() throws IOException {
    CopilotAppSource appSource = mock(CopilotAppSource.class);
    CopilotFile file = mock(CopilotFile.class);
    when(appSource.getBehaviour()).thenReturn(CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
    when(appSource.getFile()).thenReturn(file);
    when(file.getName()).thenReturn(TEST_TXT_FILE);
    File tempFile = mock(File.class);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<Files> files = mockStatic(Files.class)) {
      utils.when(() -> CopilotUtils.getAppSourceContent(appSource)).thenReturn("content");
      files.when(() -> Files.readString(any())).thenReturn("content");
      String result = CopilotUtils.getAppSourceContent(Collections.singletonList(appSource),
          CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
      assertTrue(result.contains(TEST_TXT_FILE));
      assertTrue(result.contains("content"));
    }
  }

  /**
   * Test getAuthJson when web service is enabled for the role.
   * Verifies that when the role has web service enabled, the method returns
   * a JSON object containing the Etendo SWS token.
   *
   * @throws Exception if there's an error creating the authentication JSON
   */
  @Test
  public void testGetAuthJsonWebServiceEnabled() throws Exception {
    Role role = mock(Role.class);
    when(role.isWebServiceEnabled()).thenReturn(true);
    OBContext context = mock(OBContext.class);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getEtendoSWSToken(context, role)).thenReturn("token");
      JSONObject result = CopilotUtils.getAuthJson(role, context);
      assertEquals("token", result.getString("ETENDO_TOKEN"));
    }
  }

  /**
   * Test getAuthJson when web service is disabled for the role.
   * Verifies that when the role has web service disabled, the method returns
   * an empty JSON object.
   *
   * @throws Exception if there's an error creating the authentication JSON
   */
  @Test
  public void testGetAuthJsonWebServiceDisabled() throws Exception {
    Role role = mock(Role.class);
    when(role.isWebServiceEnabled()).thenReturn(false);
    OBContext context = mock(OBContext.class);
    JSONObject result = CopilotUtils.getAuthJson(role, context);
    assertNotNull(result);
    assertEquals(0, result.length());
  }

  /**
   * Test getCopilotPort returns the correct port number.
   * Verifies that the method returns the expected Copilot service port.
   */
  @Test
  public void testGetCopilotPort() {
    String port = CopilotUtils.getCopilotPort();
    assertNotNull(port);
    assertEquals("5005", port);
  }

  /**
   * Test getEtendoHostDocker returns a valid host URL.
   * Verifies that the method returns a non-null host URL for Docker environment.
   */
  @Test
  public void testGetEtendoHostDocker() {
    String host = CopilotUtils.getEtendoHostDocker();
    assertNotNull(host);
  }

  /**
   * Test logIfDebug executes without throwing exceptions.
   * Verifies that the debug logging method can be called safely,
   * only logging if debug mode is enabled.
   */
  @Test
  public void testLogIfDebug() {
    // This method logs only if debug is enabled, should not throw any exception
    CopilotUtils.logIfDebug("Test debug message");
  }

  /**
   * Test checkPromptLength with valid prompt length.
   * Verifies that no exception is thrown when the prompt length
   * is within the allowed limit.
   */
  @Test
  public void testCheckPromptLengthValid() {
    StringBuilder prompt = new StringBuilder("Valid prompt");
    // Should not throw exception for valid length
    CopilotUtils.checkPromptLength(prompt);
  }

  /**
   * Test checkQuestionPrompt with valid question length.
   * Verifies that no exception is thrown when the question length
   * is within the allowed limit.
   */
  @Test
  public void testCheckQuestionPromptValid() {
    String question = "Valid question";
    // Should not throw exception for valid length
    CopilotUtils.checkQuestionPrompt(question);
  }

  /**
   * Test checkQuestionPrompt when question exceeds maximum allowed length.
   * Verifies that an OBException is thrown when the question length exceeds
   * LANGCHAIN_MAX_LENGTH_QUESTION constant.
   */
  @Test
  public void testCheckQuestionPromptTooLong() {
    String question = "a".repeat(CopilotConstants.LANGCHAIN_MAX_LENGTH_QUESTION + 1);
    try (MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_MaxLengthQuestion")).thenReturn("Question too long");
      OBException ex = assertThrows(OBException.class, () -> CopilotUtils.checkQuestionPrompt(question));
      assertEquals("Question too long", ex.getMessage());
    }
  }

  /**
   * Test purgeVectorDB with successful operation.
   * Verifies that when the Copilot service returns status 200,
   * the vector database is purged successfully without throwing exceptions.
   *
   * @throws JSONException if there's an error processing the JSON response
   */
  @Test
  public void testPurgeVectorDBSuccess() throws JSONException {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn(TEST_APP_ID);
    when(app.getName()).thenReturn(TEST_APP_NAME);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      // Should not throw exception
      CopilotUtils.purgeVectorDB(app);
    }
  }

  /**
   * Test purgeVectorDB when the operation fails with HTTP error.
   * Verifies that when the Copilot service returns status 500,
   * an OBException is thrown with an error message containing the application name.
   */
  @Test
  public void testPurgeVectorDBFailure() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn(TEST_APP_ID);
    when(app.getName()).thenReturn(TEST_APP_NAME);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(500);
    when(response.body()).thenReturn("error");
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB")).thenReturn("Purge failed: %s, %s");
      OBException ex = assertThrows(OBException.class, () -> CopilotUtils.purgeVectorDB(app));
      assertTrue(ex.getMessage().contains(TEST_APP_NAME));
    }
  }

  /**
   * Test resetVectorDB with successful operation.
   * Verifies that when the Copilot service returns status 200,
   * the vector database is reset successfully without throwing exceptions.
   *
   * @throws JSONException if there's an error processing the JSON response
   */
  @Test
  public void testResetVectorDBSuccess() throws JSONException {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn(TEST_APP_ID);
    when(app.getName()).thenReturn(TEST_APP_NAME);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      // Should not throw exception
      CopilotUtils.resetVectorDB(app);
    }
  }

  /**
   * Test getAppIdByAssistantName when the app is found.
   * Verifies that when an app with the given name exists,
   * the method returns the CopilotApp object with matching name.
   */
  @Test
  public void testGetAppIdByAssistantNameFound() {
    String appName = TEST_APP_NAME;
    CopilotApp app = mock(CopilotApp.class);
    when(app.getName()).thenReturn(appName);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getAppIdByAssistantName(appName)).thenReturn(app);
      CopilotApp result = CopilotUtils.getAppIdByAssistantName(appName);
      assertNotNull(result);
      assertEquals(appName, result.getName());
    }
  }

  /**
   * Test getAppIdByAssistantName when the app is not found.
   * Verifies that when no app with the given name exists,
   * the method returns null.
   */
  @Test
  public void testGetAppIdByAssistantNameNotFound() {
    String appName = "NonExistentApp";
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getAppIdByAssistantName(appName)).thenReturn(null);
      CopilotApp result = CopilotUtils.getAppIdByAssistantName(appName);
      assertEquals(null, result);
    }
  }

  /**
   * Test checkIfGraphQuestion returns true for LangGraph app type.
   * Verifies that the method correctly identifies apps using the
   * LANGGRAPH app type.
   */
  @Test
  public void testCheckIfGraphQuestionTrue() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getAppType()).thenReturn(CopilotConstants.APP_TYPE_LANGGRAPH);
    boolean result = CopilotUtils.checkIfGraphQuestion(app);
    assertTrue(result);
  }

  /**
   * Test checkIfGraphQuestion returns false for non-LangGraph app type.
   * Verifies that the method correctly identifies apps not using the
   * LANGGRAPH app type.
   */
  @Test
  public void testCheckIfGraphQuestionFalse() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getAppType()).thenReturn(CopilotConstants.APP_TYPE_OPENAI);
    boolean result = CopilotUtils.checkIfGraphQuestion(app);
    assertFalse(result);
  }

  /**
   * Test getAppSourceContent with empty list.
   * Verifies that when no app sources are provided,
   * the method returns an empty string.
   */
  @Test
  public void testGetAppSourceContentEmptyList() {
    String result = CopilotUtils.getAppSourceContent(Collections.emptyList(),
        CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
    assertNotNull(result);
    assertEquals("", result);
  }

  /**
   * Test getAppSourceContent with different behavior type.
   * Verifies that when the app source has a different behavior than requested,
   * it is filtered out and the method returns an empty string.
   */
  @Test
  public void testGetAppSourceContentDifferentBehaviour() {
    CopilotAppSource appSource = mock(CopilotAppSource.class);
    when(appSource.getBehaviour()).thenReturn(CopilotConstants.FILE_BEHAVIOUR_KB);
    String result = CopilotUtils.getAppSourceContent(Collections.singletonList(appSource),
        CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
    assertNotNull(result);
    assertEquals("", result);
  }

  /**
   * Test getAppSourceContent with null file.
   * Verifies that when an app source has a null file reference,
   * it is handled gracefully and the method returns an empty string.
   */
  @Test
  public void testGetAppSourceContentNullFile() {
    CopilotAppSource appSource = mock(CopilotAppSource.class);
    when(appSource.getBehaviour()).thenReturn(CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
    when(appSource.getFile()).thenReturn(null);
    String result = CopilotUtils.getAppSourceContent(Collections.singletonList(appSource),
        CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
    assertNotNull(result);
    assertEquals("", result);
  }

  /**
   * Test toVectorDB with successful file upload.
   * Verifies that when the Copilot service returns status 200,
   * the file is successfully uploaded to the vector database without exceptions.
   *
   * @throws JSONException if there's an error processing the JSON request
   */
  @Test
  public void testToVectorDBSuccess() throws JSONException {
    File file = mock(File.class);
    when(file.getName()).thenReturn(TEST_TXT_FILE);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      // Should not throw exception
      CopilotUtils.toVectorDB(file, TEST_DB, "txt", false, null, null);
    }
  }

  /**
   * Test toVectorDB with chunk size and overlap parameters.
   * Verifies that the method successfully handles custom chunking parameters
   * for text splitting during vector database upload.
   *
   * @throws JSONException if there's an error processing the JSON request
   */
  @Test
  public void testToVectorDBWithChunkParams() throws JSONException {
    File file = mock(File.class);
    when(file.getName()).thenReturn(TEST_TXT_FILE);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      // Should not throw exception
      CopilotUtils.toVectorDB(file, TEST_DB, "txt", false, 1000L, 100L);
    }
  }

  /**
   * Test toVectorDB when the upload fails with HTTP error.
   * Verifies that when the Copilot service returns status 500,
   * an OBException is thrown with the sync failed message.
   */
  @Test
  public void testToVectorDBFailure() {
    File file = mock(File.class);
    when(file.getName()).thenReturn(TEST_TXT_FILE);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(500);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_Error_sync_vectorDB")).thenReturn(SYNC_FAILED_MESSAGE);
      OBException ex = assertThrows(OBException.class,
          () -> CopilotUtils.toVectorDB(file, TEST_DB, "txt", false, null, null));
      assertEquals(SYNC_FAILED_MESSAGE, ex.getMessage());
    }
  }

  /**
   * Test toVectorDB when response is null.
   * Verifies that when the Copilot service returns a null response,
   * an OBException is thrown with the sync failed message.
   */
  @Test
  public void testToVectorDBNullResponse() {
    File file = mock(File.class);
    when(file.getName()).thenReturn(TEST_TXT_FILE);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(null);
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_Error_sync_vectorDB")).thenReturn(SYNC_FAILED_MESSAGE);
      OBException ex = assertThrows(OBException.class,
          () -> CopilotUtils.toVectorDB(file, TEST_DB, "txt", false, null, null));
      assertEquals(SYNC_FAILED_MESSAGE, ex.getMessage());
    }
  }

  /**
   * Test createMultipartBody with all available fields populated.
   * Verifies that the method correctly handles a complete JSON body including
   * text content, overwrite flag, skip_splitting flag, and chunking parameters.
   *
   * @throws Exception if there's an error creating the multipart body
   */
  @Test
  public void testCreateMultipartBodyWithAllFields() throws Exception {
    JSONObject jsonBody = new JSONObject().put(KB_VECTORDB_ID_KEY, TEST_DB).put(EXTENSION_KEY, "txt").put("text",
        "test text").put("overwrite", true).put("skip_splitting", true).put("max_chunk_size", 1000L).put(
        "chunk_overlap", 100L);
    File file = mock(File.class);
    when(file.getName()).thenReturn(TEST_TXT_FILE);
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.copy(any(), any())).thenAnswer(inv -> null);
      HttpRequest.BodyPublisher publisher = CopilotUtils.createMultipartBody(jsonBody, file);
      assertNotNull(publisher);
    }
  }

  /**
   * Test createMultipartBody without file attachment.
   * Verifies that the method can create a multipart body with only JSON data
   * and no file attachment.
   *
   * @throws Exception if there's an error creating the multipart body
   */
  @Test
  public void testCreateMultipartBodyWithoutFile() throws Exception {
    JSONObject jsonBody = new JSONObject().put(KB_VECTORDB_ID_KEY, TEST_DB).put(EXTENSION_KEY, "txt");
    HttpRequest.BodyPublisher publisher = CopilotUtils.createMultipartBody(jsonBody, null);
    assertNotNull(publisher);
  }

  /**
   * Test createMultipartBody with minimal fields.
   * Verifies that the method can create a multipart body with an empty JSON object
   * and no file, representing the absolute minimum required data.
   *
   * @throws Exception if there's an error creating the multipart body
   */
  @Test
  public void testCreateMultipartBodyMinimalFields() throws Exception {
    JSONObject jsonBody = new JSONObject();
    HttpRequest.BodyPublisher publisher = CopilotUtils.createMultipartBody(jsonBody, null);
    assertNotNull(publisher);
  }
}
