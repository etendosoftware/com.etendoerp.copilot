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
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Unit tests for AssistantTMSyncStatusHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssistantTMSyncStatusHandlerNewTest {

  private AssistantTMSyncStatusHandler handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<CopilotAppInfoUtils> mockedAppInfoUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private TeamMember teamMember;
  @Mock private CopilotApp copilotApp;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity teamMemberEntity;
  @Mock private Property memberProperty;
  @Mock private Property appProperty;

  /** Set up. */
  @Before
  public void setUp() {
    setupModelProviderMocks();

    handler = new AssistantTMSyncStatusHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    lenient().when(teamMember.getEntity()).thenReturn(teamMemberEntity);
    lenient().when(teamMember.getCopilotApp()).thenReturn(copilotApp);
    lenient().when(teamMemberEntity.getProperty(TeamMember.PROPERTY_MEMBER)).thenReturn(memberProperty);
    lenient().when(teamMemberEntity.getProperty(TeamMember.PROPERTY_COPILOTAPP)).thenReturn(appProperty);
  }

  private void setupModelProviderMocks() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(TeamMember.class)).thenReturn(teamMemberEntity);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedAppInfoUtils.close();
    mockedCopilotUtils.close();
  }

  /** Test on update member changed. */
  @Test
  public void testOnUpdateMemberChanged() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(updateEvent.getPreviousState(memberProperty)).thenReturn("oldMember");
    when(updateEvent.getCurrentState(memberProperty)).thenReturn("newMember");
    when(updateEvent.getPreviousState(appProperty)).thenReturn("sameApp");
    when(updateEvent.getCurrentState(appProperty)).thenReturn("sameApp");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on update app changed. */
  @Test
  public void testOnUpdateAppChanged() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(updateEvent.getPreviousState(memberProperty)).thenReturn("sameMember");
    when(updateEvent.getCurrentState(memberProperty)).thenReturn("sameMember");
    when(updateEvent.getPreviousState(appProperty)).thenReturn("oldApp");
    when(updateEvent.getCurrentState(appProperty)).thenReturn("newApp");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on update nothing changed. */
  @Test
  public void testOnUpdateNothingChanged() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(updateEvent.getPreviousState(memberProperty)).thenReturn("same");
    when(updateEvent.getCurrentState(memberProperty)).thenReturn("same");
    when(updateEvent.getPreviousState(appProperty)).thenReturn("same");
    when(updateEvent.getCurrentState(appProperty)).thenReturn("same");

    handler.onUpdate(updateEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(any(CopilotApp.class)), never());
  }

  /** Test on save. */
  @Test
  public void testOnSave() {
    when(newEvent.getTargetInstance()).thenReturn(teamMember);

    handler.onSave(newEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }

  /** Test on delete. */
  @Test
  public void testOnDelete() {
    when(deleteEvent.getTargetInstance()).thenReturn(teamMember);

    handler.onDelete(deleteEvent);

    mockedAppInfoUtils.verify(
        () -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
  }
}
