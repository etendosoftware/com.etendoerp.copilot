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
package com.etendoerp.copilot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

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
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.hook.CopilotFileHookManager;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

/**
 * Comprehensive unit tests for the {@link OpenAIUtils} class.
 */
public class OpenAIUtilsTest extends WeldBaseTest {

  @Mock
  private CopilotApp mockApp;
  @Mock
  private CopilotAppSource mockAppSource;
  @Mock
  private CopilotFile mockFile;
  @Mock
  private OBDal mockDal;
  @Mock
  private Attachment mockAttachment;
  @Mock
  private AttachImplementationManager mockAim;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBPropertiesProvider> mockedPropertiesProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private MockedStatic<WeldUtils> mockedWeldUtils;
  private MockedStatic<Unirest> mockedUnirest;
  private MockedStatic<FileUtils> mockedFileUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<ToolsUtil> mockedToolsUtil;
  private MockedStatic<CopilotModelUtils> mockedCopilotModelUtils;

  private AutoCloseable mocks;

  private static final String TEST_API_KEY = "test-api-key-123";
  private static final String TEST_ASSISTANT_ID = "asst_123";
  private static final String TEST_FILE_ID = "file_123";
  private static final String TEST_VECTORDB_ID = "vectordb_123";
  private static final String TEST_APP_NAME = "Test App";
  private static final String TEST_MODEL = "gpt-4";
  private static final String PROPERTIES_KEY = "properties";
  private static final String TEST_FILE_NAME = "test.txt";

