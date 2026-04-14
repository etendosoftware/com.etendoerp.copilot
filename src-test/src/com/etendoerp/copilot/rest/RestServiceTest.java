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
import static org.junit.Assert.assertTrue;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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
 * Unit tests for {@link RestService} handler methods, validation, and utilities.
 * Routing tests are in {@link RestServiceRoutingTest}.
 */
public class RestServiceTest extends WeldBaseTest {

  private static final String TEST_ERROR_MESSAGE = "Test error";
  private static final String QUESTION_PATH = "/question";
  private static final String ASYNC_QUESTION_PATH = "/aquestion";
  private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

  private RestService restService;

  @Mock
  private HttpServletRequest mockRequest;
  @Mock
  private HttpServletResponse mockResponse;
  @Mock
  private HttpSession mockSession;
  @Mock
  private PrintWriter mockWriter;

  private MockedStatic<RestServiceUtil> mockedRestServiceUtil;
  private MockedStatic<RequestUtils> mockedRequestUtils;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  private AutoCloseable mocks;

  private static final String TEST_QUESTION = "What is the weather?";
  private static final String TEST_APP_ID = "testAppId123";

  /**
   * Sets up the necessary mocks and spies before each test.
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    mocks = MockitoAnnotations.openMocks(this);

    restService = spy(new RestService());

    // Set up static mocks
    mockedRestServiceUtil = mockStatic(RestServiceUtil.class);
    mockedRequestUtils = mockStatic(RequestUtils.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    // Configure OBContext
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN,
        TestConstants.Clients.FB_GRP, TestConstants.Orgs.ESP_NORTE);
    VariablesSecureApp vsa = new VariablesSecureApp(
        OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(),
        OBContext.getOBContext().getCurrentOrganization().getId(),
        OBContext.getOBContext().getRole().getId()
    );
    RequestContext.get().setVariableSecureApp(vsa);

    // Configure common mocks
    when(mockResponse.getWriter()).thenReturn(mockWriter);
    doNothing().when(mockWriter).write(anyString());
    when(mockRequest.getSession()).thenReturn(mockSession);

    // Configure OBMessageUtils mock
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(anyString()))
        .thenReturn("Error message");
  }

  /**
   * Tears down the test environment after each test.
   */
  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
    if (mockedRestServiceUtil != null) {
      mockedRestServiceUtil.close();
    }
    if (mockedRequestUtils != null) {
      mockedRequestUtils.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
  }

  // ============ handleQuestion Tests ============

