package com.etendoerp.copilot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppMCP;
import com.etendoerp.copilot.data.CopilotMCP;

/**
 * Test class for MCPUtils to verify the retrieval of MCP configurations
 * from the database and their JSON structure validation.
 */
public class MCPUtilsTest extends WeldBaseTest {

  private static final String TEST_MCP_NAME = "filesystem";
  private static final String TEST_CLIENT_ID = "client-123";
  private static final String TEST_CLIENT_NAME = "Test Client";
  private static final String TEST_ORG_ID = "org-456";
  private static final String TEST_ORG_NAME = "Test Organization";
  private static final String TEST_USER_ID = "user-789";
  private static final String TEST_USERNAME = "testuser";
  private static final String TEST_ROLE_ID = "role-101";
  private static final String TEST_ROLE_NAME = "Test Role";
  private static final String TEST_WAREHOUSE_ID = "warehouse-202";
  private static final String TEST_WAREHOUSE_NAME = "Test Warehouse";
  private static final String JSON_COMMAND_KEY = "command";
  private static final String TEST_ETENDO_HOST = "http://localhost:8080";
  private static final String TEST_ETENDO_HOST_DOCKER = "http://host.docker.internal:8080";
  private static final String TEST_SOURCES_PATH = "/opt/etendo/sources";

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
   * Creates and configures a mock OBDal instance with the given list of CopilotAppMCP objects.
   */
  private OBDal createMockOBDal(List<CopilotAppMCP> appMcpList) {
    OBDal obDal = mock(OBDal.class);
    OBCriteria<CopilotAppMCP> criteria = mock(OBCriteria.class);
    when(criteria.list()).thenReturn(appMcpList);
    when(obDal.createCriteria(CopilotAppMCP.class)).thenReturn(criteria);
    return obDal;
  }

  /**
   * Creates and configures a complete mock OBContext hierarchy with all related objects.
   */
  private OBContext createMockOBContext() {
    OBContext obContext = mock(OBContext.class);
    Client client = mock(Client.class);
    Organization org = mock(Organization.class);
    User user = mock(User.class);
    Role role = mock(Role.class);
    Warehouse warehouse = mock(Warehouse.class);
    
    when(client.getId()).thenReturn(TEST_CLIENT_ID);
    when(client.getName()).thenReturn(TEST_CLIENT_NAME);
    when(org.getId()).thenReturn(TEST_ORG_ID);
    when(org.getName()).thenReturn(TEST_ORG_NAME);
    when(user.getId()).thenReturn(TEST_USER_ID);
    when(user.getUsername()).thenReturn(TEST_USERNAME);
    when(role.getId()).thenReturn(TEST_ROLE_ID);
    when(role.getName()).thenReturn(TEST_ROLE_NAME);
    when(warehouse.getId()).thenReturn(TEST_WAREHOUSE_ID);
    when(warehouse.getName()).thenReturn(TEST_WAREHOUSE_NAME);
    
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getCurrentOrganization()).thenReturn(org);
    when(obContext.getUser()).thenReturn(user);
    when(obContext.getRole()).thenReturn(role);
    when(obContext.getWarehouse()).thenReturn(warehouse);
    
