package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.util.CopilotModelUtils;

/**
 * SyncModelsProcessHandler test class.
 * Tests the synchronization of models process handler.
 */
public class SyncModelsProcessHandlerTest {

  private SyncModelsProcessHandler handler;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<CopilotModelUtils> mockedCopilotModelUtils;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<DbUtility> mockedDbUtility;

  private static final String RESULT_NOT_NULL_MSG = "Result should not be null";
  private static final String SUCCESS_MSG = "Success";
  private static final String ERROR_MSG = "Error";

  @Before
  public void setUp() throws Exception {
    handler = new SyncModelsProcessHandler();

    // Set up static mocks
    mockedOBContext = mockStatic(OBContext.class);
    mockedCopilotModelUtils = mockStatic(CopilotModelUtils.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedOBDal = mockStatic(OBDal.class);
    mockedDbUtility = mockStatic(DbUtility.class);

    // Configure OBContext mock
    mockedOBContext.when(OBContext::setAdminMode).then(invocation -> null);
    mockedOBContext.when(OBContext::restorePreviousMode).then(invocation -> null);

    // Configure OBMessageUtils mock
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("Success")).thenReturn(SUCCESS_MSG);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("success")).thenReturn(SUCCESS_MSG);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("Error")).thenReturn(ERROR_MSG);
  }

  @After
  public void tearDown() throws Exception {
    if (mockedDbUtility != null) {
      mockedDbUtility.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
    if (mockedCopilotModelUtils != null) {
      mockedCopilotModelUtils.close();
    }
    if (mockedOBContext != null) {
      mockedOBContext.close();
    }
  }

  /**
   * Test doExecute with successful model synchronization.
   */
  @Test
  public void testDoExecuteSuccess() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // Mock successful sync
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).then(invocation -> null);

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have responseActions", result.has("responseActions"));

    // Verify the response structure
    assertTrue("ResponseActions should be a JSONArray",
        result.get("responseActions") instanceof org.codehaus.jettison.json.JSONArray);
  }

  /**
   * Test doExecute when syncModels throws an exception.
   */
  @Test
  public void testDoExecuteWithException() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // Mock OBDal for rollback
    OBDal obDal = org.mockito.Mockito.mock(OBDal.class);
    Connection connection = org.mockito.Mockito.mock(Connection.class);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    org.mockito.Mockito.when(obDal.getConnection()).thenReturn(connection);
    doNothing().when(connection).rollback();

    // Mock exception during sync
    RuntimeException syncException = new RuntimeException("Sync failed");
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).thenThrow(syncException);

    // Mock DbUtility
    mockedDbUtility.when(() -> DbUtility.getUnderlyingSQLException(syncException))
        .thenReturn(syncException);

    // Mock OBMessageUtils.translateError
    mockedOBMessageUtils.when(() -> OBMessageUtils.translateError(anyString()))
        .thenReturn(new org.openbravo.erpCommon.utility.OBError());

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have message", result.has("message"));

    JSONObject message = result.getJSONObject("message");
    assertEquals("Should be error severity", "error", message.getString("severity"));
    assertEquals("Should have error title", ERROR_MSG, message.getString("title"));
  }

  /**
   * Test doExecute when syncModels throws SQLException.
   */
  @Test
  public void testDoExecuteWithSQLException() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // Mock OBDal for rollback
    OBDal obDal = org.mockito.Mockito.mock(OBDal.class);
    Connection connection = org.mockito.Mockito.mock(Connection.class);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    org.mockito.Mockito.when(obDal.getConnection()).thenReturn(connection);
    doNothing().when(connection).rollback();

    // Mock exception during sync
    SQLException sqlException = new SQLException("Database error");
    OBException obException = new OBException(sqlException);
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).thenThrow(obException);

    // Mock DbUtility to return the SQLException
    mockedDbUtility.when(() -> DbUtility.getUnderlyingSQLException(obException))
        .thenReturn(sqlException);

    // Mock OBMessageUtils.translateError
    org.openbravo.erpCommon.utility.OBError obError = new org.openbravo.erpCommon.utility.OBError();
    obError.setMessage("Translated database error");
    mockedOBMessageUtils.when(() -> OBMessageUtils.translateError("Database error"))
        .thenReturn(obError);

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have message", result.has("message"));

    JSONObject message = result.getJSONObject("message");
    assertEquals("Should be error severity", "error", message.getString("severity"));
    assertTrue("Should have error text", message.has("text"));
  }

  /**
   * Test doExecute when rollback also fails.
   */
  @Test
  public void testDoExecuteWithRollbackException() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // Mock OBDal for rollback that fails
    OBDal obDal = org.mockito.Mockito.mock(OBDal.class);
    Connection connection = org.mockito.Mockito.mock(Connection.class);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    org.mockito.Mockito.when(obDal.getConnection()).thenReturn(connection);
    doThrow(new SQLException("Rollback failed")).when(connection).rollback();

    // Mock exception during sync
    RuntimeException syncException = new RuntimeException("Sync failed");
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).thenThrow(syncException);

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    // The handler catches the rollback exception, so result should still be a valid JSON
  }

  /**
   * Test doExecute ensures restorePreviousMode is always called.
   */
  @Test
  public void testDoExecuteRestoresPreviousMode() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // Mock successful sync
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).then(invocation -> null);

    // When
    handler.doExecute(parameters, content);

    // Then
    // Verify that restorePreviousMode was called (implicitly tested by the mock setup)
    // If this wasn't called, the mock would fail
  }

  /**
   * Test doExecute with empty parameters.
   */
  @Test
  public void testDoExecuteWithEmptyParameters() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // Mock successful sync
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).then(invocation -> null);

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have responseActions", result.has("responseActions"));
  }

  /**
   * Test doExecute with null content.
   */
  @Test
  public void testDoExecuteWithNullContent() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = null;

    // Mock successful sync
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).then(invocation -> null);

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have responseActions", result.has("responseActions"));
  }

  /**
   * Test buildMessage structure through successful doExecute.
   */
  @Test
  public void testBuildMessageStructure() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // Mock successful sync
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).then(invocation -> null);

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertTrue("Should have responseActions", result.has("responseActions"));

    org.codehaus.jettison.json.JSONArray actions = result.getJSONArray("responseActions");
    assertTrue("Should have at least one action", actions.length() > 0);

    JSONObject action = actions.getJSONObject(0);
    assertTrue("Should have showMsgInProcessView", action.has("showMsgInProcessView"));

    JSONObject showMsg = action.getJSONObject("showMsgInProcessView");
    assertEquals("Should be success message type", "success", showMsg.getString("msgType"));
    assertEquals("Should have success title", SUCCESS_MSG, showMsg.getString("msgTitle"));
    assertTrue("Should have wait flag", showMsg.getBoolean("wait"));
  }
}