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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
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

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

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

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      // Act
      JSONArray result = MCPUtils.getMCPConfigurations(copilotApp);

      // Assert
      assertNotNull(result);
      assertEquals(0, result.length()); // Invalid JSON should be skipped
    }
  }
}