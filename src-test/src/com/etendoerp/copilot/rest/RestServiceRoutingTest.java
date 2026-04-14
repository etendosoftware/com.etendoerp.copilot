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
package com.etendoerp.copilot.rest;

import static com.etendoerp.copilot.rest.RestService.CACHED_QUESTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.ConversationUtils;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Tests for REST endpoint routing in {@link RestService#doGet} and {@link RestService#doPost}.
 */
public class RestServiceRoutingTest extends WeldBaseTest {

  private static final String TEST_ERROR_MESSAGE = "Test error";
  private static final String QUESTION_PATH = "/question";
  private static final String ASYNC_QUESTION_PATH = "/aquestion";
  private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";
  private static final String TEST_QUESTION = "What is the weather?";
  private static final String TEST_APP_ID = "testAppId123";

  private final List<AutoCloseable> closeables = new ArrayList<>();
  private RestService restService;
  private MockedStatic<RestServiceUtil> mockedRestServiceUtil;
  private MockedStatic<RequestUtils> mockedRequestUtils;
  private MockedStatic<ConversationUtils> mockedConversationUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  @Mock private HttpServletRequest mockRequest;
  @Mock private HttpServletResponse mockResponse;
  @Mock private HttpSession mockSession;
  @Mock private PrintWriter mockWriter;

  /**
   * Sets up the necessary mocks and spies before each test.
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    closeables.add(MockitoAnnotations.openMocks(this));
    restService = spy(new RestService());

    mockedRestServiceUtil = mockStatic(RestServiceUtil.class);
    mockedRequestUtils = mockStatic(RequestUtils.class);
    mockedConversationUtils = mockStatic(ConversationUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    MockedStatic<OBMessageUtils> mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    closeables.addAll(List.of(mockedRestServiceUtil, mockedRequestUtils,
        mockedConversationUtils, mockedCopilotUtils, mockedOBMessageUtils));

    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN,
        TestConstants.Clients.FB_GRP, TestConstants.Orgs.ESP_NORTE);
    RequestContext.get().setVariableSecureApp(new VariablesSecureApp(
        OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(),
        OBContext.getOBContext().getCurrentOrganization().getId(),
        OBContext.getOBContext().getRole().getId()));

    when(mockResponse.getWriter()).thenReturn(mockWriter);
    doNothing().when(mockWriter).write(anyString());
    when(mockRequest.getSession()).thenReturn(mockSession);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(anyString()))
        .thenReturn("Error message");
  }

  /**
   * Tears down the test environment after each test.
   */
  @After
  public void tearDown() throws Exception {
    for (AutoCloseable c : closeables) {
      c.close();
    }
    closeables.clear();
  }

  // ============ doGet Routing Tests ============

  /**
   * Test doGet with /conversations path.
   */
  @Test
  public void testDoGetWithConversations() throws Exception {
    verifyGetRoutesToConversationHandler("/conversations",
        () -> ConversationUtils.handleConversations(mockRequest, mockResponse));
  }

  /**
   * Test doGet with /conversationMessages path.
   */
  @Test
  public void testDoGetWithConversationMessages() throws Exception {
    verifyGetRoutesToConversationHandler("/conversationMessages",
        () -> ConversationUtils.handleConversationMessages(mockRequest, mockResponse));
  }

  /**
   * Test doGet with /archivedConversations path.
   */
  @Test
  public void testDoGetWithArchivedConversations() throws Exception {
    verifyGetRoutesToConversationHandler("/archivedConversations",
        () -> ConversationUtils.handleArchivedConversations(mockRequest, mockResponse));
  }

  private void verifyGetRoutesToConversationHandler(String path,
      MockedStatic.Verification handler) throws Exception {
    when(mockRequest.getPathInfo()).thenReturn(path);
    mockedConversationUtils.when(handler).thenAnswer(invocation -> null);

    restService.doGet(mockRequest, mockResponse);

    mockedConversationUtils.verify(handler, times(1));
  }

  /**
   * Test doGet with /aquestion path (async question).
   */
  @Test
  public void testDoGetWithAsyncQuestion() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn(ASYNC_QUESTION_PATH);
    doNothing().when(restService).handleQuestion(mockRequest, mockResponse);

    restService.doGet(mockRequest, mockResponse);

    verify(restService, times(1)).handleQuestion(mockRequest, mockResponse);
  }

  /**
   * Test doGet with /labels path.
   */
  @Test
  public void testDoGetWithLabels() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn("/labels");
    JSONObject labels = new JSONObject().put("labels", "data");
    mockedRestServiceUtil.when(RestServiceUtil::getJSONLabels).thenReturn(labels);

    restService.doGet(mockRequest, mockResponse);

    verify(mockResponse, times(1)).setContentType(JSON_CONTENT_TYPE);
    verify(mockWriter, times(1)).write(labels.toString());
  }

  /**
   * Test doGet with /structure path.
   */
  @Test
  public void testDoGetWithStructure() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn("/structure");
    JSONObject params = new JSONObject().put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest)).thenReturn(params);

    CopilotApp mockApp = mock(CopilotApp.class);
    mockedCopilotUtils.when(() -> CopilotUtils.getAssistantByIDOrName(TEST_APP_ID)).thenReturn(mockApp);
    mockedRestServiceUtil.when(() -> RestServiceUtil.generateAssistantStructure(eq(mockApp), any(JSONObject.class)))
        .thenAnswer(invocation -> {
          JSONObject result = invocation.getArgument(1);
          result.put("structure", "data");
          return null;
        });

    restService.doGet(mockRequest, mockResponse);

    verify(mockResponse, times(1)).setContentType(JSON_CONTENT_TYPE);
    verify(mockWriter, times(1)).write(anyString());
  }

  /**
   * Test doGet with invalid path.
   */
  @Test
  public void testDoGetWithInvalidPath() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn("/invalidPath");

    restService.doGet(mockRequest, mockResponse);

    verify(mockResponse, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  // ============ doPost Routing Tests ============

  /**
   * Test doPost with /question path.
   */
  @Test
  public void testDoPostWithQuestion() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn(QUESTION_PATH);
    doNothing().when(restService).handleQuestion(mockRequest, mockResponse);

    restService.doPost(mockRequest, mockResponse);

    verify(restService, times(1)).handleQuestion(mockRequest, mockResponse);
  }

  /**
   * Test doPost with /file path.
   */
  @Test
  public void testDoPostWithFile() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn("/file");

    JSONObject fileResponse = new JSONObject().put("file", "uploaded");

    mockedRestServiceUtil.when(() -> RestServiceUtil.handleFile((List<FileItem>) any(), eq("attachFile")))
        .thenReturn(fileResponse);

    when(mockRequest.getContentType()).thenReturn("multipart/form-data");
  }

  /**
   * Test doPost with /cacheQuestion path.
   */
  @Test
  public void testDoPostWithCacheQuestion() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn("/cacheQuestion");
    when(mockRequest.getMethod()).thenReturn("POST");
    String questionJson = String.format("{\"question\":\"%s\"}", TEST_QUESTION);
    when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(questionJson)));
    doNothing().when(mockSession).setAttribute(CACHED_QUESTION, TEST_QUESTION);

    restService.doPost(mockRequest, mockResponse);

    verify(mockSession, times(1)).setAttribute(CACHED_QUESTION, TEST_QUESTION);
    verify(mockResponse, times(1)).setStatus(HttpServletResponse.SC_OK);
  }

  /**
   * Test doPost with /configCheck path.
   */
  @Test
  public void testDoPostWithConfigCheck() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn("/configCheck");

    restService.doPost(mockRequest, mockResponse);

    verify(mockResponse, times(1)).setStatus(HttpServletResponse.SC_OK);
  }

  /**
   * Test doPost with /generateTitleConversation path.
   */
  @Test
  public void testDoPostWithGenerateTitleConversation() throws Exception {
    verifyPostRoutesToConversationHandler("/generateTitleConversation",
        () -> ConversationUtils.handleGetTitleConversation(mockRequest, mockResponse));
  }

  /**
   * Test doPost with /renameConversation path.
   */
  @Test
  public void testDoPostWithRenameConversation() throws Exception {
    verifyPostRoutesToConversationHandler("/renameConversation",
        () -> ConversationUtils.handleRenameConversation(mockRequest, mockResponse));
  }

  /**
   * Test doPost with /deleteConversation path.
   */
  @Test
  public void testDoPostWithDeleteConversation() throws Exception {
    verifyPostRoutesToConversationHandler("/deleteConversation",
        () -> ConversationUtils.handleDeleteConversation(mockRequest, mockResponse));
  }

  /**
   * Test doPost with /restoreConversation path.
   */
  @Test
  public void testDoPostWithRestoreConversation() throws Exception {
    verifyPostRoutesToConversationHandler("/restoreConversation",
        () -> ConversationUtils.handleRestoreConversation(mockRequest, mockResponse));
  }

  /**
   * Test doPost with /permanentDeleteConversation path.
   */
  @Test
  public void testDoPostWithPermanentDeleteConversation() throws Exception {
    verifyPostRoutesToConversationHandler("/permanentDeleteConversation",
        () -> ConversationUtils.handlePermanentDeleteConversation(mockRequest, mockResponse));
  }

  private void verifyPostRoutesToConversationHandler(String path,
      MockedStatic.Verification handler) throws Exception {
    when(mockRequest.getPathInfo()).thenReturn(path);
    mockedConversationUtils.when(handler).thenAnswer(invocation -> null);

    restService.doPost(mockRequest, mockResponse);

    mockedConversationUtils.verify(handler, times(1));
  }

  /**
   * Test doPost with invalid path.
   */
  @Test
  public void testDoPostWithInvalidPath() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn("/invalidPath");

    restService.doPost(mockRequest, mockResponse);

    verify(mockResponse, times(1)).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }

  /**
   * Test doPost with exception during processing.
   */
  @Test
  public void testDoPostWithException() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn(QUESTION_PATH);
    doThrow(new RuntimeException(TEST_ERROR_MESSAGE)).when(restService).handleQuestion(mockRequest, mockResponse);

    restService.doPost(mockRequest, mockResponse);

    verify(mockResponse, times(1)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  /**
   * Test handleStructure with missing appId.
   */
  @Test
  public void testHandleStructureWithMissingAppId() throws Exception {
    when(mockRequest.getPathInfo()).thenReturn("/structure");
    JSONObject params = new JSONObject();
    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest)).thenReturn(params);

    restService.doGet(mockRequest, mockResponse);

    verify(mockResponse, times(1)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    verify(mockResponse, org.mockito.Mockito.atLeastOnce()).getWriter();
  }
}
