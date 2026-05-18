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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
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
  private static final String TOOL_SYNC_OLD_VALUE = "oldValue";
  private static final String TOOL_SYNC_NEW_VALUE = "newValue";

  private ToolSyncStatusHandler handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<CopilotAppInfoUtils> mockedAppInfoUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private CopilotTool copilotTool;
  @Mock private CopilotApp copilotApp;
  @Mock private CopilotAppTool copilotAppTool;
  @Mock private OBDal obDal;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;
  @Mock private Property property;
  @Mock private OBCriteria<CopilotAppTool> criteria;

  /** Set up. */
  @Before
  public void setUp() {
    prepareToolSyncStaticMocks();
    configureToolSyncBehavior();

    handler = new ToolSyncStatusHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  private void prepareToolSyncStaticMocks() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
  }

  private void configureToolSyncBehavior() {
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    lenient().when(modelProvider.getEntity(CopilotTool.class)).thenReturn(entity);
    lenient().when(obDal.createCriteria(CopilotAppTool.class)).thenReturn(criteria);
    lenient().when(entity.getProperty(anyString())).thenReturn(property);
    lenient().when(criteria.add(any(Criterion.class))).thenReturn(criteria);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedCopilotUtils.close();
    mockedAppInfoUtils.close();
    mockedOBDal.close();
    mockedModelProvider.close();
  }

  /** Test on update no property changes. */
  @Test
  public void testOnUpdateNoPropertyChanges() {
    setupUpdateEvent("value", "value");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  /** Test on update property changed. */
  @Test
  public void testOnUpdatePropertyChanged() {
    setupUpdateEvent(TOOL_SYNC_OLD_VALUE, TOOL_SYNC_NEW_VALUE);
    when(criteria.list()).thenReturn(Collections.singletonList(copilotAppTool));
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on update multiple apps. */
  @Test
  public void testOnUpdateMultipleApps() {
    setupUpdateEvent("old", "new");

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

  /** Test on save does nothing. */
  @Test
  public void testOnSaveDoesNothing() {
    lenient().when(newEvent.getTargetInstance()).thenReturn(copilotTool);

    handler.onSave(newEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  /** Test on delete. */
  @Test
  public void testOnDelete() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotTool);
    when(criteria.list()).thenReturn(Collections.singletonList(copilotAppTool));
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on delete empty list. */
  @Test
  public void testOnDeleteEmptyList() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotTool);
    when(criteria.list()).thenReturn(Collections.emptyList());

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  private void setupUpdateEvent(String previousState, String currentState) {
    when(updateEvent.getTargetInstance()).thenReturn(copilotTool);
    when(copilotTool.getEntity()).thenReturn(entity);
    when(updateEvent.getPreviousState(any(Property.class))).thenReturn(previousState);
    when(updateEvent.getCurrentState(any(Property.class))).thenReturn(currentState);
  }
}
