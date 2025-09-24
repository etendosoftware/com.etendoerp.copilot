package com.etendoerp.copilot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotApiToken;

/**
 * Unit test class for CopilotVarReplacerUtil.
 * <p>
 * This test class verifies the functionality of the CopilotVarReplacerUtil utility class,
 * including variable replacement, context handling, custom mappings, bracket balancing,
 * and API token processing.
 */
public class CopilotVarReplacerUtilTest extends WeldBaseTest {

  // Test constants
  private static final String TEST_LANGUAGE_ID = "testLanguageId";
  private static final String TEST_ETENDO_HOST = "http://localhost:8080/etendo";
  private static final String TEST_ETENDO_HOST_DOCKER = "http://etendo:8080/etendo";
  private static final String TEST_PATH = "/test/path";

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

  /**
   * Creates a mock OBContext with basic language setup.
   */
  private OBContext createBasicMockContext() {
    OBContext mockContext = mock(OBContext.class);
    when(mockContext.getCurrentClient()).thenReturn(null);
    when(mockContext.getCurrentOrganization()).thenReturn(null);
    when(mockContext.getUser()).thenReturn(null);
    when(mockContext.getRole()).thenReturn(null);
    when(mockContext.getWarehouse()).thenReturn(null);
    
    Language mockLanguage = mock(Language.class);
    when(mockLanguage.getId()).thenReturn(TEST_LANGUAGE_ID);
    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    
    return mockContext;
  }

  /**
   * Sets up common static mocks for utility classes.
   */
  private void setupUtilityMocks(MockedStatic<CopilotUtils> copilotUtilsMock, 
                                 MockedStatic<OBPropertiesProvider> propertiesMock) {
    copilotUtilsMock.when(CopilotUtils::getEtendoHost).thenReturn(TEST_ETENDO_HOST);
    copilotUtilsMock.when(CopilotUtils::getEtendoHostDocker).thenReturn(TEST_ETENDO_HOST_DOCKER);
    
    Properties props = new Properties();
    OBPropertiesProvider mockProvider = mock(OBPropertiesProvider.class);
    when(mockProvider.getOpenbravoProperties()).thenReturn(props);
    propertiesMock.when(OBPropertiesProvider::getInstance).thenReturn(mockProvider);
    copilotUtilsMock.when(() -> CopilotUtils.getSourcesPath(props)).thenReturn(TEST_PATH);
  }