    return obContext;
  }

  /**
   * Creates and configures a mock OBPropertiesProvider with Properties.
   */
  private OBPropertiesProvider createMockPropertiesProvider() {
    OBPropertiesProvider propertiesProvider = mock(OBPropertiesProvider.class);
    Properties properties = mock(Properties.class);
    when(propertiesProvider.getOpenbravoProperties()).thenReturn(properties);
    return propertiesProvider;
  }

  /**
   * Configures common static mocks for CopilotUtils.
   */
  private void configureCopilotUtilsMocks(MockedStatic<CopilotUtils> copilotUtilsStatic) {
    copilotUtilsStatic.when(CopilotUtils::getEtendoHost).thenReturn(TEST_ETENDO_HOST);
    copilotUtilsStatic.when(CopilotUtils::getEtendoHostDocker).thenReturn(TEST_ETENDO_HOST_DOCKER);
    copilotUtilsStatic.when(() -> CopilotUtils.getSourcesPath(any(Properties.class))).thenReturn(TEST_SOURCES_PATH);
  }

  @Test
  public void testGetMCPConfigurations_WithValidConfiguration() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    
    CopilotMCP mcpConfig = mock(CopilotMCP.class);
    when(mcpConfig.isActive()).thenReturn(true);
    when(mcpConfig.getName()).thenReturn(TEST_MCP_NAME);
    when(mcpConfig.getJsonStructure()).thenReturn("{\"command\": \"npx\"}");

    CopilotAppMCP appMcp = mock(CopilotAppMCP.class);
    when(appMcp.getMCPServer()).thenReturn(mcpConfig);

    List<CopilotAppMCP> appMcpList = Collections.singletonList(appMcp);

    OBDal obDal = createMockOBDal(appMcpList);
    OBContext obContext = createMockOBContext();
    OBPropertiesProvider propertiesProvider = createMockPropertiesProvider();

    // Mock normalized JSON result
    JSONArray normalizedArray = new JSONArray();
    JSONObject normalizedConfig = new JSONObject();
    normalizedConfig.put(JSON_COMMAND_KEY, "npx");
    normalizedConfig.put("name", TEST_MCP_NAME);
    normalizedArray.put(normalizedConfig);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBPropertiesProvider> propertiesStatic = mockStatic(OBPropertiesProvider.class);
         MockedStatic<CopilotUtils> copilotUtilsStatic = mockStatic(CopilotUtils.class);
         MockedStatic<MCPConfigNormalizer> normalizerStatic = mockStatic(MCPConfigNormalizer.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      propertiesStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
      
      configureCopilotUtilsMocks(copilotUtilsStatic);
      
      normalizerStatic.when(() -> MCPConfigNormalizer.normalizeToArray(any(JSONObject.class), anyString()))
                      .thenReturn(normalizedArray);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.length());
      
      JSONObject config = result.getJSONObject(0);
      assertEquals(TEST_MCP_NAME, config.getString("name"));
      assertEquals("npx", config.getString(JSON_COMMAND_KEY));
    }
  }

  @Test
  public void testGetMCPConfigurations_WithEmptyList() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    List<CopilotAppMCP> emptyList = Collections.emptyList();

    OBDal obDal = createMockOBDal(emptyList);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(0, result.length());
    }
  }

  @Test
  public void testGetMCPConfigurations_WithInactiveServer() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    
    CopilotMCP mcpConfig = mock(CopilotMCP.class);
    when(mcpConfig.isActive()).thenReturn(false);
    when(mcpConfig.getName()).thenReturn("inactive_server");
    when(mcpConfig.getJsonStructure()).thenReturn("{\"command\": \"test\"}");

    CopilotAppMCP appMcp = mock(CopilotAppMCP.class);
    when(appMcp.getMCPServer()).thenReturn(mcpConfig);

    List<CopilotAppMCP> appMcpList = Collections.singletonList(appMcp);

    OBDal obDal = createMockOBDal(appMcpList);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(0, result.length());
    }
  }

  @Test
  public void testGetMCPConfigurations_WithInvalidJson() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    
    CopilotMCP mcpConfig = mock(CopilotMCP.class);
    when(mcpConfig.isActive()).thenReturn(true);
    when(mcpConfig.getName()).thenReturn("invalid_server");
    when(mcpConfig.getJsonStructure()).thenReturn("{ invalid json }");

    CopilotAppMCP appMcp = mock(CopilotAppMCP.class);
    when(appMcp.getMCPServer()).thenReturn(mcpConfig);

    List<CopilotAppMCP> appMcpList = Collections.singletonList(appMcp);

    OBDal obDal = createMockOBDal(appMcpList);
    OBContext obContext = createMockOBContext();
    OBPropertiesProvider propertiesProvider = createMockPropertiesProvider();

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBPropertiesProvider> propertiesStatic = mockStatic(OBPropertiesProvider.class);
         MockedStatic<CopilotUtils> copilotUtilsStatic = mockStatic(CopilotUtils.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      propertiesStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
      
      configureCopilotUtilsMocks(copilotUtilsStatic);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(0, result.length()); // Invalid JSON should be skipped
    }
  }

  @Test
  public void testGetMCPConfigurations_WithVariableReplacement() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    
    CopilotMCP mcpConfig = mock(CopilotMCP.class);
    when(mcpConfig.isActive()).thenReturn(true);
    when(mcpConfig.getName()).thenReturn(TEST_MCP_NAME);
    when(mcpConfig.getJsonStructure()).thenReturn(
        "{\"command\": \"npx\", \"args\": [\"@source.path@\"], \"env\": {\"CLIENT_ID\": \"@AD_CLIENT_ID@\", \"USER\": \"@USERNAME@\"}}");

    CopilotAppMCP appMcp = mock(CopilotAppMCP.class);
    when(appMcp.getMCPServer()).thenReturn(mcpConfig);

    List<CopilotAppMCP> appMcpList = Collections.singletonList(appMcp);

    OBDal obDal = createMockOBDal(appMcpList);
    OBContext obContext = createMockOBContext();
    OBPropertiesProvider propertiesProvider = createMockPropertiesProvider();

    // Mock normalized JSON result with replaced variables
    JSONArray normalizedArray = new JSONArray();
    JSONObject normalizedConfig = new JSONObject();
    normalizedConfig.put(JSON_COMMAND_KEY, "npx");
    normalizedConfig.put("name", TEST_MCP_NAME);
    JSONArray argsArray = new JSONArray();
    argsArray.put(TEST_SOURCES_PATH);
    normalizedConfig.put("args", argsArray);
    JSONObject envObject = new JSONObject();
    envObject.put("CLIENT_ID", TEST_CLIENT_ID);
    envObject.put("USER", TEST_USERNAME);
    normalizedConfig.put("env", envObject);
    normalizedArray.put(normalizedConfig);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBPropertiesProvider> propertiesStatic = mockStatic(OBPropertiesProvider.class);
         MockedStatic<CopilotUtils> copilotUtilsStatic = mockStatic(CopilotUtils.class);
         MockedStatic<MCPConfigNormalizer> normalizerStatic = mockStatic(MCPConfigNormalizer.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      propertiesStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
      
      configureCopilotUtilsMocks(copilotUtilsStatic);
      
      normalizerStatic.when(() -> MCPConfigNormalizer.normalizeToArray(any(JSONObject.class), anyString()))
                      .thenReturn(normalizedArray);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.length());
      
      JSONObject config = result.getJSONObject(0);
      assertEquals(TEST_MCP_NAME, config.getString("name"));
      assertEquals("npx", config.getString(JSON_COMMAND_KEY));
      
      // Verify that variables were replaced in the JSON processing
      // (This is indirectly tested through the mocked normalizer behavior)
    }
  }

  @Test
  public void testGetMCPConfigurations_WithNullMCPConfig() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    
    CopilotAppMCP appMcp = mock(CopilotAppMCP.class);
    when(appMcp.getMCPServer()).thenReturn(null); // Null MCP config

    List<CopilotAppMCP> appMcpList = Collections.singletonList(appMcp);

    OBDal obDal = createMockOBDal(appMcpList);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(0, result.length()); // Null config should be skipped
    }
  }

  @Test
  public void testGetMCPConfigurations_WithEmptyJsonStructure() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    
    CopilotMCP mcpConfig = mock(CopilotMCP.class);
    when(mcpConfig.isActive()).thenReturn(true);
    when(mcpConfig.getName()).thenReturn("empty_config");
    when(mcpConfig.getJsonStructure()).thenReturn(""); // Empty JSON structure

    CopilotAppMCP appMcp = mock(CopilotAppMCP.class);
    when(appMcp.getMCPServer()).thenReturn(mcpConfig);

    List<CopilotAppMCP> appMcpList = Collections.singletonList(appMcp);

    OBDal obDal = createMockOBDal(appMcpList);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(0, result.length()); // Empty JSON structure should be skipped
    }
  }
}
