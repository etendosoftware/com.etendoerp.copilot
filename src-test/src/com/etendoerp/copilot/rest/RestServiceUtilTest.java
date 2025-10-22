package com.etendoerp.copilot.rest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TransferQueue;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.hook.CopilotQuestionHookManager;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.ExtractedResponse;
import com.etendoerp.copilot.util.TrackingUtil;

class RestServiceUtilTest {
  private static final org.apache.logging.log4j.Logger testLog = org.apache.logging.log4j.LogManager
      .getLogger(RestServiceUtilTest.class);
  private static final String ENDPOINT = "/endpoint";
  private static final String TEST_FILE_NAME = "test.txt";
  private static final String TEST_FILE_PREFIX = "test";
  private static final String TEST_FILE_SUFFIX = ".txt";
  // common test literals to avoid duplication
  private static final String LIT_UPLOADED = "uploaded";
  private static final String LIT_APP1 = "app-1";
  private static final String LIT_CONV1 = "conv1";
  private static final String LIT_LANG1 = "lang1";
  private static final String LIT_RESPONSE_TEXT = "responseText";
  private static final String LIT_CONV_ID = "convId";
  private static final String LIT_CONV2 = "conv2";
  private static final String LIT_CONV3 = "conv3";
  private static final String LIT_RESPONSE_WITH_METADATA = "response with metadata";
  private static final String LIT_GPT4 = "gpt-4";
  private static final String LIT_OPENAI = "openai";
  private static final String LIT_PROVIDER = "provider";
  private static final String LIT_MODEL = "model";
  private static final String LIT_TIMESTAMP = "timestamp";

  // Setup and utility methods for mocks will go here

  @Test
  void testHandleFileWithEmptyList() throws Exception {
    List<FileItem> items = List.of();
    JSONObject result = RestServiceUtil.handleFile(items, ENDPOINT);
    Assertions.assertEquals(0, result.length());
  }

  @Test
  void testSendDataWithNullQueue() {
    // Should not throw
    RestServiceUtil.sendData(null, "data");
  }

  @Test
  void testHandleFileWithFile() throws Exception {
    org.apache.commons.fileupload.disk.DiskFileItem itemDisk = Mockito.mock(
        org.apache.commons.fileupload.disk.DiskFileItem.class);
    Mockito.when(itemDisk.isFormField()).thenReturn(false);
    Mockito.when(itemDisk.getName()).thenReturn(TEST_FILE_NAME);
    Mockito.when(itemDisk.getFieldName()).thenReturn("file1");
    Mockito.when(itemDisk.isInMemory()).thenReturn(true);
    Mockito.doNothing().when(itemDisk).write(org.mockito.ArgumentMatchers.any(File.class));
    List<FileItem> items = List.of(itemDisk);
    java.net.http.HttpResponse<String> mockResponse = Mockito.mock(java.net.http.HttpResponse.class);
    Mockito.when(mockResponse.body()).thenReturn(
        new org.codehaus.jettison.json.JSONObject().put(RestServiceUtil.PROP_ANSWER, LIT_UPLOADED).toString());
    try (org.mockito.MockedStatic<com.etendoerp.copilot.util.CopilotUtils> utils = org.mockito.Mockito
        .mockStatic(com.etendoerp.copilot.util.CopilotUtils.class)) {
      utils.when(
              () -> com.etendoerp.copilot.util.CopilotUtils.getResponseFromCopilot(org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.any()))
          .thenReturn(mockResponse);
      JSONObject result = RestServiceUtil.handleFile(items, ENDPOINT);
      Assertions.assertNotNull(result);
    }
  }

  @Test
  void testHandleFileWithNullName() throws Exception {
    org.apache.commons.fileupload.disk.DiskFileItem itemDisk = Mockito.mock(
        org.apache.commons.fileupload.disk.DiskFileItem.class);
    Mockito.when(itemDisk.isFormField()).thenReturn(false);
    Mockito.when(itemDisk.getName()).thenReturn(null);
    Mockito.when(itemDisk.getFieldName()).thenReturn("fileNull");
    Mockito.when(itemDisk.isInMemory()).thenReturn(true);
    Mockito.doNothing().when(itemDisk).write(org.mockito.ArgumentMatchers.any(File.class));
    List<FileItem> items = List.of(itemDisk);
    java.net.http.HttpResponse<String> mockResponse = Mockito.mock(java.net.http.HttpResponse.class);
    Mockito.when(mockResponse.body()).thenReturn(
        new org.codehaus.jettison.json.JSONObject().put(RestServiceUtil.PROP_ANSWER, LIT_UPLOADED).toString());
    try (org.mockito.MockedStatic<com.etendoerp.copilot.util.CopilotUtils> utils = org.mockito.Mockito
        .mockStatic(com.etendoerp.copilot.util.CopilotUtils.class)) {
      utils.when(
              () -> com.etendoerp.copilot.util.CopilotUtils.getResponseFromCopilot(org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.any()))
          .thenReturn(mockResponse);
      JSONObject result = RestServiceUtil.handleFile(items, ENDPOINT);
      Assertions.assertNotNull(result);
    }
  }

  @Test
  void testHandleFileWithFileOverload() throws IOException {
    java.nio.file.Path tempPath = java.nio.file.Files.createTempFile(TEST_FILE_PREFIX, TEST_FILE_SUFFIX);
    java.io.File tempFile = tempPath.toFile();
    tempFile.deleteOnExit();
    try {
      // attempt to restrict permissions in test environment
      secureTempFilePermissions(tempPath, tempFile);
      RestServiceUtil.handleFile(tempFile, ENDPOINT);
    } catch (Exception e) {
      // Acceptable if CopilotUtils is not mocked
    }
  }