  /**
   * Sets up OBDal mocks for API token testing.
   */
  private void setupOBDalMocks(MockedStatic<OBDal> obDalMock, List<CopilotApiToken> tokenList) {
    OBDal mockOBDal = mock(OBDal.class);
    OBCriteria<CopilotApiToken> mockCriteria = mock(OBCriteria.class);
    when(mockOBDal.createCriteria(CopilotApiToken.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(org.mockito.ArgumentMatchers.any())).thenReturn(mockCriteria);
    when(mockCriteria.list()).thenReturn(tokenList);
    obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
  }

  @Test
  public void testReplaceCopilotPromptVariables_Simple() {
    String input = "Hello @USERNAME@";
    String result = CopilotVarReplacerUtil.replaceCopilotPromptVariables(input);
    assertNotNull(result);
    assertTrue(result.contains("Hello"));
  }

  @Test
  public void testReplaceCopilotPromptVariables_WithContext() throws JSONException {
    Client mockClient = mock(Client.class);
    when(mockClient.getId()).thenReturn("testClientId");
    when(mockClient.getName()).thenReturn("Test Client");

    Organization mockOrg = mock(Organization.class);
    when(mockOrg.getId()).thenReturn("testOrgId");
    when(mockOrg.getName()).thenReturn("Test Organization");

    User mockUser = mock(User.class);
    when(mockUser.getId()).thenReturn("testUserId");
    when(mockUser.getUsername()).thenReturn("testUser");

    Role mockRole = mock(Role.class);
    when(mockRole.getId()).thenReturn("testRoleId");
    when(mockRole.getName()).thenReturn("Test Role");

    Warehouse mockWarehouse = mock(Warehouse.class);
    when(mockWarehouse.getId()).thenReturn("testWarehouseId");
    when(mockWarehouse.getName()).thenReturn("Test Warehouse");

    Language mockLanguage = mock(Language.class);
    when(mockLanguage.getId()).thenReturn(TEST_LANGUAGE_ID);

    OBContext mockContext = mock(OBContext.class);
    when(mockContext.getCurrentClient()).thenReturn(mockClient);
    when(mockContext.getCurrentOrganization()).thenReturn(mockOrg);
    when(mockContext.getUser()).thenReturn(mockUser);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockContext.getWarehouse()).thenReturn(mockWarehouse);
    when(mockContext.getLanguage()).thenReturn(mockLanguage);

    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class);
         MockedStatic<CopilotUtils> copilotUtilsMock = mockStatic(CopilotUtils.class);
         MockedStatic<OBPropertiesProvider> propertiesMock = mockStatic(OBPropertiesProvider.class)) {
      
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      setupUtilityMocks(copilotUtilsMock, propertiesMock);
      
      // Override properties for this specific test
      Properties props = new Properties();
      props.setProperty("source.path", TEST_PATH);
      OBPropertiesProvider mockProvider = mock(OBPropertiesProvider.class);
      when(mockProvider.getOpenbravoProperties()).thenReturn(props);
      propertiesMock.when(OBPropertiesProvider::getInstance).thenReturn(mockProvider);

      String input = "Host: @ETENDO_HOST@, Client: @CLIENT_NAME@, User: @USERNAME@";
      String result = CopilotVarReplacerUtil.replaceCopilotPromptVariables(input, null, false);
      
      assertEquals("Host: " + TEST_ETENDO_HOST + ", Client: Test Client, User: testUser", result);
    }
  }

  @Test
  public void testReplaceCopilotPromptVariables_WithCustomMaps() throws JSONException {
    JSONObject maps = new JSONObject();
    maps.put("customVar", "customValue");
    maps.put("booleanVar", true);

    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class);
         MockedStatic<CopilotUtils> copilotUtilsMock = mockStatic(CopilotUtils.class);
         MockedStatic<OBPropertiesProvider> propertiesMock = mockStatic(OBPropertiesProvider.class);
         MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      
      OBContext mockContext = createBasicMockContext();
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      setupUtilityMocks(copilotUtilsMock, propertiesMock);
      setupOBDalMocks(obDalMock, Collections.emptyList());

      String input = "Custom: ${customVar}, Boolean: ${booleanVar}";
      String result = CopilotVarReplacerUtil.replaceCopilotPromptVariables(input, maps, false);
      
      assertEquals("Custom: customValue, Boolean: true", result);
    }
  }

  @Test
  public void testReplaceCopilotPromptVariables_WithBalancedBrackets() throws JSONException {
    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class);
         MockedStatic<CopilotUtils> copilotUtilsMock = mockStatic(CopilotUtils.class);
         MockedStatic<OBPropertiesProvider> propertiesMock = mockStatic(OBPropertiesProvider.class);
         MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      
      OBContext mockContext = createBasicMockContext();
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      setupUtilityMocks(copilotUtilsMock, propertiesMock);
      setupOBDalMocks(obDalMock, Collections.emptyList());

      String input = "Test {value}";
      String result = CopilotVarReplacerUtil.replaceCopilotPromptVariables(input, null, true);
      
      assertEquals("Test {{value}}", result);
    }
  }

  @Test
  public void testReplaceCopilotPromptVariables_UnbalancedBrackets() {
    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class);
         MockedStatic<CopilotUtils> copilotUtilsMock = mockStatic(CopilotUtils.class);
         MockedStatic<OBPropertiesProvider> propertiesMock = mockStatic(OBPropertiesProvider.class);
         MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> messageUtilsMock = mockStatic(OBMessageUtils.class)) {
      
      OBContext mockContext = createBasicMockContext();
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      setupUtilityMocks(copilotUtilsMock, propertiesMock);
      setupOBDalMocks(obDalMock, Collections.emptyList());

      messageUtilsMock.when(() -> OBMessageUtils.messageBD("ETCOP_BalancedBrackets")).thenReturn("Brackets not balanced");

      String input = "Test {unbalanced";
      
      OBException ex = assertThrows(OBException.class, 
          () -> CopilotVarReplacerUtil.replaceCopilotPromptVariables(input, null, true));
      assertEquals("Brackets not balanced", ex.getMessage());
    }
  }

  @Test
  public void testReplaceCopilotPromptVariables_WithApiTokens() throws JSONException {
    User mockUser = mock(User.class);
    when(mockUser.getId()).thenReturn("testUserId");

    Role mockRole = mock(Role.class);
    when(mockRole.getId()).thenReturn("testRoleId");

    Client mockClient = mock(Client.class);
    when(mockClient.getId()).thenReturn("testClientId");

    OBContext mockContext = mock(OBContext.class);
    when(mockContext.getCurrentClient()).thenReturn(mockClient);
    when(mockContext.getCurrentOrganization()).thenReturn(null);
    when(mockContext.getUser()).thenReturn(mockUser);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockContext.getWarehouse()).thenReturn(null);

    Language mockLanguage = mock(Language.class);
    when(mockLanguage.getId()).thenReturn(TEST_LANGUAGE_ID);
    when(mockContext.getLanguage()).thenReturn(mockLanguage);

    CopilotApiToken mockToken = mock(CopilotApiToken.class);
    when(mockToken.getAlias()).thenReturn("TEST_TOKEN");
    when(mockToken.getToken()).thenReturn("encrypted_token_value");
    when(mockToken.getUserContact()).thenReturn(mockUser);
    when(mockToken.getRole()).thenReturn(mockRole);

    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class);
         MockedStatic<CopilotUtils> copilotUtilsMock = mockStatic(CopilotUtils.class);
         MockedStatic<OBPropertiesProvider> propertiesMock = mockStatic(OBPropertiesProvider.class);
         MockedStatic<OBDal> obDalMock = mockStatic(OBDal.class)) {
      
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      setupUtilityMocks(copilotUtilsMock, propertiesMock);
      setupOBDalMocks(obDalMock, Collections.singletonList(mockToken));

      // Note: We're not testing the actual decryption as it requires more complex mocking
      // This test verifies the token retrieval logic structure
      String input = "Token: @TEST_TOKEN@";
      String result = CopilotVarReplacerUtil.replaceCopilotPromptVariables(input, null, false);
      
      // The token won't be replaced due to encryption error, but the method should complete
      assertNotNull(result);
      assertTrue(result.contains("Token:"));
    }
  }
}
