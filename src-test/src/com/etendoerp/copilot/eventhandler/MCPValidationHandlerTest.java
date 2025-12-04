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
package com.etendoerp.copilot.eventhandler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotMCP;

/**
 * Test class for MCPValidationHandler to verify JSON validation functionality
 * for MCP server configurations during entity persistence events.
 */
public class MCPValidationHandlerTest extends WeldBaseTest {

  private static final String TEST_SERVER_NAME = "test_server";
  private static final String INVALID_JSON_STRUCTURE = "{ invalid json }";

  private AutoCloseable mocks;

  @Mock
  private EntityNewEvent newEvent;
  @Mock
  private EntityUpdateEvent updateEvent;
  @Mock
  private CopilotMCP copilotMCP;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    mocks = MockitoAnnotations.openMocks(this);
    
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN, TestConstants.Clients.FB_GRP,
        TestConstants.Orgs.ESP_NORTE);
    VariablesSecureApp vsa = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId(),
        OBContext.getOBContext().getRole().getId());
    RequestContext.get().setVariableSecureApp(vsa);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  public void testOnSave_WithValidJson() {
    // Arrange
    MCPValidationHandler handler = new MCPValidationHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
    
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn(TEST_SERVER_NAME);
    when(copilotMCP.getJsonStructure()).thenReturn("{\"command\": \"npx\"}");
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);

    // Act & Assert
    assertDoesNotThrow(() -> handler.onSave(newEvent));
  }

  @Test
  public void testOnSave_WithInvalidJson() {
    // Arrange
    MCPValidationHandler handler = new MCPValidationHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
    
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn(TEST_SERVER_NAME);
    when(copilotMCP.getJsonStructure()).thenReturn(INVALID_JSON_STRUCTURE);
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);

    try (MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_InvalidMCPJsonStructure"))
          .thenReturn("Invalid JSON structure: %s");

      // Act & Assert
      assertThrows(OBException.class, () -> handler.onSave(newEvent));
    }
  }

  @Test
  public void testOnSave_WithInactiveRecord() {
    // Arrange
    MCPValidationHandler handler = new MCPValidationHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
    
    when(copilotMCP.isActive()).thenReturn(false);
    when(copilotMCP.getJsonStructure()).thenReturn(INVALID_JSON_STRUCTURE);
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);

    // Act & Assert - Should not validate inactive records
    assertDoesNotThrow(() -> handler.onSave(newEvent));
  }

  @Test
  public void testOnUpdate_WithInvalidJson() {
    // Arrange
    MCPValidationHandler handler = new MCPValidationHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
    
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn(TEST_SERVER_NAME);
    when(copilotMCP.getJsonStructure()).thenReturn(INVALID_JSON_STRUCTURE);
    when(updateEvent.getTargetInstance()).thenReturn(copilotMCP);

    try (MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_InvalidMCPJsonStructure"))
          .thenReturn("Invalid JSON structure: %s");

      // Act & Assert
      assertThrows(OBException.class, () -> handler.onUpdate(updateEvent));
    }
  }
}