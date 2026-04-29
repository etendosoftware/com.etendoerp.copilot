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
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.TeamMember;

/**
 * Unit tests for SkillToolViewHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class SkillToolViewHandlerNewTest {
  private static final String ETCOP_ERRORCLIENT = "ETCOP_errorClient";
  private static final String SKILL_VIEW_DEFAULT_CLIENT = "100";

  private SkillToolViewHandler handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private CopilotAppTool copilotAppTool;
  @Mock private TeamMember teamMember;
  @Mock private CopilotApp copilotApp;
  @Mock private Client currentClient;
  @Mock private Client objClient;
  @Mock private OBDal obDal;
  @Mock private OBContext obContext;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;

  /** Set up. */
  @Before
  public void setUp() {
    openSkillToolViewStaticMocks();
    wireSkillToolViewProviders();

    handler = new SkillToolViewHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  private void openSkillToolViewStaticMocks() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
  }

  private void wireSkillToolViewProviders() {
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(obContext.getCurrentClient()).thenReturn(currentClient);
    lenient().when(modelProvider.getEntity(CopilotAppTool.class)).thenReturn(entity);
    lenient().when(modelProvider.getEntity(TeamMember.class)).thenReturn(entity);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedOBDal.close();
    mockedOBMessageUtils.close();
    mockedOBContext.close();
  }

  /** Test on update copilot app tool same client. */
  @Test
  public void testOnUpdateCopilotAppToolSameClient() {
    setupSameClientEvent(updateEvent, copilotAppTool);

    handler.onUpdate(updateEvent);
    // No exception means same client check passed
  }

  /** Test on update team member same client. */
  @Test
  public void testOnUpdateTeamMemberSameClient() {
    setupSameClientEvent(updateEvent, teamMember);

    handler.onUpdate(updateEvent);
  }

  /** Test on update different client throws. */
  @Test(expected = OBException.class)
  public void testOnUpdateDifferentClientThrows() {
    setupDifferentClientEvent(updateEvent, copilotAppTool, "Other Client");

    handler.onUpdate(updateEvent);
  }

  /** Test on update null obj client throws. */
  @Test(expected = OBException.class)
  public void testOnUpdateNullObjClientThrows() {
    setupNullClientEvent(updateEvent);

    handler.onUpdate(updateEvent);
  }

  /** Test on save same client. */
  @Test
  public void testOnSaveSameClient() {
    setupSameClientEvent(newEvent, copilotAppTool);

    handler.onSave(newEvent);
  }

  /** Test on delete same client. */
  @Test
  public void testOnDeleteSameClient() {
    setupSameClientEvent(deleteEvent, copilotAppTool);

    handler.onDelete(deleteEvent);
  }

  /** Test on save different client throws. */
  @Test(expected = OBException.class)
  public void testOnSaveDifferentClientThrows() {
    setupDifferentClientEvent(newEvent, teamMember, "Other");

    handler.onSave(newEvent);
  }

  /** Test on delete different client throws. */
  @Test(expected = OBException.class)
  public void testOnDeleteDifferentClientThrows() {
    setupDifferentClientEvent(deleteEvent, copilotAppTool, "Other");

    handler.onDelete(deleteEvent);
  }

  private void setupSameClientEvent(EntityPersistenceEvent event, Object targetInstance) {
    when(event.getTargetInstance()).thenReturn((BaseOBObject) targetInstance);
    configureCopilotAppFromTarget(targetInstance);
    when(copilotApp.getClient()).thenReturn(objClient);
    when(objClient.getId()).thenReturn(SKILL_VIEW_DEFAULT_CLIENT);
    when(currentClient.getId()).thenReturn(SKILL_VIEW_DEFAULT_CLIENT);
    when(obDal.get(Client.class, SKILL_VIEW_DEFAULT_CLIENT)).thenReturn(objClient);
  }

  private void configureCopilotAppFromTarget(Object targetInstance) {
    if (targetInstance instanceof CopilotAppTool) {
      when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    } else {
      when(teamMember.getCopilotApp()).thenReturn(copilotApp);
    }
  }

  private void setupDifferentClientEvent(EntityPersistenceEvent event, Object targetInstance,
      String clientName) {
    when(event.getTargetInstance()).thenReturn((BaseOBObject) targetInstance);
    configureCopilotAppFromTarget(targetInstance);
    Client diffClient = mock(Client.class);
    when(copilotApp.getClient()).thenReturn(diffClient);
    when(currentClient.getId()).thenReturn(SKILL_VIEW_DEFAULT_CLIENT);
    when(diffClient.getId()).thenReturn("200");
    when(obDal.get(Client.class, "200")).thenReturn(diffClient);
    when(diffClient.getName()).thenReturn(clientName);
    mockedOBMessageUtils.when(
        () -> OBMessageUtils.messageBD(ETCOP_ERRORCLIENT)).thenReturn("Error client");
  }

  private void setupNullClientEvent(EntityPersistenceEvent event) {
    when(event.getTargetInstance()).thenReturn(copilotAppTool);
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(objClient);
    when(objClient.getId()).thenReturn("999");
    when(obDal.get(Client.class, "999")).thenReturn(null);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(ETCOP_ERRORCLIENT))
        .thenReturn("Error client");
  }
}