  /**
   * Test handleQuestion with synchronous request and valid parameters.
   */
  @Test
  public void testHandleQuestionWithSyncRequest() throws IOException, JSONException {
    // Given
    when(mockRequest.getPathInfo()).thenReturn(QUESTION_PATH);
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_QUESTION, TEST_QUESTION);
    json.put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest)).thenReturn(json);
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(null);
    doReturn(false).when(restService).isAsyncRequest(mockRequest);
    doNothing().when(restService).processSyncRequest(any(HttpServletResponse.class), any(JSONObject.class));

    // When
    restService.handleQuestion(mockRequest, mockResponse);

    // Then
    verify(restService, times(1)).processSyncRequest(mockResponse, json);
  }

  /**
   * Test handleQuestion with asynchronous request.
   */
  @Test
  public void testHandleQuestionWithAsyncRequest() throws IOException, JSONException {
    // Given
    when(mockRequest.getPathInfo()).thenReturn(ASYNC_QUESTION_PATH);
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_QUESTION, TEST_QUESTION);
    json.put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest)).thenReturn(json);
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(null);
    doReturn(true).when(restService).isAsyncRequest(mockRequest);

    JSONObject responseJson = new JSONObject().put("response", "data");
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(true), eq(mockResponse), any(JSONObject.class)))
        .thenReturn(responseJson);

    // When
    restService.handleQuestion(mockRequest, mockResponse);

    // Then
    mockedRestServiceUtil.verify(() -> RestServiceUtil.handleQuestion(eq(true), eq(mockResponse), any(JSONObject.class)), times(1));
  }

  /**
   * Test handleQuestion throws exception when question is missing.
   */
  @Test(expected = OBException.class)
  public void testHandleQuestionWithMissingQuestion() throws IOException, JSONException {
    // Given
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest)).thenReturn(json);
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(null);

    // When
    restService.handleQuestion(mockRequest, mockResponse);

    // Then - Exception is expected
  }

  /**
   * Test handleQuestion throws exception when appId is missing.
   */
  @Test(expected = OBException.class)
  public void testHandleQuestionWithMissingAppId() throws IOException, JSONException {
    // Given
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_QUESTION, TEST_QUESTION);

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest)).thenReturn(json);
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(null);

    // When
    restService.handleQuestion(mockRequest, mockResponse);

    // Then - Exception is expected
  }

  /**
   * Test handleQuestion with cached question.
   */
  @Test
  public void testHandleQuestionWithCachedQuestion() throws IOException, JSONException {
    // Given
    when(mockRequest.getPathInfo()).thenReturn(QUESTION_PATH);
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);

    mockedRequestUtils.when(() -> RequestUtils.extractRequestBody(mockRequest)).thenReturn(json);
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(TEST_QUESTION);
    doReturn(false).when(restService).isAsyncRequest(mockRequest);
    doNothing().when(restService).processSyncRequest(any(HttpServletResponse.class), any(JSONObject.class));

    // When
    restService.handleQuestion(mockRequest, mockResponse);

    // Then
    verify(mockSession, times(1)).removeAttribute(CACHED_QUESTION);
    verify(restService, times(1)).processSyncRequest(eq(mockResponse), any(JSONObject.class));
  }

  /**
   * Test processSyncRequest with successful response.
   */
  @Test
  public void testProcessSyncRequestSuccess() throws IOException, JSONException {
    // Given
    JSONObject requestJson = new JSONObject();
    requestJson.put(CopilotConstants.PROP_QUESTION, TEST_QUESTION);
    requestJson.put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);

    JSONObject responseJson = new JSONObject().put("answer", "test answer");
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(false), eq(mockResponse), any(JSONObject.class)))
        .thenReturn(responseJson);

    // When
    restService.processSyncRequest(mockResponse, requestJson);

    // Then
    verify(mockResponse, times(1)).setContentType(JSON_CONTENT_TYPE);
    verify(mockWriter, times(1)).write(responseJson.toString());
  }

  /**
   * Test processSyncRequest with CopilotRestServiceException.
   */
  @Test
  public void testProcessSyncRequestWithCopilotRestServiceException() throws IOException, JSONException {
    // Given
    JSONObject requestJson = new JSONObject();
    requestJson.put(CopilotConstants.PROP_QUESTION, TEST_QUESTION);

    CopilotRestServiceException exception = new CopilotRestServiceException(TEST_ERROR_MESSAGE, 400);
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(false), eq(mockResponse), any(JSONObject.class)))
        .thenThrow(exception);

    // When
    restService.processSyncRequest(mockResponse, requestJson);

    // Then
    verify(mockResponse, times(1)).setStatus(400);
    verify(mockWriter, times(1)).write(anyString());
  }

  /**
   * Test processSyncRequest with CopilotRestServiceException without error code.
   */
  @Test
  public void testProcessSyncRequestWithCopilotRestServiceExceptionNoCode() throws IOException, JSONException {
    // Given
    JSONObject requestJson = new JSONObject();

    CopilotRestServiceException exception = new CopilotRestServiceException(TEST_ERROR_MESSAGE, -1);
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(false), eq(mockResponse), any(JSONObject.class)))
        .thenThrow(exception);

    // When
    restService.processSyncRequest(mockResponse, requestJson);

    // Then
    verify(mockResponse, times(1)).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  /**
   * Test isAsyncRequest returns true for /aquestion path.
   */
  @Test
  public void testIsAsyncRequestWithAquestion() {
    // Given
    when(mockRequest.getPathInfo()).thenReturn(ASYNC_QUESTION_PATH);

    // When
    boolean result = restService.isAsyncRequest(mockRequest);

    // Then
    assertTrue("Should return true for /aquestion", result);
  }

  /**
   * Test isAsyncRequest returns true for /agraph path.
   */
  @Test
  public void testIsAsyncRequestWithAgraph() {
    // Given
    when(mockRequest.getPathInfo()).thenReturn("/agraph");

    // When
    boolean result = restService.isAsyncRequest(mockRequest);

    // Then
    assertTrue("Should return true for /agraph", result);
  }

  /**
   * Test isAsyncRequest returns false for other paths.
   */
  @Test
  public void testIsAsyncRequestWithOtherPath() {
    // Given
    when(mockRequest.getPathInfo()).thenReturn(QUESTION_PATH);

    // When
    boolean result = restService.isAsyncRequest(mockRequest);

    // Then
    assertFalse("Should return false for /question", result);
  }


  /**
   * Test handleAssistants with successful response.
   */
  @Test
  public void testHandleAssistantsSuccess() throws Exception {
    // Given
    JSONArray assistants = new JSONArray();
    assistants.put(new JSONObject().put("assistants", "data"));
    mockedRestServiceUtil.when(RestServiceUtil::handleAssistants).thenReturn(assistants);

    // When
    restService.handleAssistants(mockResponse);

    // Then
    verify(mockWriter, times(1)).write(assistants.toString());
  }

  /**
   * Test handleAssistants with exception.
   */
  @Test
  public void testHandleAssistantsWithException() {
    // Given
    mockedRestServiceUtil.when(RestServiceUtil::handleAssistants).thenThrow(new RuntimeException(TEST_ERROR_MESSAGE));

    // When
    restService.handleAssistants(mockResponse);

    // Then
    verify(mockResponse, times(1)).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  /**
   * Test addCachedQuestionIfPresent adds cached question when JSON has no question.
   */
  @Test
  public void testAddCachedQuestionIfPresentAddsQuestion() throws JSONException {
    // Given
    JSONObject json = new JSONObject();
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(TEST_QUESTION);

    // When
    restService.addCachedQuestionIfPresent(mockRequest, json);

    // Then
    assertEquals("Should add cached question", TEST_QUESTION, json.getString(CopilotConstants.PROP_QUESTION));
  }

  /**
   * Test addCachedQuestionIfPresent does not override existing question.
   */
  @Test
  public void testAddCachedQuestionIfPresentDoesNotOverride() throws JSONException {
    // Given
    String existingQuestion = "Existing question";
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_QUESTION, existingQuestion);
    when(mockSession.getAttribute(CACHED_QUESTION)).thenReturn(TEST_QUESTION);

    // When
    restService.addCachedQuestionIfPresent(mockRequest, json);

    // Then
    assertEquals("Should not override existing question", existingQuestion, json.getString(CopilotConstants.PROP_QUESTION));
  }

  /**
   * Test addCachedQuestionIfPresent with null request.
   */
  @Test
  public void testAddCachedQuestionIfPresentWithNullRequest() throws JSONException {
    // Given
    JSONObject json = new JSONObject();

    // When
    restService.addCachedQuestionIfPresent(null, json);

    // Then
    assertFalse("Should not add question with null request", json.has(CopilotConstants.PROP_QUESTION));
  }

  /**
   * Test validateRequiredParams with valid parameters.
   */
  @Test
  public void testValidateRequiredParamsSuccess() throws JSONException {
    // Given
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_QUESTION, TEST_QUESTION);
    json.put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);

    // When
    restService.validateRequiredParams(json);

    // Then - No exception should be thrown
  }

  /**
   * Test validateRequiredParams throws exception when question is missing.
   */
  @Test(expected = OBException.class)
  public void testValidateRequiredParamsMissingQuestion() throws JSONException {
    // Given
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_APP_ID, TEST_APP_ID);

    // When
    restService.validateRequiredParams(json);

    // Then - Exception is expected
  }

  /**
   * Test validateRequiredParams throws exception when appId is missing.
   */
  @Test(expected = OBException.class)
  public void testValidateRequiredParamsMissingAppId() throws JSONException {
    // Given
    JSONObject json = new JSONObject();
    json.put(CopilotConstants.PROP_QUESTION, TEST_QUESTION);

    // When
    restService.validateRequiredParams(json);

    // Then - Exception is expected
  }

  /**
   * Test CACHED_QUESTION constant.
   */
  @Test
  public void testCachedQuestionConstant() {
    assertEquals("CACHED_QUESTION constant should match", "cachedQuestion", CACHED_QUESTION);
  }
}
