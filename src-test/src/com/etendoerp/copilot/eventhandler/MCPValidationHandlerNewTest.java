package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotMCP;

/**
 * Unit tests for MCPValidationHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class MCPValidationHandlerNewTest {

  private MCPValidationHandler handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityNewEvent newEvent;
  @Mock private EntityUpdateEvent updateEvent;
  @Mock private CopilotMCP copilotMCP;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;

  @Before
  public void setUp() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotMCP.class)).thenReturn(entity);

    handler = new MCPValidationHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedOBMessageUtils.close();
  }

  @Test
  public void testOnSaveValidJson() {
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn("test");
    when(copilotMCP.getJsonStructure()).thenReturn("{\"command\": \"npx\"}");

    handler.onSave(newEvent);
    // No exception
  }

  @Test(expected = OBException.class)
  public void testOnSaveInvalidJson() {
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);
    when(copilotMCP.isActive()).thenReturn(true);
    lenient().when(copilotMCP.getName()).thenReturn("test");
    when(copilotMCP.getJsonStructure()).thenReturn("{ invalid json }");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_InvalidMCPJsonStructure"))
        .thenReturn("Invalid JSON: %s");

    handler.onSave(newEvent);
  }

  @Test
  public void testOnSaveInactiveRecord() {
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);
    when(copilotMCP.isActive()).thenReturn(false);
    when(copilotMCP.getJsonStructure()).thenReturn("{ invalid json }");

    handler.onSave(newEvent);
    // No exception - inactive records skip validation
  }

  @Test
  public void testOnSaveActiveEmptyJson() {
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn("test");
    when(copilotMCP.getJsonStructure()).thenReturn("");

    handler.onSave(newEvent);
    // No exception - empty json just logs warning
  }

  @Test
  public void testOnSaveActiveNullJson() {
    when(newEvent.getTargetInstance()).thenReturn(copilotMCP);
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn("test");
    when(copilotMCP.getJsonStructure()).thenReturn(null);

    handler.onSave(newEvent);
    // No exception - null json just logs warning
  }

  @Test
  public void testOnUpdateValidJson() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotMCP);
    when(copilotMCP.isActive()).thenReturn(true);
    when(copilotMCP.getName()).thenReturn("test");
    when(copilotMCP.getJsonStructure()).thenReturn("{\"key\": \"value\"}");

    handler.onUpdate(updateEvent);
    // No exception
  }

  @Test(expected = OBException.class)
  public void testOnUpdateInvalidJson() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotMCP);
    when(copilotMCP.isActive()).thenReturn(true);
    lenient().when(copilotMCP.getName()).thenReturn("test");
    when(copilotMCP.getJsonStructure()).thenReturn("not json");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_InvalidMCPJsonStructure"))
        .thenReturn("Invalid JSON: %s");

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnUpdateInactive() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotMCP);
    when(copilotMCP.isActive()).thenReturn(false);
    when(copilotMCP.getJsonStructure()).thenReturn("not json");

    handler.onUpdate(updateEvent);
    // No exception
  }
}
