package com.etendoerp.copilot.eventhandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.hibernate.criterion.Criterion;
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
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Unit tests for ToolSyncStatusHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class ToolSyncStatusHandlerNewTest {

  private ToolSyncStatusHandler handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<CopilotAppInfoUtils> mockedAppInfoUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;

  @Mock
  private EntityUpdateEvent updateEvent;
  @Mock
  private EntityNewEvent newEvent;
  @Mock
  private EntityDeleteEvent deleteEvent;
  @Mock
  private CopilotTool copilotTool;
  @Mock
  private CopilotApp copilotApp;
  @Mock
  private CopilotAppTool copilotAppTool;
  @Mock
  private OBDal obDal;
  @Mock
  private ModelProvider modelProvider;
  @Mock
  private Entity entity;
  @Mock
  private Property property;
  @Mock
  private OBCriteria<CopilotAppTool> criteria;

  @Before
  public void setUp() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotTool.class)).thenReturn(entity);

    handler = new ToolSyncStatusHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    lenient().when(entity.getProperty(anyString())).thenReturn(property);
    lenient().when(obDal.createCriteria(CopilotAppTool.class)).thenReturn(criteria);
    lenient().when(criteria.add(any(Criterion.class))).thenReturn(criteria);
  }

  @After
  public void tearDown() {
    mockedOBDal.close();
    mockedModelProvider.close();
    mockedAppInfoUtils.close();
    mockedCopilotUtils.close();
  }

  @Test
  public void testOnUpdateNoPropertyChanges() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotTool);
    when(copilotTool.getEntity()).thenReturn(entity);
    when(updateEvent.getPreviousState(any(Property.class))).thenReturn("value");
    when(updateEvent.getCurrentState(any(Property.class))).thenReturn("value");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  @Test
  public void testOnUpdatePropertyChanged() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotTool);
    when(copilotTool.getEntity()).thenReturn(entity);
    when(updateEvent.getPreviousState(any(Property.class))).thenReturn("oldValue");
    when(updateEvent.getCurrentState(any(Property.class))).thenReturn("newValue");
    when(criteria.list()).thenReturn(Collections.singletonList(copilotAppTool));
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  @Test
  public void testOnUpdateMultipleApps() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotTool);
    when(copilotTool.getEntity()).thenReturn(entity);
    when(updateEvent.getPreviousState(any(Property.class))).thenReturn("old");
    when(updateEvent.getCurrentState(any(Property.class))).thenReturn("new");

    CopilotApp copilotApp2 = mock(CopilotApp.class);
    CopilotAppTool appTool2 = mock(CopilotAppTool.class);
    when(appTool2.getCopilotApp()).thenReturn(copilotApp2);
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    when(criteria.list()).thenReturn(Arrays.asList(copilotAppTool, appTool2));

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp2));
  }

  @Test
  public void testOnSaveDoesNothing() {
    lenient().when(newEvent.getTargetInstance()).thenReturn(copilotTool);

    handler.onSave(newEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  @Test
  public void testOnDelete() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotTool);
    when(criteria.list()).thenReturn(Collections.singletonList(copilotAppTool));
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  @Test
  public void testOnDeleteEmptyList() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotTool);
    when(criteria.list()).thenReturn(Collections.emptyList());

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }
}