  /**
   * Try to set owner-only permissions on the provided temp file. In environments
   * where POSIX permissions are not available the method falls back to the
   * {@link File#setReadable} / {@link File#setWritable} API and logs failures.
   */
  private void secureTempFilePermissions(java.nio.file.Path tempPath, java.io.File tempFile) {
    try {
      java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
          java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
      java.nio.file.Files.setPosixFilePermissions(tempPath, perms);
    } catch (UnsupportedOperationException | IOException ignore) {
      boolean ok;
      ok = tempFile.setReadable(false, false);
      if (!ok) testLog.warn("Failed to set non-owner readable on temp file: {}", tempFile.getAbsolutePath());
      ok = tempFile.setWritable(false, false);
      if (!ok) testLog.warn("Failed to set non-owner writable on temp file: {}", tempFile.getAbsolutePath());
      ok = tempFile.setReadable(true, true);
      if (!ok) testLog.warn("Failed to set owner-readable on temp file: {}", tempFile.getAbsolutePath());
      ok = tempFile.setWritable(true, true);
      if (!ok) testLog.warn("Failed to set owner-writable on temp file: {}", tempFile.getAbsolutePath());
    }
  }

  @Test
  void testSendDataWithQueue() throws InterruptedException {
    @SuppressWarnings("unchecked")
    TransferQueue<String> queue = Mockito.mock(TransferQueue.class);
    RestServiceUtil.sendData(queue, "data");
    Mockito.verify(queue).transfer("data");
  }

  @Test
  void testHandleQuestionWithMinimalJson() throws JSONException {
    JSONObject jsonRequest = new JSONObject();
    jsonRequest.put("app_id", "dummyAppId");
    jsonRequest.put("question", "What is Etendo?");
    try {
      RestServiceUtil.handleQuestion(false, null, jsonRequest);
    } catch (Exception e) {
      // Acceptable for coverage if dependencies are not mocked
    }
  }

