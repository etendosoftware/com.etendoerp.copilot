package com.etendoerp.copilot.eventhandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Unit tests for AssistantSyncStatusHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssistantSyncStatusHandlerNewTest {

  private AssistantSyncStatusHandler handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<CopilotAppInfoUtils> mockedAppInfoUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private CopilotApp copilotApp;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity appEntity;
  @Mock private Property property;

  @Before
  public void setUp() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);

    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotApp.class)).thenReturn(appEntity);
    lenient().when(modelProvider.getEntity(CopilotApp.ENTITY_NAME)).thenReturn(appEntity);

    handler = new AssistantSyncStatusHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    lenient().when(appEntity.getProperty(anyString())).thenReturn(property);
  }

  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedAppInfoUtils.close();
    mockedCopilotUtils.close();
  }

  @Test
  public void testOnUpdatePropertyChanged() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(updateEvent.getPreviousState(any(Property.class))).thenReturn("old");
    when(updateEvent.getCurrentState(any(Property.class))).thenReturn("new");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  @Test
  public void testOnUpdateNoPropertyChanged() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(updateEvent.getPreviousState(any(Property.class))).thenReturn("same");
    when(updateEvent.getCurrentState(any(Property.class))).thenReturn("same");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  @Test
  public void testOnSaveDoesNothing() {
    lenient().when(newEvent.getTargetInstance()).thenReturn(copilotApp);

    handler.onSave(newEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  @Test
  public void testOnDeleteDoesNothing() {
    lenient().when(deleteEvent.getTargetInstance()).thenReturn(copilotApp);

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

}
