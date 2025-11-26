package com.etendoerp.copilot.eventhandler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;

/**
 * AutoRegistrationAssistantHandler test class.
 */
public class AutoRegistrationAssistantHandlerTest {

  private AutoRegistrationAssistantHandler handler;
  private AutoCloseable mocks;

  @Mock
  private OBDal obDal;
  @Mock
  private OBContext obContext;
  @Mock
  private Role mockRole;
  @Mock
  private CopilotApp mockApp;
  @Mock
  private OBCriteria<CopilotRoleApp> mockCriteria;
  @Mock
  private CopilotRoleApp mockExistingRoleApp;
  @Mock
  private CopilotRoleApp mockNewRoleApp;
  @Mock
  private OBProvider obProvider;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBProvider> mockedOBProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  private static final String TEST_APP_ID = "testAppId123";
  private static final String TEST_ROLE_ID = "testRoleId123";
  private static final String TEST_APP_NAME = "Test Copilot App";
  private static final String ERROR_ROLE_NOT_ADDED = "Role could not be added";
  private static final String RESULT_NOT_NULL_MESSAGE = "Result should not be null";
  private static final String APP_ID_JSON_FORMAT = "{\"appId\":\"%s\"}";
  private static final String SUCCESS_KEY = "success";
  private static final String MESSAGE_KEY = "message";
  private static final String RESULT_SHOULD_INDICATE_FAILURE = "Result should indicate failure";
  private static final String RESULT_SHOULD_CONTAIN_ERROR_MESSAGE = "Result should contain error message";
  private static final String ERROR_PREFIX = "Error:";

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception if setup fails
   */
  @BeforeEach
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    handler = new AutoRegistrationAssistantHandler();

    // Set up static mocks
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBProvider = mockStatic(OBProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    // Configure OBDal mock
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.get(CopilotApp.class, TEST_APP_ID)).thenReturn(mockApp);
    when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(mockCriteria);
    doNothing().when(obDal).save(any(CopilotRoleApp.class));
    doNothing().when(obDal).flush();

    // Configure OBContext mock
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getRole()).thenReturn(mockRole);

    // Configure OBProvider mock
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(obProvider);
    when(obProvider.get(CopilotRoleApp.class)).thenReturn(mockNewRoleApp);

    // Configure CopilotApp mock
    when(mockApp.getId()).thenReturn(TEST_APP_ID);
    when(mockApp.getName()).thenReturn(TEST_APP_NAME);

    // Configure Role mock
    when(mockRole.getId()).thenReturn(TEST_ROLE_ID);

    // Configure OBCriteria mock
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);

    // Configure new CopilotRoleApp mock
    doNothing().when(mockNewRoleApp).setCopilotApp(any(CopilotApp.class));
    doNothing().when(mockNewRoleApp).setRole(any(Role.class));

    // Configure OBMessageUtils mock
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_RoleNotAdded"))
        .thenReturn(ERROR_ROLE_NOT_ADDED);
  }

  /**
   * Tears down the test environment after each test.
   *
   * @throws Exception if teardown fails
   */
  @AfterEach
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mockedOBContext != null) {
      mockedOBContext.close();
    }
    if (mockedOBProvider != null) {
      mockedOBProvider.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
  }

  /**
   * Test execute with successful new role app registration.
   *
   */
  @Test
  public void testExecuteSuccessfulNewRegistration() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = String.format(APP_ID_JSON_FORMAT, TEST_APP_ID);

    // No existing role app found
    when(mockCriteria.uniqueResult()).thenReturn(null);

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MESSAGE);
    verify(obDal, times(1)).get(CopilotApp.class, TEST_APP_ID);
    verify(mockCriteria, times(2)).add(any());
    verify(mockCriteria, times(1)).setMaxResults(1);
    verify(mockCriteria, times(1)).uniqueResult();

    // Verify new CopilotRoleApp was created and saved
    verify(obProvider, times(1)).get(CopilotRoleApp.class);
    verify(mockNewRoleApp, times(1)).setCopilotApp(mockApp);
    verify(mockNewRoleApp, times(1)).setRole(mockRole);
    verify(obDal, times(1)).save(mockNewRoleApp);
    verify(obDal, times(1)).flush();
  }

  /**
   * Test execute when role app already exists.
   *
   */
  @Test
  public void testExecuteRoleAppAlreadyExists() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = String.format(APP_ID_JSON_FORMAT, TEST_APP_ID);

    // Existing role app found
    when(mockCriteria.uniqueResult()).thenReturn(mockExistingRoleApp);

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MESSAGE);
    verify(obDal, times(1)).get(CopilotApp.class, TEST_APP_ID);
    verify(mockCriteria, times(1)).uniqueResult();

    // Verify no new CopilotRoleApp was created
    verify(obProvider, times(0)).get(CopilotRoleApp.class);
    verify(obDal, times(0)).save(any(CopilotRoleApp.class));
    verify(obDal, times(0)).flush();
  }

  /**
   * Test execute with invalid JSON content.
   *
   */
  @Test
  public void testExecuteWithInvalidJSON() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "invalid json";

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MESSAGE);
    assertFalse(result.optBoolean(SUCCESS_KEY, true), RESULT_SHOULD_INDICATE_FAILURE);
    assertTrue(result.optString(MESSAGE_KEY, "").contains(ERROR_PREFIX),
        RESULT_SHOULD_CONTAIN_ERROR_MESSAGE);
  }

  /**
   * Test execute with missing appId in content.
   *
   */
  @Test
  public void testExecuteWithMissingAppId() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "{}";

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MESSAGE);
    assertFalse(result.optBoolean(SUCCESS_KEY, true), RESULT_SHOULD_INDICATE_FAILURE);
    assertTrue(result.optString(MESSAGE_KEY, "").contains(ERROR_PREFIX),
        RESULT_SHOULD_CONTAIN_ERROR_MESSAGE);
  }
  /**
   * Test execute verifies criteria filters are correct.
   *
   */
  @Test
  public void testExecuteVerifiesCriteriaFilters() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = String.format(APP_ID_JSON_FORMAT, TEST_APP_ID);

    when(mockCriteria.uniqueResult()).thenReturn(null);

    // When
    handler.execute(parameters, content);

    // Then
    // Verify criteria was configured with correct restrictions
    verify(mockCriteria, times(2)).add(any());
    verify(mockCriteria, times(1)).setMaxResults(1);
    verify(obDal, times(1)).createCriteria(CopilotRoleApp.class);
  }

  /**
   * Test execute with empty content.
   *
   */
  @Test
  public void testExecuteWithEmptyContent() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull(result, RESULT_NOT_NULL_MESSAGE);
    assertFalse(result.optBoolean(SUCCESS_KEY, true), RESULT_SHOULD_INDICATE_FAILURE);
    assertTrue(result.optString(MESSAGE_KEY, "").contains(ERROR_PREFIX),
        RESULT_SHOULD_CONTAIN_ERROR_MESSAGE);
  }

  /**
   * Test execute verifies role from context.
   *
   */
  @Test
  public void testExecuteVerifiesRoleFromContext() {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = String.format(APP_ID_JSON_FORMAT, TEST_APP_ID);

    when(mockCriteria.uniqueResult()).thenReturn(null);

    // When
    handler.execute(parameters, content);

    // Then
    mockedOBContext.verify(OBContext::getOBContext, times(1));
    verify(obContext, times(1)).getRole();
    verify(mockNewRoleApp, times(1)).setRole(mockRole);
  }
}