  /**
   * Sets up the necessary mocks and spies before each test.
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    mocks = MockitoAnnotations.openMocks(this);

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

    mockedOBDal = mockStatic(OBDal.class);
    mockedPropertiesProvider = mockStatic(OBPropertiesProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedWeldUtils = mockStatic(WeldUtils.class);
    mockedUnirest = mockStatic(Unirest.class);
    mockedFileUtils = mockStatic(FileUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedToolsUtil = mockStatic(ToolsUtil.class);
    mockedCopilotModelUtils = mockStatic(CopilotModelUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(mockDal);
    doNothing().when(mockDal).save(any());
    doNothing().when(mockDal).flush();

    Properties mockProperties = new Properties();
    mockProperties.setProperty(OpenAIUtils.OPENAI_API_KEY, TEST_API_KEY);
    OBPropertiesProvider mockProvider = mock(OBPropertiesProvider.class);
    when(mockProvider.getOpenbravoProperties()).thenReturn(mockProperties);
    mockedPropertiesProvider.when(OBPropertiesProvider::getInstance).thenReturn(mockProvider);

    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(anyString()))
        .thenReturn("Error message: %s");
  }

  /**
   * Tears down the test environment after each test.
   */
  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mockedPropertiesProvider != null) {
      mockedPropertiesProvider.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
    if (mockedWeldUtils != null) {
      mockedWeldUtils.close();
    }
    if (mockedUnirest != null) {
      mockedUnirest.close();
    }
    if (mockedFileUtils != null) {
      mockedFileUtils.close();
    }
    if (mockedCopilotUtils != null) {
      mockedCopilotUtils.close();
    }
    if (mockedToolsUtil != null) {
      mockedToolsUtil.close();
    }
    if (mockedCopilotModelUtils != null) {
      mockedCopilotModelUtils.close();
    }
  }

  /**
   * Test wrappWithJSONSchema creates correct JSON schema structure.
   */
  @Test
  public void testWrappWithJSONSchema() throws JSONException {
    JSONObject parameters = new JSONObject();
    parameters.put("name", new JSONObject().put("type", "string"));
    parameters.put("age", new JSONObject().put("type", "number"));

    JSONObject result = OpenAIUtils.wrappWithJSONSchema(parameters);

    assertNotNull(result);
    assertEquals("object", result.getString("type"));
    assertTrue(result.has(PROPERTIES_KEY));
    JSONObject properties = result.getJSONObject(PROPERTIES_KEY);
    assertTrue(properties.has("name"));
    assertTrue(properties.has("age"));
  }

  /**
   * Test wrappWithJSONSchema with empty parameters.
   */
  @Test
  public void testWrappWithJSONSchemaEmpty() throws JSONException {
    JSONObject parameters = new JSONObject();

    JSONObject result = OpenAIUtils.wrappWithJSONSchema(parameters);

    assertNotNull(result);
    assertEquals("object", result.getString("type"));
    assertTrue(result.has(PROPERTIES_KEY));
  }

  /**
   * Test logIfDebug does not throw exception.
   */
  @Test
  public void testLogIfDebug() {
    // When/Then - Should not throw any exception
    OpenAIUtils.logIfDebug("Test message");
    OpenAIUtils.logIfDebug(null);
  }

  /**
   * Test getOpenaiApiKey retrieves API key from properties.
   */
  @Test
  public void testGetOpenaiApiKey() {
    // When
    String apiKey = OpenAIUtils.getOpenaiApiKey();

    // Then
    assertEquals(TEST_API_KEY, apiKey);
  }

  /**
   * Test checkIfAppCanUseAttachedFiles with valid configuration.
   */
  @Test
  public void testCheckIfAppCanUseAttachedFilesWithCodeInterpreter() {
    // Given
    when(mockApp.isCodeInterpreter()).thenReturn(true);
    when(mockApp.isRetrieval()).thenReturn(false);
    List<CopilotAppSource> knowledgeBaseFiles = new ArrayList<>();
    knowledgeBaseFiles.add(mockAppSource);

    // When/Then - Should not throw exception
    OpenAIUtils.checkIfAppCanUseAttachedFiles(mockApp, knowledgeBaseFiles);
  }

  /**
   * Test checkIfAppCanUseAttachedFiles with retrieval enabled.
   */
  @Test
  public void testCheckIfAppCanUseAttachedFilesWithRetrieval() {
    // Given
    when(mockApp.isCodeInterpreter()).thenReturn(false);
    when(mockApp.isRetrieval()).thenReturn(true);
    List<CopilotAppSource> knowledgeBaseFiles = new ArrayList<>();
    knowledgeBaseFiles.add(mockAppSource);

    // When/Then - Should not throw exception
    OpenAIUtils.checkIfAppCanUseAttachedFiles(mockApp, knowledgeBaseFiles);
  }

  /**
   * Test checkIfAppCanUseAttachedFiles throws exception when neither flag is enabled.
   */
  @Test
  public void testCheckIfAppCanUseAttachedFilesThrowsException() {
    // Given
    when(mockApp.isCodeInterpreter()).thenReturn(false);
    when(mockApp.isRetrieval()).thenReturn(false);
    when(mockApp.getName()).thenReturn(TEST_APP_NAME);
    List<CopilotAppSource> knowledgeBaseFiles = new ArrayList<>();
    knowledgeBaseFiles.add(mockAppSource);

    // When/Then
    assertThrows(OBException.class, () ->
        OpenAIUtils.checkIfAppCanUseAttachedFiles(mockApp, knowledgeBaseFiles));
  }

  /**
   * Test checkIfAppCanUseAttachedFiles with empty file list.
   */
  @Test
  public void testCheckIfAppCanUseAttachedFilesWithEmptyList() {
    // Given
    when(mockApp.isCodeInterpreter()).thenReturn(false);
    when(mockApp.isRetrieval()).thenReturn(false);
    List<CopilotAppSource> knowledgeBaseFiles = new ArrayList<>();

    // When/Then - Should not throw exception because list is empty
    OpenAIUtils.checkIfAppCanUseAttachedFiles(mockApp, knowledgeBaseFiles);
  }


  /**
   * Test uploadFileToOpenAI with successful upload.
   */
  @Test
  public void testUploadFileToOpenAISuccess() throws Exception {
    // Given
    File mockFileToUpload = mock(File.class);
    when(mockFileToUpload.getName()).thenReturn(TEST_FILE_NAME);

    JSONObject successResponse = new JSONObject();
    successResponse.put("id", TEST_FILE_ID);

    HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
    when(mockHttpResponse.isSuccess()).thenReturn(true);
    when(mockHttpResponse.getBody()).thenReturn(successResponse.toString());

    // Mock Unirest chain
    kong.unirest.HttpRequestWithBody mockRequest = mock(kong.unirest.HttpRequestWithBody.class);
    kong.unirest.MultipartBody mockMultipart = mock(kong.unirest.MultipartBody.class);

    mockedUnirest.when(() -> Unirest.post(anyString())).thenReturn(mockRequest);
    when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
    when(mockRequest.field(eq("purpose"), anyString())).thenReturn(mockMultipart);
    when(mockMultipart.field(eq("file"), any(File.class), anyString())).thenReturn(mockMultipart);
    when(mockMultipart.asString()).thenReturn(mockHttpResponse);

    try (MockedStatic<OpenAIUtils> mockedOpenAIUtils = mockStatic(OpenAIUtils.class, CALLS_REAL_METHODS)) {
      mockedOpenAIUtils.when(() -> OpenAIUtils.getOpenaiApiKey()).thenReturn(TEST_API_KEY);

      // When
      String fileId = OpenAIUtils.uploadFileToOpenAI(TEST_API_KEY, mockFileToUpload);

      // Then
      assertEquals(TEST_FILE_ID, fileId);
    }
  }

  /**
   * Test uploadFileToOpenAI with error response.
   */
  @Test
  public void testUploadFileToOpenAIWithError() throws Exception {
    // Given
    File mockFileToUpload = mock(File.class);
    when(mockFileToUpload.getName()).thenReturn(TEST_FILE_NAME);

    JSONObject errorResponse = new JSONObject();
    JSONObject error = new JSONObject();
    error.put("message", "Invalid file format");
    errorResponse.put("error", error);

    HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
    when(mockHttpResponse.isSuccess()).thenReturn(false);
    when(mockHttpResponse.getBody()).thenReturn(errorResponse.toString());

    // Mock Unirest chain
    kong.unirest.HttpRequestWithBody mockRequest = mock(kong.unirest.HttpRequestWithBody.class);
    kong.unirest.MultipartBody mockMultipart = mock(kong.unirest.MultipartBody.class);

    mockedUnirest.when(() -> Unirest.post(anyString())).thenReturn(mockRequest);
    when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
    when(mockRequest.field(eq("purpose"), anyString())).thenReturn(mockMultipart);
    when(mockMultipart.field(eq("file"), any(File.class), anyString())).thenReturn(mockMultipart);
    when(mockMultipart.asString()).thenReturn(mockHttpResponse);

    try (MockedStatic<OpenAIUtils> mockedOpenAIUtils = mockStatic(OpenAIUtils.class, CALLS_REAL_METHODS)) {
      mockedOpenAIUtils.when(() -> OpenAIUtils.getOpenaiApiKey()).thenReturn(TEST_API_KEY);

      // When/Then
      assertThrows(OBException.class, () ->
          OpenAIUtils.uploadFileToOpenAI(TEST_API_KEY, mockFileToUpload));
    }
  }

  /**
   * Test getModelList retrieves models successfully.
   */
  @Test
  public void testGetModelListSuccess() throws Exception {
    // Given
    JSONArray models = new JSONArray();
    models.put(new JSONObject().put("id", TEST_MODEL));
    models.put(new JSONObject().put("id", "gpt-3.5-turbo"));

    JSONObject response = new JSONObject();
    response.put("data", models.toString());

    try (MockedStatic<OpenAIUtils> mockedOpenAIUtils = mockStatic(OpenAIUtils.class, CALLS_REAL_METHODS)) {
      mockedOpenAIUtils.when(() -> OpenAIUtils.makeRequestToOpenAI(
          eq(TEST_API_KEY),
          eq(OpenAIUtils.ENDPOINT_MODELS),
          eq(null),
          eq("GET"),
          eq(null),
          eq(true)))
          .thenReturn(response);

      // When
      JSONArray result = OpenAIUtils.getModelList(TEST_API_KEY);

      // Then
      assertNotNull(result);
      assertEquals(2, result.length());
    }
  }

  /**
   * Test getModelList throws exception on error.
   */
  @Test
  public void testGetModelListThrowsException() throws Exception {
    // Given
    try (MockedStatic<OpenAIUtils> mockedOpenAIUtils = mockStatic(OpenAIUtils.class, CALLS_REAL_METHODS)) {
      mockedOpenAIUtils.when(() -> OpenAIUtils.makeRequestToOpenAI(
          eq(TEST_API_KEY),
          eq(OpenAIUtils.ENDPOINT_MODELS),
          eq(null),
          eq("GET"),
          eq(null),
          eq(true)))
          .thenThrow(new JSONException("Parse error"));

      // When/Then
      assertThrows(OBException.class, () -> OpenAIUtils.getModelList(TEST_API_KEY));
    }
  }




  /**
   * Test syncAppSource when file hasn't changed.
   */
  @Test
  public void testSyncAppSourceFileNotChanged() throws Exception {
    // Given
    when(mockAppSource.getFile()).thenReturn(mockFile);
    when(mockFile.getName()).thenReturn(TEST_FILE_NAME);
    when(mockFile.getOpenaiIdFile()).thenReturn(TEST_FILE_ID);
    when(mockFile.getLastSync()).thenReturn(new Date(System.currentTimeMillis() + 10000));
    when(mockFile.getUpdated()).thenReturn(new Date());

    CopilotFileHookManager mockHookManager = mock(CopilotFileHookManager.class);
    mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class))
        .thenReturn(mockHookManager);
    doNothing().when(mockHookManager).executeHooks(any());

    OBCriteria<Attachment> mockCriteria = mock(OBCriteria.class);
    when(mockDal.createCriteria(Attachment.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(null);

    // When
    OpenAIUtils.syncAppSource(mockAppSource, TEST_API_KEY);

    // Then - Should return early without uploading
    verify(mockAppSource, times(0)).setOpenaiIdFile(anyString());
  }

  /**
   * Test syncAppSource with new file upload.
   */
  @Test
  public void testSyncAppSourceNewFileUpload() throws Exception {
    // Given
    when(mockAppSource.getFile()).thenReturn(mockFile);
    when(mockAppSource.getOpenaiIdFile()).thenReturn("");
    when(mockFile.getName()).thenReturn(TEST_FILE_NAME);
    when(mockFile.getOpenaiIdFile()).thenReturn("");
    when(mockFile.getType()).thenReturn(CopilotConstants.KBF_TYPE_ATTACHED);

    CopilotFileHookManager mockHookManager = mock(CopilotFileHookManager.class);
    mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class))
        .thenReturn(mockHookManager);
    doNothing().when(mockHookManager).executeHooks(any());

    File tempFile = mock(File.class);
    when(tempFile.getName()).thenReturn(TEST_FILE_NAME);

    try (MockedStatic<OpenAIUtils> mockedOpenAIUtils = mockStatic(OpenAIUtils.class, CALLS_REAL_METHODS)) {
      mockedOpenAIUtils.when(() -> OpenAIUtils.getFileFromCopilotFile(mockFile))
          .thenReturn(tempFile);
      mockedOpenAIUtils.when(() -> OpenAIUtils.uploadFileToOpenAI(TEST_API_KEY, tempFile))
          .thenReturn(TEST_FILE_ID);

      // When
      OpenAIUtils.syncAppSource(mockAppSource, TEST_API_KEY);

      // Then
      verify(mockFile, times(1)).setOpenaiIdFile(TEST_FILE_ID);
      verify(mockAppSource, times(1)).setOpenaiIdFile(TEST_FILE_ID);
    }
  }

  /**
   * Test getFileFromCopilotFile retrieves file successfully.
   */
  @Test
  public void testGetFileFromCopilotFileSuccess() throws Exception {
    // Given
    when(mockFile.getId()).thenReturn("file123");
    when(mockAttachment.getId()).thenReturn("attach123");
    when(mockAttachment.getName()).thenReturn("document.pdf");

    OBCriteria<Attachment> mockCriteria = mock(OBCriteria.class);
    when(mockDal.createCriteria(Attachment.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(mockAttachment);

    mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class))
        .thenReturn(mockAim);
    doNothing().when(mockAim).download(anyString(), any(ByteArrayOutputStream.class));

    // When
    File result = OpenAIUtils.getFileFromCopilotFile(mockFile);

    // Then
    assertNotNull(result);
    assertTrue(result.getName().contains("document"));
    assertTrue(result.getName().endsWith(".pdf"));
  }

  /**
   * Test getFileFromCopilotFile throws exception when attachment is missing.
   */
  @Test
  public void testGetFileFromCopilotFileMissingAttachment() {
    // Given
    when(mockFile.getId()).thenReturn("file123");
    when(mockFile.getName()).thenReturn(TEST_FILE_NAME);

    OBCriteria<Attachment> mockCriteria = mock(OBCriteria.class);
    when(mockDal.createCriteria(Attachment.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(null);

    mockedFileUtils.when(() -> FileUtils.throwMissingAttachException(mockFile))
        .thenThrow(new OBException("Missing attachment"));

    // When/Then
    assertThrows(OBException.class, () -> OpenAIUtils.getFileFromCopilotFile(mockFile));
  }



  /**
   * Test makeRequestToOpenAI with GET method.
   */
  @Test
  public void testMakeRequestToOpenAIGetMethod() throws Exception {
    // Given
    JSONObject expectedResponse = new JSONObject();
    expectedResponse.put("success", true);

    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.isSuccess()).thenReturn(true);
    when(mockResponse.getBody()).thenReturn(expectedResponse.toString());

  }

  /**
   * Test makeRequestToOpenAI throws exception for invalid method.
   */
  @Test
  public void testMakeRequestToOpenAIInvalidMethod() {
    // When/Then
    assertThrows(IllegalArgumentException.class, () ->
        OpenAIUtils.makeRequestToOpenAI(TEST_API_KEY, "/test", null, "INVALID", null, true));
  }

  /**
   * Test that all constants are properly defined.
   */
  @Test
  public void testConstants() {
    assertEquals("https://api.openai.com/v1", OpenAIUtils.BASE_URL);
    assertEquals("DELETE", OpenAIUtils.METHOD_DELETE);
    assertEquals("Authorization", OpenAIUtils.HEADER_AUTHORIZATION);
    assertEquals("Content-Type", OpenAIUtils.HEADER_CONTENT_TYPE);
    assertEquals("OpenAI-Beta", OpenAIUtils.HEADER_OPEN_AI_BETA);
    assertEquals("application/json", OpenAIUtils.CONTENT_TYPE_JSON);
    assertEquals("Bearer ", OpenAIUtils.HEADER_BEARER);
    assertEquals("assistants=v2", OpenAIUtils.HEADER_ASSISTANTS_V_2);
    assertEquals("OPENAI_API_KEY", OpenAIUtils.OPENAI_API_KEY);
    assertEquals("/files", OpenAIUtils.ENDPOINT_FILES);
    assertEquals("/models", OpenAIUtils.ENDPOINT_MODELS);
    assertEquals("/assistants", OpenAIUtils.ENDPOINT_ASSISTANTS);
    assertEquals("/vector_stores", OpenAIUtils.ENDPOINT_VECTORDB);
    assertEquals(5 * 60 * 1000, OpenAIUtils.MILLIES_SOCKET_TIMEOUT);
    assertEquals("message", OpenAIUtils.MESSAGE);
    assertEquals("instructions", OpenAIUtils.INSTRUCTIONS);
  }

  /**
   * Test that constructor throws IllegalStateException (utility class pattern).
   */
  @Test
  public void testConstructorThrowsException() throws Exception {
    // Given
    java.lang.reflect.Constructor<OpenAIUtils> constructor =
        OpenAIUtils.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    // When
    java.lang.reflect.InvocationTargetException exception = assertThrows(
        java.lang.reflect.InvocationTargetException.class,
        constructor::newInstance);

    // Then
    assertInstanceOf(IllegalStateException.class, exception.getCause());
    assertEquals("Utility class", exception.getCause().getMessage());
  }
}
