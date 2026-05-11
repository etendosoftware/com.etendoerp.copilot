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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.util.CopilotConstants;

/**
 * Unit tests for {@link RestService} handler methods, validation and utilities.
 *
 * Routing scenarios are covered in {@link RestServiceRoutingTest} to avoid duplicated tests.
 */
public class RestServiceTest extends WeldBaseTest {

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
    MockedStatic<OBMessageUtils> mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    closeables.addAll(List.of(mockedRestServiceUtil, mockedRequestUtils, mockedOBMessageUtils));

    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN,
        TestConstants.Clients.SYSTEM, TestConstants.Orgs.MAIN);
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
    for (AutoCloseable closeable : closeables) {
      closeable.close();
    }
    closeables.clear();
  }

  @Test
  public void testHandleQuestionWithSyncRequest() throws Exception {
    JSONObject json = createQuestionRequestJson();
    mockQuestionRequest(json, false, null);
    doNothing().when(restService).processSyncRequest(mockResponse, json);

    restService.handleQuestion(mockRequest, mockResponse);

    verify(restService, times(1)).processSyncRequest(mockResponse, json);
  }

  @Test
  public void testHandleQuestionWithAsyncRequest() throws Exception {
    JSONObject json = createQuestionRequestJson();
    mockQuestionRequest(json, true, null);

    JSONObject responseJson = new JSONObject().put("response", "data");
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(true), eq(mockResponse), any(JSONObject.class)))
        .thenReturn(responseJson);

    restService.handleQuestion(mockRequest, mockResponse);

    mockedRestServiceUtil.verify(
        () -> RestServiceUtil.handleQuestion(eq(true), eq(mockResponse), any(JSONObject.class)), times(1));
  }

  @Test
  public void testHandleQuestionWithMissingQuestion() throws Exception {
    mockQuestionRequest(createAppIdOnlyJson(), false, null);

    org.junit.Assert.assertThrows(OBException.class,
        () -> restService.handleQuestion(mockRequest, mockResponse));
  }

  @Test
  public void testHandleQuestionWithMissingAppId() throws Exception {
    mockQuestionRequest(createQuestionOnlyJson(), false, null);

    org.junit.Assert.assertThrows(OBException.class,
        () -> restService.handleQuestion(mockRequest, mockResponse));
  }

  @Test
  public void testHandleQuestionWithCachedQuestion() throws Exception {
    JSONObject json = createAppIdOnlyJson();
    mockQuestionRequest(json, false, TEST_QUESTION);
    doNothing().when(restService).processSyncRequest(eq(mockResponse), any(JSONObject.class));

    restService.handleQuestion(mockRequest, mockResponse);

    verify(mockSession, times(1)).removeAttribute(CACHED_QUESTION);
    verify(restService, times(1)).processSyncRequest(eq(mockResponse), any(JSONObject.class));
    assertEquals(TEST_QUESTION, json.getString(CopilotConstants.PROP_QUESTION));
  }

  @Test
  public void testProcessSyncRequestSuccess() throws Exception {
    JSONObject requestJson = createQuestionRequestJson();
    JSONObject responseJson = new JSONObject().put("answer", "test answer");
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(false), eq(mockResponse), any(JSONObject.class)))
        .thenReturn(responseJson);

    restService.processSyncRequest(mockResponse, requestJson);

    verify(mockResponse, times(1)).setContentType(JSON_CONTENT_TYPE);
    verify(mockWriter, times(1)).write(responseJson.toString());
  }

  @Test
  public void testProcessSyncRequestWithCopilotRestServiceException() throws Exception {
    JSONObject requestJson = createQuestionOnlyJson();
    CopilotRestServiceException exception = new CopilotRestServiceException(TEST_ERROR_MESSAGE, 400);
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(false), eq(mockResponse), any(JSONObject.class)))
        .thenThrow(exception);

    restService.processSyncRequest(mockResponse, requestJson);

    verify(mockResponse, times(1)).setStatus(400);
    verify(mockWriter, times(1)).write(anyString());
  }

  @Test
  public void testProcessSyncRequestWithCopilotRestServiceExceptionNoCode() throws Exception {
    JSONObject requestJson = new JSONObject();
    CopilotRestServiceException exception = new CopilotRestServiceException(TEST_ERROR_MESSAGE, -1);
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(false), eq(mockResponse), any(JSONObject.class)))
        .thenThrow(exception);

    restService.processSyncRequest(mockResponse, requestJson);

    verify(mockResponse, times(1)).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testIsAsyncRequestWithAquestion() throws Exception {
    assertAsyncRequest(ASYNC_QUESTION_PATH, true);
  }

  @Test
  public void testIsAsyncRequestWithAgraph() throws Exception {
    assertAsyncRequest("/agraph", true);
  }

  @Test
  public void testIsAsyncRequestWithOtherPath() throws Exception {
    assertAsyncRequest(QUESTION_PATH, false);
  }

  @Test
  public void testHandleAssistantsSuccess() throws Exception {
    JSONArray assistants = new JSONArray();
    assistants.put(new JSONObject().put("assistants", "data"));
    mockedRestServiceUtil.when(RestServiceUtil::handleAssistants).thenReturn(assistants);

    restService.handleAssistants(mockResponse);

    verify(mockWriter, times(1)).write(assistants.toString());
  }

  @Test
  public void testHandleAssistantsWithException() throws Exception {
    mockedRestServiceUtil.when(RestServiceUtil::handleAssistants)
        .thenThrow(new RuntimeException(TEST_ERROR_MESSAGE));

    restService.handleAssistants(mockResponse);

    verify(mockResponse, times(1)).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testAddCachedQuestionIfPresentAddsQuestion() throws Exception {
    JSONObject json = new JSONObject();
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(TEST_QUESTION);

    restService.addCachedQuestionIfPresent(mockRequest, json);

    assertEquals(TEST_QUESTION, json.getString(CopilotConstants.PROP_QUESTION));
  }

  @Test
  public void testAddCachedQuestionIfPresentDoesNotOverride() throws Exception {
    String existingQuestion = "Existing question";
    JSONObject json = createQuestionOnlyJson(existingQuestion);
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(TEST_QUESTION);

    restService.addCachedQuestionIfPresent(mockRequest, json);

    assertEquals(existingQuestion, json.getString(CopilotConstants.PROP_QUESTION));
  }

  @Test
  public void testAddCachedQuestionIfPresentWithNullRequest() throws Exception {
    JSONObject json = new JSONObject();

    restService.addCachedQuestionIfPresent(null, json);

    assertFalse(json.has(CopilotConstants.PROP_QUESTION));
  }

  @Test
  public void testValidateRequiredParamsSuccess() throws Exception {
    restService.validateRequiredParams(createQuestionRequestJson());
  }

  @Test
  public void testValidateRequiredParamsMissingQuestion() throws Exception {
    org.junit.Assert.assertThrows(OBException.class,
        () -> restService.validateRequiredParams(createAppIdOnlyJson()));
  }

  @Test
  public void testValidateRequiredParamsMissingAppId() throws Exception {
    org.junit.Assert.assertThrows(OBException.class,
        () -> restService.validateRequiredParams(createQuestionOnlyJson()));
  }

  @Test
  public void testCachedQuestionConstant() throws Exception {
    assertEquals("cachedQuestion", CACHED_QUESTION);
  }

  private void mockQuestionRequest(JSONObject json, boolean asyncRequest, String cachedQuestion)
      throws IOException, JSONException {
    when(mockRequest.getPathInfo()).thenReturn(asyncRequest ? ASYNC_QUESTION_PATH : QUESTION_PATH);
    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest)).thenReturn(json);
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(cachedQuestion);
    doReturn(asyncRequest).when(restService).isAsyncRequest(mockRequest);
  }

  private void assertAsyncRequest(String path, boolean expected) {
    when(mockRequest.getPathInfo()).thenReturn(path);
    assertEquals(expected, restService.isAsyncRequest(mockRequest));
  }

  private JSONObject createQuestionRequestJson() throws JSONException {
    return new JSONObject()
        .put(CopilotConstants.PROP_QUESTION, TEST_QUESTION)
        .put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);
  }

  private JSONObject createQuestionOnlyJson() throws JSONException {
    return createQuestionOnlyJson(TEST_QUESTION);
  }

  private JSONObject createQuestionOnlyJson(String question) throws JSONException {
    return new JSONObject().put(CopilotConstants.PROP_QUESTION, question);
  }

  private JSONObject createAppIdOnlyJson() throws JSONException {
    return new JSONObject().put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);
  }
}
