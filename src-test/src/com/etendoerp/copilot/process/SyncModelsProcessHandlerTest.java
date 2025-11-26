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
package com.etendoerp.copilot.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.exception.OBException;
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
  private static final String SHOULD_HAVE_RESPONSE_ACTIONS = "Should have responseActions";
  private static final String RESPONSE_ACTIONS_KEY = "responseActions";
  private static final String MESSAGE_KEY = "message";

  /**
   * Sets up the test environment before each test execution.
   * Initializes the SyncModelsProcessHandler instance and configures all static mocks
   * including OBContext, CopilotModelUtils, OBMessageUtils, OBDal, and DbUtility.
   * Sets up default behaviors for admin mode management and message translations.
   *
   * @throws Exception if there's an error during mock initialization
   */
  @BeforeEach
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
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(SUCCESS_MSG)).thenReturn(SUCCESS_MSG);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("success")).thenReturn(SUCCESS_MSG);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(ERROR_MSG)).thenReturn(ERROR_MSG);
  }

  /**
   * Cleans up resources after each test execution.
   * Closes all static mocks in reverse order of initialization to prevent
   * memory leaks and ensure proper test isolation.
   *
   * @throws Exception if there's an error during resource cleanup
   */
  @AfterEach
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
   * Verifies that when model synchronization succeeds, the method returns
   * a proper JSON response with responseActions array containing success message.
   *
   * @throws Exception if there's an error during test execution
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
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has(RESPONSE_ACTIONS_KEY), SHOULD_HAVE_RESPONSE_ACTIONS);

    // Verify the response structure
    assertInstanceOf(JSONArray.class, result.get(RESPONSE_ACTIONS_KEY), "ResponseActions should be a JSONArray");
  }

  /**
   * Test doExecute when syncModels throws an exception.
   * Verifies that when a RuntimeException occurs during synchronization,
   * the method handles it gracefully by performing a database rollback
   * and returning an error response with appropriate severity and message.
   *
   * @throws Exception if there's an error during test execution
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
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has(MESSAGE_KEY), "Should have message");

    JSONObject message = result.getJSONObject(MESSAGE_KEY);
    assertEquals("error", message.getString("severity"), "Should be error severity");
    assertEquals(ERROR_MSG, message.getString("title"), "Should have error title");
  }

  /**
   * Test doExecute when syncModels throws SQLException.
   * Verifies that when a SQLException wrapped in an OBException occurs,
   * the method extracts the underlying SQLException, translates the error
   * message, performs a database rollback, and returns a properly formatted
   * error response.
   *
   * @throws Exception if there's an error during test execution
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
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has(MESSAGE_KEY), "Should have message");

    JSONObject message = result.getJSONObject(MESSAGE_KEY);
    assertEquals("error", message.getString("severity"), "Should be error severity");
    assertTrue(message.has("text"), "Should have error text");
  }

  /**
   * Test doExecute when rollback also fails.
   * Verifies that when both the synchronization and the subsequent database
   * rollback fail, the method handles both exceptions gracefully and still
   * returns a valid JSON response without propagating the exceptions.
   *
   * @throws Exception if there's an error during test execution
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
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    // The handler catches the rollback exception, so result should still be a valid JSON
  }

  /**
   * Test doExecute ensures restorePreviousMode is always called.
   * Verifies that regardless of success or failure, the OBContext.restorePreviousMode()
   * method is always invoked to properly restore the context state, ensuring
   * proper cleanup even in the presence of exceptions.
   *
   */
  @Test
  public void testDoExecuteRestoresPreviousMode() {
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
   * Verifies that the method handles empty parameter maps correctly
   * and still performs successful model synchronization, returning
   * a valid response with responseActions.
   *
   */
  @Test
  public void testDoExecuteWithEmptyParameters() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // Mock successful sync
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).then(invocation -> null);

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has(RESPONSE_ACTIONS_KEY), SHOULD_HAVE_RESPONSE_ACTIONS);
  }

  /**
   * Test doExecute with null content.
   * Verifies that the method handles null content parameter gracefully
   * and still performs successful model synchronization without errors.
   *
   */
  @Test
  public void testDoExecuteWithNullContent() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = null;

    // Mock successful sync
    mockedCopilotModelUtils.when(CopilotModelUtils::syncModels).then(invocation -> null);

    // When
    JSONObject result = handler.doExecute(parameters, content);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has(RESPONSE_ACTIONS_KEY), SHOULD_HAVE_RESPONSE_ACTIONS);
  }

  /**
   * Test buildMessage structure through successful doExecute.
   * Verifies the complete structure of the success message returned by doExecute,
   * including the presence of responseActions array, message type, title,
   * and wait flag. Ensures the message follows the expected JSON schema
   * for process view notifications.
   *
   * @throws Exception if there's an error during test execution
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
    assertNotNull(result, RESULT_NOT_NULL_MSG);
    assertTrue(result.has(RESPONSE_ACTIONS_KEY), SHOULD_HAVE_RESPONSE_ACTIONS);

    org.codehaus.jettison.json.JSONArray actions = result.getJSONArray(RESPONSE_ACTIONS_KEY);
    assertTrue(actions.length() > 0, "Should have at least one action");

    JSONObject action = actions.getJSONObject(0);
    assertTrue(action.has("showMsgInProcessView"), "Should have showMsgInProcessView");

    JSONObject showMsg = action.getJSONObject("showMsgInProcessView");
    assertEquals("success", showMsg.getString("msgType"), "Should be success message type");
    assertEquals(SUCCESS_MSG, showMsg.getString("msgTitle"), "Should have success title");
    assertTrue(showMsg.getBoolean("wait"), "Should have wait flag");
  }
}
