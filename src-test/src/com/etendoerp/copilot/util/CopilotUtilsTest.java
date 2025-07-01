package com.etendoerp.copilot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  public void testResetVectorDB_Failure() throws JSONException {
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
  public void testThrowMissingAttachException_AttachedType() {
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
  public void testCheckPromptLength_TooLong() {
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
  public void testGetAuthJson_WebServiceEnabled() throws Exception {
    Role role = mock(Role.class);
    when(role.isWebServiceEnabled()).thenReturn(true);
    OBContext context = mock(OBContext.class);
    try (MockedStatic<CopilotUtils> utils = mockStatic(CopilotUtils.class, CALLS_REAL_METHODS)) {
      utils.when(() -> CopilotUtils.getEtendoSWSToken(context, role)).thenReturn("token");
      JSONObject result = CopilotUtils.getAuthJson(role, context);
      assertEquals("token", result.getString("ETENDO_TOKEN"));
    }
  }

}
