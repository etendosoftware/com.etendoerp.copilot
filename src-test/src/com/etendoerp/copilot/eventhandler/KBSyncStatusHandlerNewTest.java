package com.etendoerp.copilot.eventhandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Unit tests for KBSyncStatusHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class KBSyncStatusHandlerNewTest {

  private KBSyncStatusHandler handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<CopilotAppInfoUtils> mockedAppInfoUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private CopilotFile copilotFile;
  @Mock private CopilotApp copilotApp;
  @Mock private CopilotAppSource appSource;
  @Mock private OBDal obDal;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity fileEntity;
  @Mock private Property property;
  @Mock private OBCriteria<CopilotAppSource> criteria;

  @Before
  public void setUp() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotFile.class)).thenReturn(fileEntity);

    handler = new KBSyncStatusHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    lenient().when(fileEntity.getProperty(anyString())).thenReturn(property);
    lenient().when(obDal.createCriteria(CopilotAppSource.class)).thenReturn(criteria);
    lenient().when(criteria.add(any(Criterion.class))).thenReturn(criteria);
    lenient().when(appSource.getEtcopApp()).thenReturn(copilotApp);
  }

  @After
  public void tearDown() {
    mockedOBDal.close();
    mockedModelProvider.close();
    mockedAppInfoUtils.close();
    mockedCopilotUtils.close();
  }

  @Test
  public void testOnUpdateWithChangedProperty() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getEntity()).thenReturn(fileEntity);
    when(updateEvent.getPreviousState(any(Property.class))).thenReturn("old");
    when(updateEvent.getCurrentState(any(Property.class))).thenReturn("new");
    when(criteria.list()).thenReturn(Collections.singletonList(appSource));

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  @Test
  public void testOnUpdateNoChanges() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
    when(copilotFile.getEntity()).thenReturn(fileEntity);
    when(updateEvent.getPreviousState(any(Property.class))).thenReturn("same");
    when(updateEvent.getCurrentState(any(Property.class))).thenReturn("same");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  @Test
  public void testOnSaveDoesNothing() {
    lenient().when(newEvent.getTargetInstance()).thenReturn(copilotFile);

    handler.onSave(newEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  @Test
  public void testOnDelete() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotFile);
    when(criteria.list()).thenReturn(Collections.singletonList(appSource));

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  @Test
  public void testOnDeleteMultipleSources() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotFile);
    CopilotApp app2 = mock(CopilotApp.class);
    CopilotAppSource source2 = mock(CopilotAppSource.class);
    when(source2.getEtcopApp()).thenReturn(app2);
    when(criteria.list()).thenReturn(Arrays.asList(appSource, source2));

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(app2));
  }

  @Test
  public void testOnDeleteEmptyList() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotFile);
    when(criteria.list()).thenReturn(Collections.emptyList());

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }
}
