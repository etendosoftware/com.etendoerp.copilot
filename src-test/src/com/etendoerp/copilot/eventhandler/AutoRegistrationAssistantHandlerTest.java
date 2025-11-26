package com.etendoerp.copilot.eventhandler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
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

  /**
   * The Expected exception.
   */
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

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

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception if setup fails
   */
  @Before
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
  @After
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
   * @throws Exception if test fails
   */
  @Test
  public void testExecuteSuccessfulNewRegistration() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = String.format("{\"appId\":\"%s\"}", TEST_APP_ID);

    // No existing role app found
    when(mockCriteria.uniqueResult()).thenReturn(null);

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull("Result should not be null", result);
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
   * @throws Exception if test fails
   */
  @Test
  public void testExecuteRoleAppAlreadyExists() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = String.format("{\"appId\":\"%s\"}", TEST_APP_ID);

    // Existing role app found
    when(mockCriteria.uniqueResult()).thenReturn(mockExistingRoleApp);

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull("Result should not be null", result);
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
   * @throws Exception if test fails
   */
  @Test
  public void testExecuteWithInvalidJSON() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "invalid json";

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull("Result should not be null", result);
    assertFalse("Result should indicate failure", result.optBoolean("success", true));
    assertTrue("Result should contain error message",
        result.optString("message", "").contains("Error:"));
  }

  /**
   * Test execute with missing appId in content.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testExecuteWithMissingAppId() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "{}";

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull("Result should not be null", result);
    assertFalse("Result should indicate failure", result.optBoolean("success", true));
    assertTrue("Result should contain error message",
        result.optString("message", "").contains("Error:"));
  }
  /**
   * Test execute verifies criteria filters are correct.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testExecuteVerifiesCriteriaFilters() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = String.format("{\"appId\":\"%s\"}", TEST_APP_ID);

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
   * @throws Exception if test fails
   */
  @Test
  public void testExecuteWithEmptyContent() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = "";

    // When
    JSONObject result = handler.execute(parameters, content);

    // Then
    assertNotNull("Result should not be null", result);
    assertFalse("Result should indicate failure", result.optBoolean("success", true));
    assertTrue("Result should contain error message",
        result.optString("message", "").contains("Error:"));
  }

  /**
   * Test execute verifies role from context.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testExecuteVerifiesRoleFromContext() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    String content = String.format("{\"appId\":\"%s\"}", TEST_APP_ID);

    when(mockCriteria.uniqueResult()).thenReturn(null);

    // When
    handler.execute(parameters, content);

    // Then
    mockedOBContext.verify(OBContext::getOBContext, times(1));
    verify(obContext, times(1)).getRole();
    verify(mockNewRoleApp, times(1)).setRole(mockRole);
  }
}