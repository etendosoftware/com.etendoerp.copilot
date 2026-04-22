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
import static org.mockito.Mockito.lenient;
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
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Unit tests for AssistantToolSyncStatusHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssistantToolSyncStatusHandlerNewTest {

  private AssistantToolSyncStatusHandler handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<CopilotAppInfoUtils> mockedAppInfoUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private CopilotAppTool copilotAppTool;
  @Mock private CopilotApp copilotApp;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity appToolEntity;
  @Mock private Property toolProperty;

  /** Set up. */
  @Before
  public void setUp() {
    initModelProviderAndEntity();

    handler = new AssistantToolSyncStatusHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    lenient().when(copilotAppTool.getEntity()).thenReturn(appToolEntity);
    lenient().when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    lenient().when(appToolEntity.getProperty(CopilotAppTool.PROPERTY_COPILOTTOOL)).thenReturn(toolProperty);
  }

  private void initModelProviderAndEntity() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotAppTool.class)).thenReturn(appToolEntity);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedAppInfoUtils.close();
    mockedCopilotUtils.close();
  }

  /** Test on update tool changed. */
  @Test
  public void testOnUpdateToolChanged() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(updateEvent.getPreviousState(toolProperty)).thenReturn("oldTool");
    when(updateEvent.getCurrentState(toolProperty)).thenReturn("newTool");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on update tool not changed. */
  @Test
  public void testOnUpdateToolNotChanged() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(updateEvent.getPreviousState(toolProperty)).thenReturn("sameTool");
    when(updateEvent.getCurrentState(toolProperty)).thenReturn("sameTool");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  /** Test on update tool changed from null. */
  @Test
  public void testOnUpdateToolChangedFromNull() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(updateEvent.getPreviousState(toolProperty)).thenReturn(null);
    when(updateEvent.getCurrentState(toolProperty)).thenReturn("newTool");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on update both null. */
  @Test
  public void testOnUpdateBothNull() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(updateEvent.getPreviousState(toolProperty)).thenReturn(null);
    when(updateEvent.getCurrentState(toolProperty)).thenReturn(null);

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  /** Test on save. */
  @Test
  public void testOnSave() {
    when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);

    handler.onSave(newEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on delete. */
  @Test
  public void testOnDelete() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }
}
