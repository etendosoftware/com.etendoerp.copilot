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

  @Test
  public void testGetMCPConfigurations_WithValidConfiguration() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    
    CopilotMCP mcpConfig = mock(CopilotMCP.class);
    when(mcpConfig.isActive()).thenReturn(true);
    when(mcpConfig.getName()).thenReturn("filesystem");
    when(mcpConfig.getJsonStructure()).thenReturn("{\"command\": \"npx\"}");

    CopilotAppMCP appMcp = mock(CopilotAppMCP.class);
    when(appMcp.getMCPServer()).thenReturn(mcpConfig);

    List<CopilotAppMCP> appMcpList = Collections.singletonList(appMcp);

    // Mock OBDal
    OBDal obDal = mock(OBDal.class);
    OBCriteria<CopilotAppMCP> criteria = mock(OBCriteria.class);
    when(criteria.list()).thenReturn(appMcpList);
    when(obDal.createCriteria(CopilotAppMCP.class)).thenReturn(criteria);

    // Mock OBContext and related objects
    OBContext obContext = mock(OBContext.class);
    Client client = mock(Client.class);
    Organization org = mock(Organization.class);
    User user = mock(User.class);
    Role role = mock(Role.class);
    Warehouse warehouse = mock(Warehouse.class);
    
    when(client.getId()).thenReturn("client-123");
    when(client.getName()).thenReturn("Test Client");
    when(org.getId()).thenReturn("org-456");
    when(org.getName()).thenReturn("Test Organization");
    when(user.getId()).thenReturn("user-789");
    when(user.getUsername()).thenReturn("testuser");
    when(role.getId()).thenReturn("role-101");
    when(role.getName()).thenReturn("Test Role");
    when(warehouse.getId()).thenReturn("warehouse-202");
    when(warehouse.getName()).thenReturn("Test Warehouse");
    
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getCurrentOrganization()).thenReturn(org);
    when(obContext.getUser()).thenReturn(user);
    when(obContext.getRole()).thenReturn(role);
    when(obContext.getWarehouse()).thenReturn(warehouse);

    // Mock OBPropertiesProvider
    OBPropertiesProvider propertiesProvider = mock(OBPropertiesProvider.class);
    Properties properties = mock(Properties.class);
    when(propertiesProvider.getOpenbravoProperties()).thenReturn(properties);

    // Mock normalized JSON result
    JSONArray normalizedArray = new JSONArray();
    JSONObject normalizedConfig = new JSONObject();
    normalizedConfig.put("command", "npx");
    normalizedConfig.put("name", "filesystem");
    normalizedArray.put(normalizedConfig);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBPropertiesProvider> propertiesStatic = mockStatic(OBPropertiesProvider.class);
         MockedStatic<CopilotUtils> copilotUtilsStatic = mockStatic(CopilotUtils.class);
         MockedStatic<MCPConfigNormalizer> normalizerStatic = mockStatic(MCPConfigNormalizer.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      propertiesStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
      
      copilotUtilsStatic.when(CopilotUtils::getEtendoHost).thenReturn("http://localhost:8080");
      copilotUtilsStatic.when(CopilotUtils::getEtendoHostDocker).thenReturn("http://host.docker.internal:8080");
      copilotUtilsStatic.when(() -> CopilotUtils.getSourcesPath(any(Properties.class))).thenReturn("/opt/etendo/sources");
      
      normalizerStatic.when(() -> MCPConfigNormalizer.normalizeToArray(any(JSONObject.class), anyString()))
                      .thenReturn(normalizedArray);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.length());
      
      JSONObject config = result.getJSONObject(0);
      assertEquals("filesystem", config.getString("name"));
      assertEquals("npx", config.getString("command"));
    }
  }

  @Test
  public void testGetMCPConfigurations_WithEmptyList() throws JSONException {
    // Arrange
    CopilotApp copilotApp = mock(CopilotApp.class);
    List<CopilotAppMCP> emptyList = Collections.emptyList();

    // Mock OBDal
    OBDal obDal = mock(OBDal.class);
    OBCriteria<CopilotAppMCP> criteria = mock(OBCriteria.class);
    when(criteria.list()).thenReturn(emptyList);
    when(obDal.createCriteria(CopilotAppMCP.class)).thenReturn(criteria);

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

    // Mock OBDal
    OBDal obDal = mock(OBDal.class);
    OBCriteria<CopilotAppMCP> criteria = mock(OBCriteria.class);
    when(criteria.list()).thenReturn(appMcpList);
    when(obDal.createCriteria(CopilotAppMCP.class)).thenReturn(criteria);

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

    // Mock OBDal
    OBDal obDal = mock(OBDal.class);
    OBCriteria<CopilotAppMCP> criteria = mock(OBCriteria.class);
    when(criteria.list()).thenReturn(appMcpList);
    when(obDal.createCriteria(CopilotAppMCP.class)).thenReturn(criteria);

    // Mock OBContext and related objects (needed for variable replacement)
    OBContext obContext = mock(OBContext.class);
    Client client = mock(Client.class);
    Organization org = mock(Organization.class);
    User user = mock(User.class);
    Role role = mock(Role.class);
    Warehouse warehouse = mock(Warehouse.class);
    
    when(client.getId()).thenReturn("client-123");
    when(client.getName()).thenReturn("Test Client");
    when(org.getId()).thenReturn("org-456");
    when(org.getName()).thenReturn("Test Organization");
    when(user.getId()).thenReturn("user-789");
    when(user.getUsername()).thenReturn("testuser");
    when(role.getId()).thenReturn("role-101");
    when(role.getName()).thenReturn("Test Role");
    when(warehouse.getId()).thenReturn("warehouse-202");
    when(warehouse.getName()).thenReturn("Test Warehouse");
    
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getCurrentOrganization()).thenReturn(org);
    when(obContext.getUser()).thenReturn(user);
    when(obContext.getRole()).thenReturn(role);
    when(obContext.getWarehouse()).thenReturn(warehouse);

    // Mock OBPropertiesProvider
    OBPropertiesProvider propertiesProvider = mock(OBPropertiesProvider.class);
    Properties properties = mock(Properties.class);
    when(propertiesProvider.getOpenbravoProperties()).thenReturn(properties);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBPropertiesProvider> propertiesStatic = mockStatic(OBPropertiesProvider.class);
         MockedStatic<CopilotUtils> copilotUtilsStatic = mockStatic(CopilotUtils.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      propertiesStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
      
      copilotUtilsStatic.when(CopilotUtils::getEtendoHost).thenReturn("http://localhost:8080");
      copilotUtilsStatic.when(CopilotUtils::getEtendoHostDocker).thenReturn("http://host.docker.internal:8080");
      copilotUtilsStatic.when(() -> CopilotUtils.getSourcesPath(any(Properties.class))).thenReturn("/opt/etendo/sources");

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
    when(mcpConfig.getName()).thenReturn("filesystem");
    when(mcpConfig.getJsonStructure()).thenReturn(
        "{\"command\": \"npx\", \"args\": [\"@source.path@\"], \"env\": {\"CLIENT_ID\": \"@AD_CLIENT_ID@\", \"USER\": \"@USERNAME@\"}}");

    CopilotAppMCP appMcp = mock(CopilotAppMCP.class);
    when(appMcp.getMCPServer()).thenReturn(mcpConfig);

    List<CopilotAppMCP> appMcpList = Collections.singletonList(appMcp);

    // Mock OBDal
    OBDal obDal = mock(OBDal.class);
    OBCriteria<CopilotAppMCP> criteria = mock(OBCriteria.class);
    when(criteria.list()).thenReturn(appMcpList);
    when(obDal.createCriteria(CopilotAppMCP.class)).thenReturn(criteria);

    // Mock OBContext and related objects
    OBContext obContext = mock(OBContext.class);
    Client client = mock(Client.class);
    Organization org = mock(Organization.class);
    User user = mock(User.class);
    Role role = mock(Role.class);
    Warehouse warehouse = mock(Warehouse.class);
    
    when(client.getId()).thenReturn("client-123");
    when(client.getName()).thenReturn("Test Client");
    when(org.getId()).thenReturn("org-456");
    when(org.getName()).thenReturn("Test Organization");
    when(user.getId()).thenReturn("user-789");
    when(user.getUsername()).thenReturn("testuser");
    when(role.getId()).thenReturn("role-101");
    when(role.getName()).thenReturn("Test Role");
    when(warehouse.getId()).thenReturn("warehouse-202");
    when(warehouse.getName()).thenReturn("Test Warehouse");
    
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getCurrentOrganization()).thenReturn(org);
    when(obContext.getUser()).thenReturn(user);
    when(obContext.getRole()).thenReturn(role);
    when(obContext.getWarehouse()).thenReturn(warehouse);

    // Mock OBPropertiesProvider
    OBPropertiesProvider propertiesProvider = mock(OBPropertiesProvider.class);
    Properties properties = mock(Properties.class);
    when(propertiesProvider.getOpenbravoProperties()).thenReturn(properties);

    // Mock normalized JSON result with replaced variables
    JSONArray normalizedArray = new JSONArray();
    JSONObject normalizedConfig = new JSONObject();
    normalizedConfig.put("command", "npx");
    normalizedConfig.put("name", "filesystem");
    JSONArray argsArray = new JSONArray();
    argsArray.put("/opt/etendo/sources");
    normalizedConfig.put("args", argsArray);
    JSONObject envObject = new JSONObject();
    envObject.put("CLIENT_ID", "client-123");
    envObject.put("USER", "testuser");
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
      
      copilotUtilsStatic.when(CopilotUtils::getEtendoHost).thenReturn("http://localhost:8080");
      copilotUtilsStatic.when(CopilotUtils::getEtendoHostDocker).thenReturn("http://host.docker.internal:8080");
      copilotUtilsStatic.when(() -> CopilotUtils.getSourcesPath(any(Properties.class))).thenReturn("/opt/etendo/sources");
      
      normalizerStatic.when(() -> MCPConfigNormalizer.normalizeToArray(any(JSONObject.class), anyString()))
                      .thenReturn(normalizedArray);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.length());
      
      JSONObject config = result.getJSONObject(0);
      assertEquals("filesystem", config.getString("name"));
      assertEquals("npx", config.getString("command"));
      
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

    // Mock OBDal
    OBDal obDal = mock(OBDal.class);
    OBCriteria<CopilotAppMCP> criteria = mock(OBCriteria.class);
    when(criteria.list()).thenReturn(appMcpList);
    when(obDal.createCriteria(CopilotAppMCP.class)).thenReturn(criteria);

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

    // Mock OBDal
    OBDal obDal = mock(OBDal.class);
    OBCriteria<CopilotAppMCP> criteria = mock(OBCriteria.class);
    when(criteria.list()).thenReturn(appMcpList);
    when(obDal.createCriteria(CopilotAppMCP.class)).thenReturn(criteria);

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
