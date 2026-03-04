package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;

/**
 * Unit tests for ToolAddedToSystemApp.
 */
@RunWith(MockitoJUnitRunner.class)
public class ToolAddedToSystemAppNewTest {

  private ToolAddedToSystemApp handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private CopilotAppTool copilotAppTool;
  @Mock private CopilotApp copilotApp;
  @Mock private Client appClient;
  @Mock private Client contextClient;
  @Mock private OBContext obContext;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;

  @Before
  public void setUp() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    lenient().when(modelProvider.getEntity(CopilotAppTool.ENTITY_NAME)).thenReturn(entity);
    lenient().when(obContext.getCurrentClient()).thenReturn(contextClient);

    handler = new ToolAddedToSystemApp() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    lenient().when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    lenient().when(copilotApp.getClient()).thenReturn(appClient);
  }

  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedOBContext.close();
    mockedOBMessageUtils.close();
  }

  @Test
  public void testOnUpdateSameClient() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(appClient.getId()).thenReturn("100");
    when(contextClient.getId()).thenReturn("100");

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateDifferentClient() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(appClient.getId()).thenReturn("100");
    when(contextClient.getId()).thenReturn("200");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_WrongClientApp"))
        .thenReturn("Wrong client");

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnSaveSameClient() {
    when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(appClient.getId()).thenReturn("100");
    when(contextClient.getId()).thenReturn("100");

    handler.onSave(newEvent);
  }

  @Test(expected = OBException.class)
  public void testOnSaveDifferentClient() {
    when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(appClient.getId()).thenReturn("100");
    when(contextClient.getId()).thenReturn("200");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_WrongClientApp"))
        .thenReturn("Wrong client");

    handler.onSave(newEvent);
  }

  @Test
  public void testOnDeleteDoesNothing() {
    lenient().when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);

    handler.onDelete(deleteEvent);
    // No client check on delete
  }
}
