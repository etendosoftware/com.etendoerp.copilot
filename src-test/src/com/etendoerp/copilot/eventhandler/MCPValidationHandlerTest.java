package com.etendoerp.copilot.eventhandler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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

public class MCPValidationHandlerTest extends WeldBaseTest {

  private MCPValidationHandler handler;
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
    
    // Create a handler spy that overrides isValidEvent to always return true
    handler = new MCPValidationHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
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
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn("test_server");
    when(copilotMCP.getJsonStructure()).thenReturn("{\"command\": \"npx\"}");
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);

    // Act & Assert
    assertDoesNotThrow(() -> handler.onSave(newEvent));
  }

  @Test
  public void testOnSave_WithInvalidJson() {
    // Arrange
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn("test_server");
    when(copilotMCP.getJsonStructure()).thenReturn("{ invalid json }");
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
    when(copilotMCP.isActive()).thenReturn(false);
    when(copilotMCP.getJsonStructure()).thenReturn("{ invalid json }");
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);

    // Act & Assert - Should not validate inactive records
    assertDoesNotThrow(() -> handler.onSave(newEvent));
  }

  @Test
  public void testOnUpdate_WithInvalidJson() {
    // Arrange
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn("test_server");
    when(copilotMCP.getJsonStructure()).thenReturn("{ invalid json }");
    when(updateEvent.getTargetInstance()).thenReturn(copilotMCP);

    try (MockedStatic<OBMessageUtils> messageUtils = mockStatic(OBMessageUtils.class)) {
      messageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_InvalidMCPJsonStructure"))
          .thenReturn("Invalid JSON structure: %s");

      // Act & Assert
      assertThrows(OBException.class, () -> handler.onUpdate(updateEvent));
    }
  }
}