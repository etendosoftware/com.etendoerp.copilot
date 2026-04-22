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
  private static final String TOOL_ADDED_SAME_CLIENT = "100";
  private static final String TOOL_ADDED_DIFF_CLIENT = "200";

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

  /** Set up. */
  @Before
  public void setUp() {
    bootstrapToolAddedMocks();
    lenient().when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    lenient().when(copilotApp.getClient()).thenReturn(appClient);

    handler = new ToolAddedToSystemApp() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  private void bootstrapToolAddedMocks() {
    mockedOBContext = mockStatic(OBContext.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(obContext.getCurrentClient()).thenReturn(contextClient);
    lenient().when(modelProvider.getEntity(CopilotAppTool.ENTITY_NAME)).thenReturn(entity);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedOBContext.close();
    mockedOBMessageUtils.close();
    mockedModelProvider.close();
  }

  /** Test on update same client. */
  @Test
  public void testOnUpdateSameClient() {
    configureToolAddedSameClient(updateEvent);
    handler.onUpdate(updateEvent);
  }

  /** Test on update different client. */
  @Test(expected = OBException.class)
  public void testOnUpdateDifferentClient() {
    configureToolAddedDiffClient(updateEvent);
    handler.onUpdate(updateEvent);
  }

  /** Test on save same client. */
  @Test
  public void testOnSaveSameClient() {
    configureToolAddedSameClient(newEvent);
    handler.onSave(newEvent);
  }

  /** Test on save different client. */
  @Test(expected = OBException.class)
  public void testOnSaveDifferentClient() {
    configureToolAddedDiffClient(newEvent);
    handler.onSave(newEvent);
  }

  /** Test on delete does nothing. */
  @Test
  public void testOnDeleteDoesNothing() {
    lenient().when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);

    handler.onDelete(deleteEvent);
    // No client check on delete
  }

  private void configureToolAddedSameClient(EntityPersistenceEvent event) {
    when(event.getTargetInstance()).thenReturn(copilotAppTool);
    when(appClient.getId()).thenReturn(TOOL_ADDED_SAME_CLIENT);
    when(contextClient.getId()).thenReturn(TOOL_ADDED_SAME_CLIENT);
  }

  private void configureToolAddedDiffClient(EntityPersistenceEvent event) {
    when(event.getTargetInstance()).thenReturn(copilotAppTool);
    when(appClient.getId()).thenReturn(TOOL_ADDED_SAME_CLIENT);
    when(contextClient.getId()).thenReturn(TOOL_ADDED_DIFF_CLIENT);
    mockedOBMessageUtils.when(
        () -> OBMessageUtils.messageBD("ETCOP_WrongClientApp")).thenReturn("Wrong client");
  }
}