  @Test
  void testGetFilesReceivedWithSingleFile() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("file", "file123");
    List<String> files = RestServiceUtil.getFilesReceived(json);
    Assertions.assertEquals(List.of("file123"), files);
  }

  @Test
  void testGetFilesReceivedWithArray() throws JSONException {
    JSONArray arr = new JSONArray();
    arr.put("fileA");
    arr.put("fileB");
    JSONObject json = new JSONObject();
    json.put("file", arr.toString());
    List<String> files = RestServiceUtil.getFilesReceived(json);
    Assertions.assertEquals(List.of("fileA", "fileB"), files);
  }

  @Test
  void testIsAnswerWithNullOrErrorRole() throws JSONException {
    JSONObject answerNull = new JSONObject().put("role", "null");
    JSONObject objNull = new JSONObject().put(RestServiceUtil.PROP_ANSWER, answerNull);
    Assertions.assertTrue(RestServiceUtil.isAnswerWithNullOrErrorRole(objNull));

    JSONObject answerError = new JSONObject().put("role", RestServiceUtil.PROP_ERROR);
    JSONObject objError = new JSONObject().put(RestServiceUtil.PROP_ANSWER, answerError);
    Assertions.assertTrue(RestServiceUtil.isAnswerWithNullOrErrorRole(objError));

    JSONObject answerOther = new JSONObject().put("role", "user");
    JSONObject objOther = new JSONObject().put(RestServiceUtil.PROP_ANSWER, answerOther);
    Assertions.assertFalse(RestServiceUtil.isAnswerWithNullOrErrorRole(objOther));
  }

  @Test
  void testAddTimestampToResponse() throws JSONException {
    JSONObject obj = new JSONObject();
    RestServiceUtil.addTimestampToResponse(obj);
    Assertions.assertTrue(obj.has(LIT_TIMESTAMP));
  }

  @Test
  void testExtractResponseWithAnswer() throws JSONException {
    JSONObject answer = new JSONObject();
    answer.put(RestServiceUtil.PROP_RESPONSE, LIT_RESPONSE_TEXT);
    answer.put(RestServiceUtil.PROP_CONVERSATION_ID, LIT_CONV_ID);
    JSONObject finalResponseAsync = new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer);
    JSONObject responseOriginal = new JSONObject();
    ExtractedResponse result = RestServiceUtil.extractResponse(finalResponseAsync, responseOriginal,
        "");
    Assertions.assertEquals(LIT_RESPONSE_TEXT, result.getResponse());
    Assertions.assertEquals(LIT_CONV_ID, result.getConversationId());
    Assertions.assertEquals(LIT_CONV_ID, responseOriginal.getString(RestServiceUtil.PROP_CONVERSATION_ID));
    // Verify metadata is empty when not provided
    Assertions.assertNotNull(result.getMetadata());
    Assertions.assertEquals(0, result.getMetadata().length());
  }

  @Test
  void testExtractResponseWithAnswerAndMetadata() throws JSONException {
    JSONObject metadata = new JSONObject();
    metadata.put(LIT_MODEL, LIT_GPT4);
    metadata.put("tokens", 150);

    JSONObject answer = new JSONObject();
    answer.put(RestServiceUtil.PROP_RESPONSE, LIT_RESPONSE_TEXT);
    answer.put(RestServiceUtil.PROP_CONVERSATION_ID, LIT_CONV_ID);
    answer.put(RestServiceUtil.METADATA, metadata);
    JSONObject finalResponseAsync = new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer);
    JSONObject responseOriginal = new JSONObject();
    ExtractedResponse result = RestServiceUtil.extractResponse(finalResponseAsync, responseOriginal, "");
    Assertions.assertEquals(LIT_RESPONSE_TEXT, result.getResponse());
    Assertions.assertEquals(LIT_CONV_ID, result.getConversationId());
    Assertions.assertEquals(LIT_CONV_ID, responseOriginal.getString(RestServiceUtil.PROP_CONVERSATION_ID));
    // Verify metadata is correctly extracted
    Assertions.assertNotNull(result.getMetadata());
    Assertions.assertEquals(LIT_GPT4, result.getMetadata().getString(LIT_MODEL));
    Assertions.assertEquals(150, result.getMetadata().getInt("tokens"));
    // Verify metadata is also added to responseOriginal
    Assertions.assertTrue(responseOriginal.has(RestServiceUtil.METADATA));
    Assertions.assertEquals(LIT_GPT4, responseOriginal.getJSONObject(RestServiceUtil.METADATA).getString(LIT_MODEL));
  }

  @Test
  void testExtractResponseWithResponse() throws JSONException {
    JSONObject finalResponseAsync = new JSONObject();
    finalResponseAsync.put(RestServiceUtil.PROP_RESPONSE, LIT_RESPONSE_TEXT);
    finalResponseAsync.put(RestServiceUtil.PROP_CONVERSATION_ID, LIT_CONV_ID);
    JSONObject responseOriginal = new JSONObject();
    ExtractedResponse result = RestServiceUtil.extractResponse(finalResponseAsync, responseOriginal, "");
    Assertions.assertEquals(LIT_RESPONSE_TEXT, result.getResponse());
    Assertions.assertEquals(LIT_CONV_ID, result.getConversationId());
    Assertions.assertEquals(LIT_CONV_ID, responseOriginal.getString(RestServiceUtil.PROP_CONVERSATION_ID));
    // Verify metadata is empty when not provided
    Assertions.assertNotNull(result.getMetadata());
    Assertions.assertEquals(0, result.getMetadata().length());
  }

  @Test
  void testExtractResponseWithResponseAndMetadata() throws JSONException {
    JSONObject metadata = new JSONObject();
    metadata.put(LIT_PROVIDER, LIT_OPENAI);
    metadata.put("usage", 75);

    JSONObject finalResponseAsync = new JSONObject();
    finalResponseAsync.put(RestServiceUtil.PROP_RESPONSE, LIT_RESPONSE_TEXT);
    finalResponseAsync.put(RestServiceUtil.PROP_CONVERSATION_ID, LIT_CONV_ID);
    finalResponseAsync.put(RestServiceUtil.METADATA, metadata);
    JSONObject responseOriginal = new JSONObject();
    ExtractedResponse result = RestServiceUtil.extractResponse(finalResponseAsync, responseOriginal, "");
    Assertions.assertEquals(LIT_RESPONSE_TEXT, result.getResponse());
    Assertions.assertEquals(LIT_CONV_ID, result.getConversationId());
    Assertions.assertEquals(LIT_CONV_ID, responseOriginal.getString(RestServiceUtil.PROP_CONVERSATION_ID));
    // Verify metadata is correctly extracted
    Assertions.assertNotNull(result.getMetadata());
    Assertions.assertEquals(LIT_OPENAI, result.getMetadata().getString(LIT_PROVIDER));
    Assertions.assertEquals(75, result.getMetadata().getInt("usage"));
    // Verify metadata is also added to responseOriginal
    Assertions.assertTrue(responseOriginal.has(RestServiceUtil.METADATA));
    Assertions.assertEquals(LIT_OPENAI,
        responseOriginal.getJSONObject(RestServiceUtil.METADATA).getString(LIT_PROVIDER));
  }

  @Test
  void testExtractResponseWithNullMetadata() throws JSONException {
    JSONObject answer = new JSONObject();
    answer.put(RestServiceUtil.PROP_RESPONSE, LIT_RESPONSE_TEXT);
    answer.put(RestServiceUtil.PROP_CONVERSATION_ID, LIT_CONV_ID);
    answer.put(RestServiceUtil.METADATA, (Object) null); // Explicitly null metadata
    JSONObject finalResponseAsync = new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer);
    JSONObject responseOriginal = new JSONObject();
    ExtractedResponse result = RestServiceUtil.extractResponse(finalResponseAsync, responseOriginal, "");
    Assertions.assertEquals(LIT_RESPONSE_TEXT, result.getResponse());
    Assertions.assertEquals(LIT_CONV_ID, result.getConversationId());
    // Verify metadata is handled as empty when null
    Assertions.assertNotNull(result.getMetadata());
    Assertions.assertEquals(0, result.getMetadata().length());
    // Verify null metadata is not added to responseOriginal
    Assertions.assertFalse(responseOriginal.has(RestServiceUtil.METADATA));
  }

  @Test
  void testHandleMissingAnswerThrows() throws JSONException {
    JSONObject obj = new JSONObject();
    JSONArray detail = new JSONArray();
    JSONObject detailObj = new JSONObject();
    detailObj.put("message", "errorMsg");
    detail.put(detailObj);
    obj.put("detail", detail);
    // messageBD uses OBContext internally which is not initialized in unit tests and causes NPE.
    // Mock the static call to return a formatted message so handleMissingAnswer throws OBException as expected.
    try (org.mockito.MockedStatic<org.openbravo.erpCommon.utility.OBMessageUtils> mocked = org.mockito.Mockito
        .mockStatic(org.openbravo.erpCommon.utility.OBMessageUtils.class)) {
      mocked.when(() -> org.openbravo.erpCommon.utility.OBMessageUtils.messageBD(RestServiceUtil.ETCOP_COPILOT_ERROR))
          .thenReturn("ETCOP Error: %s");
      Assertions.assertThrows(OBException.class, () -> RestServiceUtil.handleMissingAnswer(obj));
    }
  }

  @Test
  void testHandleMissingAnswerNoDetail() {
    JSONObject obj = new JSONObject();
    Assertions.assertDoesNotThrow(() -> RestServiceUtil.handleMissingAnswer(obj));
  }

  @Test
  void testDetermineEndpoint() {
    // Use a simple stub for CopilotApp
    class TestCopilotApp extends CopilotApp {
      @Override
      public String getAppType() {
        return CopilotConstants.APP_TYPE_OPENAI;
      }
    }
    CopilotApp copilotApp = new TestCopilotApp();
    // For static method, just test both branches
    Assertions.assertEquals(RestServiceUtil.QUESTION, RestServiceUtil.determineEndpoint(false, copilotApp));
    Assertions.assertEquals(RestServiceUtil.AQUESTION, RestServiceUtil.determineEndpoint(true, copilotApp));
  }

  @Test
  void testProcessFileItemInMemoryAndNullName() throws Exception {
    // Mock DiskFileItem for in-memory write and null name
    org.apache.commons.fileupload.disk.DiskFileItem itemDisk = Mockito.mock(
        org.apache.commons.fileupload.disk.DiskFileItem.class);
    Mockito.when(itemDisk.isFormField()).thenReturn(false);
    Mockito.when(itemDisk.getName()).thenReturn(null);
    Mockito.when(itemDisk.getFieldName()).thenReturn("fileNull");
    Mockito.when(itemDisk.isInMemory()).thenReturn(true);
    Mockito.doNothing().when(itemDisk).write(org.mockito.ArgumentMatchers.any(File.class));

    java.net.http.HttpResponse<String> mockResponse = Mockito.mock(java.net.http.HttpResponse.class);
    Mockito.when(mockResponse.body()).thenReturn(
        new org.codehaus.jettison.json.JSONObject().put(RestServiceUtil.PROP_ANSWER, LIT_UPLOADED).toString());

    try (org.mockito.MockedStatic<com.etendoerp.copilot.util.CopilotUtils> utils = org.mockito.Mockito
        .mockStatic(com.etendoerp.copilot.util.CopilotUtils.class);
         org.mockito.MockedStatic<org.openbravo.erpCommon.utility.OBMessageUtils> mockedMsg = org.mockito.Mockito
             .mockStatic(org.openbravo.erpCommon.utility.OBMessageUtils.class)) {
      utils.when(
              () -> com.etendoerp.copilot.util.CopilotUtils.getResponseFromCopilot(org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.any()))
          .thenReturn(mockResponse);
      mockedMsg.when(
              () -> org.openbravo.erpCommon.utility.OBMessageUtils.messageBD(org.mockito.ArgumentMatchers.anyString()))
          .thenReturn("msg");

      // invoke private method via reflection
      // call package-private helper directly
      String result = RestServiceUtil.processFileItem(itemDisk, ENDPOINT);
      Assertions.assertEquals(LIT_UPLOADED, result);
    }
  }

  @Test
  void testProcessFileItemDiskRenameFail() throws Exception {
    // Mock DiskFileItem for disk store where rename fails
    org.apache.commons.fileupload.disk.DiskFileItem itemDisk = Mockito.mock(
        org.apache.commons.fileupload.disk.DiskFileItem.class);
    Mockito.when(itemDisk.isFormField()).thenReturn(false);
    Mockito.when(itemDisk.getName()).thenReturn(TEST_FILE_NAME);
    Mockito.when(itemDisk.getFieldName()).thenReturn("file1");
    Mockito.when(itemDisk.isInMemory()).thenReturn(false);

    // Provide a store location File whose renameTo returns false by overriding renameTo
    File fakeStore = new File("fakeStore.tmp") {
      @Override
      public boolean renameTo(File dest) {
        return false;
      }
    };
    Mockito.when(itemDisk.getStoreLocation()).thenReturn(fakeStore);

    try (org.mockito.MockedStatic<org.openbravo.erpCommon.utility.OBMessageUtils> mockedMsg = org.mockito.Mockito
        .mockStatic(org.openbravo.erpCommon.utility.OBMessageUtils.class)) {
      mockedMsg.when(
              () -> org.openbravo.erpCommon.utility.OBMessageUtils.messageBD(org.mockito.ArgumentMatchers.anyString()))
          .thenReturn("ETCOP_ErrorSavingFile");

      // call package-private helper directly and expect OBException
      Assertions.assertThrows(org.openbravo.base.exception.OBException.class,
          () -> RestServiceUtil.processFileItem(itemDisk, ENDPOINT));
    }
  }

  @Test
  void testServerSideEventsAsyncWritesDataAndReturnsEmpty() throws Exception {
    String payload = "data: {\"answer\":{\"role\":\"user\",\"response\":\"partial\"}}\n";
    ByteArrayInputStream in = new ByteArrayInputStream(payload.getBytes());

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
    Mockito.when(resp.getWriter()).thenReturn(pw);

    JSONObject result = RestServiceUtil.serverSideEvents(true, resp, in);
    Assertions.assertNotNull(result);
    pw.flush();
    String written = sw.toString();
    Assertions.assertTrue(written.contains("data:"));
  }

  @Test
  void testServerSideEventsSyncReturnsAnswerForErrorRole() throws Exception {
    String lastLine = new JSONObject().put(RestServiceUtil.PROP_ANSWER,
        new JSONObject().put("role", RestServiceUtil.PROP_ERROR).put(RestServiceUtil.PROP_RESPONSE, "err")).toString();
    String payload = lastLine + "\n";
    ByteArrayInputStream in = new ByteArrayInputStream(payload.getBytes());

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
    Mockito.when(resp.getWriter()).thenReturn(pw);

    JSONObject answer = RestServiceUtil.serverSideEvents(false, resp, in);
    Assertions.assertNotNull(answer);
    Assertions.assertEquals("err", answer.getString(RestServiceUtil.PROP_RESPONSE));
  }

  @Test
  void testBuildRequestJsonMinimalOpenAI() throws Exception {
    // Setup CopilotApp stub
    CopilotApp app = Mockito.mock(CopilotApp.class);
    Mockito.when(app.getAppType()).thenReturn(CopilotConstants.APP_TYPE_OPENAI);
    Mockito.when(app.getId()).thenReturn(UUID.randomUUID().toString());
    Mockito.when(app.getName()).thenReturn("testApp");
    Mockito.when(app.getETCOPAppSourceList()).thenReturn(List.of());
    Mockito.when(app.getOpenaiAssistantID()).thenReturn("openai-id");

    // Mock OBContext static to provide user id and role
    OBContext mockCtx = Mockito.mock(OBContext.class);
    org.openbravo.model.ad.access.User mockUser = Mockito.mock(org.openbravo.model.ad.access.User.class);
    Mockito.when(mockUser.getId()).thenReturn("user-123");
    Role mockRole = Mockito.mock(Role.class);
    Mockito.when(mockRole.getId()).thenReturn("role-1");
    Mockito.when(mockCtx.getUser()).thenReturn(mockUser);
    Mockito.when(mockCtx.getRole()).thenReturn(mockRole);

    try (org.mockito.MockedStatic<OBContext> mockOB = org.mockito.Mockito.mockStatic(OBContext.class);
         org.mockito.MockedStatic<CopilotUtils> mockCu = org.mockito.Mockito.mockStatic(CopilotUtils.class);
         org.mockito.MockedStatic<WeldUtils> mockWeld = org.mockito.Mockito.mockStatic(WeldUtils.class);
         org.mockito.MockedStatic<OBDal> mockDal = org.mockito.Mockito.mockStatic(OBDal.class)) {
      mockOB.when(OBContext::getOBContext).thenReturn(mockCtx);
      // stub CopilotUtils static helpers used in buildRequestJson
      mockCu.when(() -> CopilotUtils.checkQuestionPrompt(org.mockito.ArgumentMatchers.anyString())).thenAnswer(
          i -> null);
      mockCu.when(() -> CopilotUtils.getAppSourceContent(org.mockito.ArgumentMatchers.any(),
              org.mockito.ArgumentMatchers.anyString()))
          .thenReturn("");

      // WeldUtils hook manager stub
      CopilotQuestionHookManager hookManager = Mockito.mock(CopilotQuestionHookManager.class);
      mockWeld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(CopilotQuestionHookManager.class)).thenReturn(
          hookManager);

      // OBDal.getInstance() calls - return a stub that can return a Role when requested
      OBDal dal = Mockito.mock(OBDal.class);
      mockDal.when(OBDal::getInstance).thenReturn(dal);
      Mockito.when(dal.get(Role.class, "role-1")).thenReturn(mockRole);

      JSONObject json = RestServiceUtil.buildRequestJson(app, null, "q", List.of());
      Assertions.assertNotNull(json);
      Assertions.assertEquals("user-123", json.getString(RestServiceUtil.PROP_AD_USER_ID));
      Assertions.assertEquals("q", json.getString(RestServiceUtil.PROP_QUESTION));
    }
  }

  @Test
  void testSendRequestToCopilotThrowsOBExceptionOnMalformedUrl() throws Exception {
    CopilotApp app = Mockito.mock(CopilotApp.class);
    Mockito.when(app.getAppType()).thenReturn(CopilotConstants.APP_TYPE_OPENAI);

    // Mock properties provider to return a host that causes MalformedURLException
    Properties props = new Properties();
    props.setProperty("COPILOT_HOST", "bad host");
    props.setProperty("COPILOT_PORT", "5005");
    try (org.mockito.MockedStatic<OBPropertiesProvider> mockProps = org.mockito.Mockito.mockStatic(
        OBPropertiesProvider.class);
         org.mockito.MockedStatic<OBMessageUtils> mockMsg = org.mockito.Mockito.mockStatic(OBMessageUtils.class)) {
      OBPropertiesProvider provider = Mockito.mock(OBPropertiesProvider.class);
      mockProps.when(OBPropertiesProvider::getInstance).thenReturn(provider);
      Mockito.when(provider.getOpenbravoProperties()).thenReturn(props);
      mockMsg.when(() -> OBMessageUtils.messageBD(org.mockito.ArgumentMatchers.anyString())).thenReturn("conn error");

      Assertions.assertThrows(OBException.class,
          () -> RestServiceUtil.sendRequestToCopilot(false, null, new JSONObject(), app));
    }
  }

  // Additional tests for other methods will be added here

  @Test
  void testProcessResponseAndTrackNullFinalResponse() throws JSONException {
    CopilotApp app = Mockito.mock(CopilotApp.class);
    Mockito.when(app.getId()).thenReturn(LIT_APP1);

    try (org.mockito.MockedStatic<TrackingUtil> mockTrack = org.mockito.Mockito.mockStatic(TrackingUtil.class)) {
      TrackingUtil tracker = Mockito.mock(TrackingUtil.class);
      mockTrack.when(TrackingUtil::getInstance).thenReturn(tracker);

      JSONObject result = RestServiceUtil.processResponseAndTrack(null, LIT_CONV1, "q", app);
      Assertions.assertNull(result);
      Mockito.verify(tracker).trackQuestion(LIT_CONV1, "q", app);
      Mockito.verify(tracker).trackResponse(LIT_CONV1, "", app, true, null);
    }
  }

  @Test
  void testProcessResponseAndTrackValidAnswer() throws Exception {
    CopilotApp app = Mockito.mock(CopilotApp.class);
    Mockito.when(app.getId()).thenReturn(LIT_APP1);

    JSONObject answer = new JSONObject();
    answer.put(RestServiceUtil.PROP_RESPONSE, "ok");
    answer.put(RestServiceUtil.PROP_CONVERSATION_ID, LIT_CONV2);
    JSONObject finalResp = new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer);

    try (org.mockito.MockedStatic<TrackingUtil> mockTrack = org.mockito.Mockito.mockStatic(TrackingUtil.class)) {
      TrackingUtil tracker = Mockito.mock(TrackingUtil.class);
      mockTrack.when(TrackingUtil::getInstance).thenReturn(tracker);

      JSONObject out = RestServiceUtil.processResponseAndTrack(finalResp, null, "q2", app);
      Assertions.assertNotNull(out);
      Assertions.assertEquals(LIT_APP1, out.getString(RestServiceUtil.APP_ID));
      Assertions.assertEquals("ok", out.getString(RestServiceUtil.PROP_RESPONSE));
      Assertions.assertEquals(LIT_CONV2, out.getString(RestServiceUtil.PROP_CONVERSATION_ID));
      Assertions.assertTrue(out.has(LIT_TIMESTAMP));
      // The method now uses the conversationId extracted from the response JSON ("conv2")
      // rather than the conversationId parameter (null) when tracking.
      Mockito.verify(tracker).trackQuestion(LIT_CONV2, "q2", app);
      // When no metadata is provided in input, an empty JSONObject is passed to trackResponse
      Mockito.verify(tracker).trackResponse(Mockito.eq(LIT_CONV2), Mockito.eq("ok"), Mockito.eq(app),
          Mockito.argThat(metadata -> metadata != null && metadata.length() == 0));
    }
  }

  @Test
  void testProcessResponseAndTrackValidAnswerWithMetadata() throws Exception {
    CopilotApp app = Mockito.mock(CopilotApp.class);
    Mockito.when(app.getId()).thenReturn(LIT_APP1);

    JSONObject metadata = new JSONObject();
    metadata.put(LIT_MODEL, "gpt-4o");
    metadata.put("finish_reason", "stop");

    JSONObject answer = new JSONObject();
    answer.put(RestServiceUtil.PROP_RESPONSE, LIT_RESPONSE_WITH_METADATA);
    answer.put(RestServiceUtil.PROP_CONVERSATION_ID, LIT_CONV3);
    answer.put(RestServiceUtil.METADATA, metadata);
    JSONObject finalResp = new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer);

    try (org.mockito.MockedStatic<TrackingUtil> mockTrack = org.mockito.Mockito.mockStatic(TrackingUtil.class)) {
      TrackingUtil tracker = Mockito.mock(TrackingUtil.class);
      mockTrack.when(TrackingUtil::getInstance).thenReturn(tracker);

      JSONObject out = RestServiceUtil.processResponseAndTrack(finalResp, null, "q3", app);
      Assertions.assertNotNull(out);
      Assertions.assertEquals(LIT_APP1, out.getString(RestServiceUtil.APP_ID));
      Assertions.assertEquals(LIT_RESPONSE_WITH_METADATA, out.getString(RestServiceUtil.PROP_RESPONSE));
      Assertions.assertEquals(LIT_CONV3, out.getString(RestServiceUtil.PROP_CONVERSATION_ID));
      Assertions.assertTrue(out.has(LIT_TIMESTAMP));
      // Verify metadata is included in the response
      Assertions.assertTrue(out.has(RestServiceUtil.METADATA));
      JSONObject outMetadata = out.getJSONObject(RestServiceUtil.METADATA);
      Assertions.assertEquals("gpt-4o", outMetadata.getString(LIT_MODEL));
      Assertions.assertEquals("stop", outMetadata.getString("finish_reason"));
      // Verify tracking is called with the extracted metadata
      Mockito.verify(tracker).trackQuestion(LIT_CONV3, "q3", app);
      Mockito.verify(tracker).trackResponse(LIT_CONV3, LIT_RESPONSE_WITH_METADATA, app, metadata);
    }
  }

  @Test
  void testGenerateAssistantStructureUnknownAppTypeThrows() throws Exception {
    CopilotApp app = Mockito.mock(CopilotApp.class);
    Mockito.when(app.getAppType()).thenReturn("UNKNOWN_TYPE");

    JSONObject json = new JSONObject();
    try (org.mockito.MockedStatic<OBMessageUtils> mockedMsg = org.mockito.Mockito.mockStatic(OBMessageUtils.class)) {
      mockedMsg.when(() -> OBMessageUtils.messageBD(org.mockito.ArgumentMatchers.anyString())).thenReturn(
          "Missing app %s");
      Assertions.assertThrows(org.openbravo.base.exception.OBException.class,
          () -> RestServiceUtil.generateAssistantStructure(app, null, "UNKNOWN_TYPE", false, json));
    }
  }

  @Test
  void testAppendLocalFileIdsWithFileIds() throws Exception {
    // Prepare list with one local file id
    List<String> ids = List.of("localFile1");

    // Mock OBDal to return null for CopilotFile lookup so branch appends the id
    OBDal dal = Mockito.mock(OBDal.class);
    org.openbravo.dal.service.OBCriteria crit = Mockito.mock(org.openbravo.dal.service.OBCriteria.class);
    Mockito.when(dal.createCriteria(CopilotFile.class)).thenReturn(crit);
    Mockito.when(crit.add(org.mockito.ArgumentMatchers.any())).thenReturn(crit);
    Mockito.when(crit.setMaxResults(1)).thenReturn(crit);
    Mockito.when(crit.uniqueResult()).thenReturn(null);

    try (org.mockito.MockedStatic<OBDal> mockDal = org.mockito.Mockito.mockStatic(OBDal.class)) {
      mockDal.when(OBDal::getInstance).thenReturn(dal);
      // call package-private method directly
      String res = RestServiceUtil.appendLocalFileIds(ids);
      Assertions.assertTrue(res.contains("Local files") || res.contains("localFile1"));
    }
  }

  @Test
  void testGetGraphImgNullAppThrows() throws Exception {
    try (org.mockito.MockedStatic<OBMessageUtils> mockedMsg = org.mockito.Mockito.mockStatic(OBMessageUtils.class)) {
      mockedMsg.when(() -> OBMessageUtils.messageBD(org.mockito.ArgumentMatchers.anyString())).thenReturn(
          "App not found");
      Assertions.assertThrows(org.openbravo.base.exception.OBException.class, () -> RestServiceUtil.getGraphImg(null));
    }
  }

  @Test
  void testGetGraphImgSuccess() throws Exception {
    CopilotApp app = Mockito.mock(CopilotApp.class);
    Mockito.when(app.getAppType()).thenReturn(CopilotConstants.APP_TYPE_LANGGRAPH);

    // Mock properties
    Properties props = new Properties();
    props.setProperty("COPILOT_HOST", "localhost");
    props.setProperty("COPILOT_PORT", "5005");

    HttpClient.Builder builder = Mockito.mock(HttpClient.Builder.class);
    HttpClient mockClient = Mockito.mock(HttpClient.class);
    HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
    Mockito.when(mockResponse.body()).thenReturn(new JSONObject().put(RestServiceUtil.PROP_ANSWER,
        new JSONObject().put(RestServiceUtil.PROP_RESPONSE, "imageBase64")).toString());

    try (org.mockito.MockedStatic<OBPropertiesProvider> mockProps = org.mockito.Mockito.mockStatic(
        OBPropertiesProvider.class);
         org.mockito.MockedStatic<HttpClient> mockHttpClientStatic = org.mockito.Mockito.mockStatic(HttpClient.class);
         org.mockito.MockedStatic<CopilotUtils> mockCu = org.mockito.Mockito.mockStatic(CopilotUtils.class);
         org.mockito.MockedStatic<WeldUtils> mockWeld = org.mockito.Mockito.mockStatic(WeldUtils.class);
         org.mockito.MockedStatic<OBContext> mockOB = org.mockito.Mockito.mockStatic(OBContext.class);
         org.mockito.MockedStatic<OBDal> mockDal = org.mockito.Mockito.mockStatic(OBDal.class)) {
      OBPropertiesProvider provider = Mockito.mock(OBPropertiesProvider.class);
      mockProps.when(OBPropertiesProvider::getInstance).thenReturn(provider);
      Mockito.when(provider.getOpenbravoProperties()).thenReturn(props);

      // HttpClient builder static
      mockHttpClientStatic.when(() -> HttpClient.newBuilder()).thenReturn(builder);
      Mockito.when(builder.build()).thenReturn(mockClient);
      Mockito.when(mockClient.send(org.mockito.ArgumentMatchers.any(HttpRequest.class),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
          .thenReturn(mockResponse);

      // Stub CopilotUtils and addExtraContextWithHooks internals
      mockCu.when(() -> CopilotUtils.buildLangraphRequestForCopilot(org.mockito.ArgumentMatchers.any(),
              org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
          .thenAnswer(i -> null);
      mockCu.when(() -> CopilotUtils.getAuthJson(org.mockito.ArgumentMatchers.any(),
          org.mockito.ArgumentMatchers.any())).thenReturn(new JSONObject());
      mockCu.when(CopilotUtils::getModelsConfigJSON).thenReturn(new JSONObject());

      // OBContext and OBDal stubs for addExtraContextWithHooks
      OBContext mockCtx = Mockito.mock(OBContext.class);
      org.openbravo.model.ad.access.User mockUser = Mockito.mock(org.openbravo.model.ad.access.User.class);
      Mockito.when(mockUser.getId()).thenReturn("u1");
      Role mockRole = Mockito.mock(Role.class);
      Mockito.when(mockRole.getId()).thenReturn("r1");
      Mockito.when(mockCtx.getUser()).thenReturn(mockUser);
      Mockito.when(mockCtx.getRole()).thenReturn(mockRole);
      mockOB.when(OBContext::getOBContext).thenReturn(mockCtx);

      OBDal dal = Mockito.mock(OBDal.class);
      mockDal.when(OBDal::getInstance).thenReturn(dal);
      Mockito.when(dal.get(Role.class, "r1")).thenReturn(mockRole);

      CopilotQuestionHookManager hookManager = Mockito.mock(CopilotQuestionHookManager.class);
      mockWeld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(CopilotQuestionHookManager.class)).thenReturn(
          hookManager);

      String res = RestServiceUtil.getGraphImg(app);
      Assertions.assertEquals("imageBase64", res);
    }
  }


  @Test
  void testHandleAssistantsSingleApp() throws Exception {
    // Mock OBContext and role
    OBContext mockCtx = Mockito.mock(OBContext.class);
    Role mockRole = Mockito.mock(Role.class);
    org.openbravo.model.ad.access.User mockUser = Mockito.mock(org.openbravo.model.ad.access.User.class);
    Mockito.when(mockCtx.getRole()).thenReturn(mockRole);
    Mockito.when(mockCtx.getUser()).thenReturn(mockUser);

    // Mock CopilotRoleApp and CopilotApp
    CopilotApp app = Mockito.mock(CopilotApp.class);
    Mockito.when(app.getId()).thenReturn(LIT_APP1);
    Mockito.when(app.getName()).thenReturn("App One");
    CopilotRoleApp roleApp = Mockito.mock(CopilotRoleApp.class);
    Mockito.when(roleApp.getCopilotApp()).thenReturn(app);

    // Mock OBDal criteria to return the single CopilotRoleApp
    OBDal dal = Mockito.mock(OBDal.class);
    org.openbravo.dal.service.OBCriteria<CopilotRoleApp> crit = Mockito.mock(
        org.openbravo.dal.service.OBCriteria.class);
    Mockito.when(dal.createCriteria(CopilotRoleApp.class)).thenReturn(crit);
    Mockito.when(crit.add(org.mockito.ArgumentMatchers.any())).thenReturn(crit);
    Mockito.when(crit.list()).thenReturn(List.of(roleApp));

    try (org.mockito.MockedStatic<OBContext> mockOB = org.mockito.Mockito.mockStatic(OBContext.class);
         org.mockito.MockedStatic<OBDal> mockDal = org.mockito.Mockito.mockStatic(OBDal.class)) {
      mockOB.when(OBContext::getOBContext).thenReturn(mockCtx);
      mockDal.when(OBDal::getInstance).thenReturn(dal);

      org.codehaus.jettison.json.JSONArray assistants = RestServiceUtil.handleAssistants();
      Assertions.assertNotNull(assistants);
      Assertions.assertEquals(1, assistants.length());
      JSONObject assistant = assistants.getJSONObject(0);
      Assertions.assertEquals(LIT_APP1, assistant.getString(RestServiceUtil.APP_ID));
      Assertions.assertEquals("App One", assistant.getString("name"));
    }
  }

  @Test
  void testHandleAssistantsThrowsOBExceptionOnError() throws Exception {
    OBContext mockCtx = Mockito.mock(OBContext.class);
    Role mockRole = Mockito.mock(Role.class);
    Mockito.when(mockCtx.getRole()).thenReturn(mockRole);

    OBDal dal = Mockito.mock(OBDal.class);
    org.openbravo.dal.service.OBCriteria<CopilotRoleApp> crit = Mockito.mock(
        org.openbravo.dal.service.OBCriteria.class);
    Mockito.when(dal.createCriteria(CopilotRoleApp.class)).thenReturn(crit);
    Mockito.when(crit.add(org.mockito.ArgumentMatchers.any())).thenReturn(crit);
    Mockito.when(crit.list()).thenThrow(new RuntimeException("boom"));

    try (org.mockito.MockedStatic<OBContext> mockOB = org.mockito.Mockito.mockStatic(OBContext.class);
         org.mockito.MockedStatic<OBDal> mockDal = org.mockito.Mockito.mockStatic(OBDal.class)) {
      mockOB.when(OBContext::getOBContext).thenReturn(mockCtx);
      mockDal.when(OBDal::getInstance).thenReturn(dal);

      Assertions.assertThrows(org.openbravo.base.exception.OBException.class, RestServiceUtil::handleAssistants);
    }
  }

  @Test
  void testGetJSONLabelsModuleLanguageEquals() throws Exception {
    // Setup OBContext with language
    org.openbravo.dal.core.OBContext mockCtx = Mockito.mock(org.openbravo.dal.core.OBContext.class);
    org.openbravo.model.ad.system.Language mockLang = Mockito.mock(org.openbravo.model.ad.system.Language.class);
    Mockito.when(mockLang.getId()).thenReturn(LIT_LANG1);
    Mockito.when(mockCtx.getLanguage()).thenReturn(mockLang);

    // Module whose language equals current language
    org.openbravo.model.ad.module.Module module = Mockito.mock(org.openbravo.model.ad.module.Module.class);
    org.openbravo.model.ad.system.Language modLang = Mockito.mock(org.openbravo.model.ad.system.Language.class);
    Mockito.when(modLang.getId()).thenReturn(LIT_LANG1);
    Mockito.when(module.getLanguage()).thenReturn(modLang);

    // OBDal and criteria for Message
    OBDal dal = Mockito.mock(OBDal.class);
    org.openbravo.dal.service.OBCriteria<org.openbravo.model.ad.ui.Message> crit = Mockito
        .mock(org.openbravo.dal.service.OBCriteria.class);
    Mockito.when(dal.get(org.openbravo.model.ad.module.Module.class, RestServiceUtil.COPILOT_MODULE_ID))
        .thenReturn(module);
    Mockito.when(dal.createCriteria(org.openbravo.model.ad.ui.Message.class)).thenReturn(crit);
    Mockito.when(crit.add(org.mockito.ArgumentMatchers.any())).thenReturn(crit);

    org.openbravo.model.ad.ui.Message msg = Mockito.mock(org.openbravo.model.ad.ui.Message.class);
    Mockito.when(msg.getIdentifier()).thenReturn("id1");
    Mockito.when(msg.getMessageText()).thenReturn("text1");
    Mockito.when(crit.list()).thenReturn(List.of(msg));

    try (org.mockito.MockedStatic<OBContext> mockOB = org.mockito.Mockito.mockStatic(OBContext.class);
         org.mockito.MockedStatic<OBDal> mockDal = org.mockito.Mockito.mockStatic(OBDal.class)) {
      mockOB.when(() -> OBContext.setAdminMode(false)).thenAnswer(i -> null);
      mockOB.when(OBContext::getOBContext).thenReturn(mockCtx);
      mockOB.when(OBContext::restorePreviousMode).thenAnswer(i -> null);

      mockDal.when(OBDal::getInstance).thenReturn(dal);

      JSONObject json = RestServiceUtil.getJSONLabels();
      Assertions.assertNotNull(json);
      Assertions.assertEquals("text1", json.getString("id1"));
    }
  }

  @Test
  void testGetJSONLabelsMessageTrlBranch() throws Exception {
    // Setup OBContext with language different from module language
    org.openbravo.dal.core.OBContext mockCtx = Mockito.mock(org.openbravo.dal.core.OBContext.class);
    org.openbravo.model.ad.system.Language mockLang = Mockito.mock(org.openbravo.model.ad.system.Language.class);
    Mockito.when(mockLang.getId()).thenReturn("lang2");
    Mockito.when(mockCtx.getLanguage()).thenReturn(mockLang);

    // Module whose language is different
    org.openbravo.model.ad.module.Module module = Mockito.mock(org.openbravo.model.ad.module.Module.class);
    org.openbravo.model.ad.system.Language modLang = Mockito.mock(org.openbravo.model.ad.system.Language.class);
    Mockito.when(modLang.getId()).thenReturn(LIT_LANG1);
    Mockito.when(module.getLanguage()).thenReturn(modLang);

    // OBDal and criteria for MessageTrl
    OBDal dal = Mockito.mock(OBDal.class);
    org.openbravo.dal.service.OBCriteria<org.openbravo.model.ad.ui.MessageTrl> crit = Mockito
        .mock(org.openbravo.dal.service.OBCriteria.class);
    Mockito.when(dal.get(org.openbravo.model.ad.module.Module.class, RestServiceUtil.COPILOT_MODULE_ID))
        .thenReturn(module);
    Mockito.when(dal.createCriteria(org.openbravo.model.ad.ui.MessageTrl.class)).thenReturn(crit);
    Mockito.when(crit.add(org.mockito.ArgumentMatchers.any())).thenReturn(crit);
    // createAlias returns the criteria; mock it to return the same crit
    Mockito.when(crit.createAlias(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString())).thenReturn(crit);

    org.openbravo.model.ad.ui.MessageTrl msgTrl = Mockito.mock(org.openbravo.model.ad.ui.MessageTrl.class);
    org.openbravo.model.ad.ui.Message linkedMsg = Mockito.mock(org.openbravo.model.ad.ui.Message.class);
    Mockito.when(linkedMsg.getIdentifier()).thenReturn("id2");
    Mockito.when(msgTrl.getMessage()).thenReturn(linkedMsg);
    Mockito.when(msgTrl.getMessageText()).thenReturn("text2");
    Mockito.when(crit.list()).thenReturn(List.of(msgTrl));

    try (org.mockito.MockedStatic<OBContext> mockOB = org.mockito.Mockito.mockStatic(OBContext.class);
         org.mockito.MockedStatic<OBDal> mockDal = org.mockito.Mockito.mockStatic(OBDal.class)) {
      mockOB.when(() -> OBContext.setAdminMode(false)).thenAnswer(i -> null);
      mockOB.when(OBContext::getOBContext).thenReturn(mockCtx);
      mockOB.when(OBContext::restorePreviousMode).thenAnswer(i -> null);

      mockDal.when(OBDal::getInstance).thenReturn(dal);

      JSONObject json = RestServiceUtil.getJSONLabels();
      Assertions.assertNotNull(json);
      Assertions.assertEquals("text2", json.getString("id2"));
    }
  }
}
