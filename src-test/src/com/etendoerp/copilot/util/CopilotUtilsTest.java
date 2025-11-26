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

public class CopilotUtilsTest extends WeldBaseTest {


  /**
   * Sets up the necessary mocks before each test.
   */

  /**
   * Sets up the necessary mocks and spies before each test.
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


  @Test
  public void testCreateMultipartBody() throws Exception {
    JSONObject jsonBody = new JSONObject().put("kb_vectordb_id", "testDB").put("extension", "txt");
    File file = mock(File.class);
    when(file.getName()).thenReturn("test.txt");
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.copy(any(), any())).thenAnswer(inv -> null);
      HttpRequest.BodyPublisher publisher = CopilotUtils.createMultipartBody(jsonBody, file);
      assertNotNull(publisher);
    }
  }

  @Test
  public void testResetVectorDBFailure() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("appId");
    when(app.getName()).thenReturn("TestApp");
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(400);
    when(response.body()).thenReturn("error");
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB")).thenReturn("Reset failed: %s, %s");
      OBException ex = assertThrows(OBException.class, () -> CopilotUtils.resetVectorDB(app));
      assertTrue(ex.getMessage().contains("TestApp"));
    }
  }


  @Test
  public void testThrowMissingAttachExceptionAttachedType() {
    CopilotFile file = mock(CopilotFile.class);
    when(file.getName()).thenReturn("test.txt");
    when(file.getType()).thenReturn(CopilotConstants.KBF_TYPE_ATTACHED);
    try (MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ErrorMissingAttach")).thenReturn(
          "Missing attachment: %s");
      OBException ex = assertThrows(OBException.class, () -> FileUtils.throwMissingAttachException(file));
      assertEquals("Missing attachment: test.txt", ex.getMessage());
    }
  }

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

  @Test
  public void testGetEtendoHost() {
    assertEquals("http://localhost:8080/etendo", CopilotUtils.getEtendoHost());
  }

  @Test
  public void testGetCopilotHost() {
    assertEquals("localhost", CopilotUtils.getCopilotHost());
  }

  @Test
  public void testGetAppSourceContent_List() throws IOException {
    CopilotAppSource appSource = mock(CopilotAppSource.class);
    CopilotFile file = mock(CopilotFile.class);
    when(appSource.getBehaviour()).thenReturn(CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
    when(appSource.getFile()).thenReturn(file);
    when(file.getName()).thenReturn("test.txt");
    File tempFile = mock(File.class);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<Files> files = mockStatic(Files.class)) {
      utils.when(() -> CopilotUtils.getAppSourceContent(appSource)).thenReturn("content");
      files.when(() -> Files.readString(any())).thenReturn("content");
      String result = CopilotUtils.getAppSourceContent(Collections.singletonList(appSource),
          CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
      assertTrue(result.contains("test.txt"));
      assertTrue(result.contains("content"));
    }
  }

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

  @Test
  public void testGetAuthJsonWebServiceDisabled() throws Exception {
    Role role = mock(Role.class);
    when(role.isWebServiceEnabled()).thenReturn(false);
    OBContext context = mock(OBContext.class);
    JSONObject result = CopilotUtils.getAuthJson(role, context);
    assertNotNull(result);
    assertEquals(0, result.length());
  }

  @Test
  public void testGetCopilotPort() {
    String port = CopilotUtils.getCopilotPort();
    assertNotNull(port);
    assertEquals("5005", port);
  }

  @Test
  public void testGetEtendoHostDocker() {
    String host = CopilotUtils.getEtendoHostDocker();
    assertNotNull(host);
  }

  @Test
  public void testLogIfDebug() {
    // This method logs only if debug is enabled, should not throw any exception
    CopilotUtils.logIfDebug("Test debug message");
  }

  @Test
  public void testCheckPromptLengthValid() {
    StringBuilder prompt = new StringBuilder("Valid prompt");
    // Should not throw exception for valid length
    CopilotUtils.checkPromptLength(prompt);
  }

  @Test
  public void testCheckQuestionPromptValid() {
    String question = "Valid question";
    // Should not throw exception for valid length
    CopilotUtils.checkQuestionPrompt(question);
  }

  @Test
  public void testCheckQuestionPromptTooLong() {
    String question = "a".repeat(CopilotConstants.LANGCHAIN_MAX_LENGTH_QUESTION + 1);
    try (MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_MaxLengthQuestion")).thenReturn("Question too long");
      OBException ex = assertThrows(OBException.class, () -> CopilotUtils.checkQuestionPrompt(question));
      assertEquals("Question too long", ex.getMessage());
    }
  }

  @Test
  public void testPurgeVectorDBSuccess() throws JSONException {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("appId");
    when(app.getName()).thenReturn("TestApp");
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      // Should not throw exception
      CopilotUtils.purgeVectorDB(app);
    }
  }

  @Test
  public void testPurgeVectorDBFailure() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("appId");
    when(app.getName()).thenReturn("TestApp");
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(500);
    when(response.body()).thenReturn("error");
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB")).thenReturn("Purge failed: %s, %s");
      OBException ex = assertThrows(OBException.class, () -> CopilotUtils.purgeVectorDB(app));
      assertTrue(ex.getMessage().contains("TestApp"));
    }
  }

  @Test
  public void testResetVectorDBSuccess() throws JSONException {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("appId");
    when(app.getName()).thenReturn("TestApp");
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      // Should not throw exception
      CopilotUtils.resetVectorDB(app);
    }
  }

  @Test
  public void testGetAppIdByAssistantNameFound() {
    String appName = "TestApp";
    CopilotApp app = mock(CopilotApp.class);
    when(app.getName()).thenReturn(appName);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getAppIdByAssistantName(appName)).thenReturn(app);
      CopilotApp result = CopilotUtils.getAppIdByAssistantName(appName);
      assertNotNull(result);
      assertEquals(appName, result.getName());
    }
  }

  @Test
  public void testGetAppIdByAssistantNameNotFound() {
    String appName = "NonExistentApp";
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getAppIdByAssistantName(appName)).thenReturn(null);
      CopilotApp result = CopilotUtils.getAppIdByAssistantName(appName);
      assertEquals(null, result);
    }
  }

  @Test
  public void testCheckIfGraphQuestionTrue() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getAppType()).thenReturn(CopilotConstants.APP_TYPE_LANGGRAPH);
    boolean result = CopilotUtils.checkIfGraphQuestion(app);
    assertTrue(result);
  }

  @Test
  public void testCheckIfGraphQuestionFalse() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getAppType()).thenReturn(CopilotConstants.APP_TYPE_OPENAI);
    boolean result = CopilotUtils.checkIfGraphQuestion(app);
    assertFalse(result);
  }

  @Test
  public void testGetAppSourceContentEmptyList() {
    String result = CopilotUtils.getAppSourceContent(Collections.emptyList(),
        CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
    assertNotNull(result);
    assertEquals("", result);
  }

  @Test
  public void testGetAppSourceContentDifferentBehaviour() {
    CopilotAppSource appSource = mock(CopilotAppSource.class);
    when(appSource.getBehaviour()).thenReturn(CopilotConstants.FILE_BEHAVIOUR_KB);
    String result = CopilotUtils.getAppSourceContent(Collections.singletonList(appSource),
        CopilotConstants.FILE_BEHAVIOUR_SYSTEM);
    assertNotNull(result);
    assertEquals("", result);
  }

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

  @Test
  public void testToVectorDBSuccess() throws JSONException {
    File file = mock(File.class);
    when(file.getName()).thenReturn("test.txt");
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      // Should not throw exception
      CopilotUtils.toVectorDB(file, "testDB", "txt", false, null, null);
    }
  }

  @Test
  public void testToVectorDBWithChunkParams() throws JSONException {
    File file = mock(File.class);
    when(file.getName()).thenReturn("test.txt");
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      // Should not throw exception
      CopilotUtils.toVectorDB(file, "testDB", "txt", false, 1000L, 100L);
    }
  }

  @Test
  public void testToVectorDBFailure() {
    File file = mock(File.class);
    when(file.getName()).thenReturn("test.txt");
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(500);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(response);
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_Error_sync_vectorDB")).thenReturn("Sync failed");
      OBException ex = assertThrows(OBException.class,
          () -> CopilotUtils.toVectorDB(file, "testDB", "txt", false, null, null));
      assertEquals("Sync failed", ex.getMessage());
    }
  }

  @Test
  public void testToVectorDBNullResponse() {
    File file = mock(File.class);
    when(file.getName()).thenReturn("test.txt");
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class,
        CALLS_REAL_METHODS); MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      utils.when(() -> CopilotUtils.getResponseFromCopilot(any(), anyString(), any(), any())).thenReturn(null);
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_Error_sync_vectorDB")).thenReturn("Sync failed");
      OBException ex = assertThrows(OBException.class,
          () -> CopilotUtils.toVectorDB(file, "testDB", "txt", false, null, null));
      assertEquals("Sync failed", ex.getMessage());
    }
  }

  @Test
  public void testCreateMultipartBodyWithAllFields() throws Exception {
    JSONObject jsonBody = new JSONObject().put("kb_vectordb_id", "testDB").put("extension", "txt").put("text",
        "test text").put("overwrite", true).put("skip_splitting", true).put("max_chunk_size", 1000L).put(
        "chunk_overlap", 100L);
    File file = mock(File.class);
    when(file.getName()).thenReturn("test.txt");
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.copy(any(), any())).thenAnswer(inv -> null);
      HttpRequest.BodyPublisher publisher = CopilotUtils.createMultipartBody(jsonBody, file);
      assertNotNull(publisher);
    }
  }

  @Test
  public void testCreateMultipartBodyWithoutFile() throws Exception {
    JSONObject jsonBody = new JSONObject().put("kb_vectordb_id", "testDB").put("extension", "txt");
    HttpRequest.BodyPublisher publisher = CopilotUtils.createMultipartBody(jsonBody, null);
    assertNotNull(publisher);
  }

  @Test
  public void testCreateMultipartBodyMinimalFields() throws Exception {
    JSONObject jsonBody = new JSONObject();
    HttpRequest.BodyPublisher publisher = CopilotUtils.createMultipartBody(jsonBody, null);
    assertNotNull(publisher);
  }

}
