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

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.TeamMember;

/**
 * Unit tests for TeamMemberValidations.
 */
@RunWith(MockitoJUnitRunner.class)
public class TeamMemberValidationsNewTest {
  private static final String ETCOP_TEAMMEMBERDESC = "ETCOP_TeamMemberDesc";
  private static final String MEMBER_S_HAS_NO_DESCRIPTION = "Member %s has no description";
  private static final String TESTMEMBER = "TestMember";


  private TeamMemberValidations handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private TeamMember teamMember;
  @Mock private CopilotApp memberApp;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;

  /** Set up. */
  @Before
  public void setUp() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(TeamMember.class)).thenReturn(entity);

    handler = new TeamMemberValidations() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedOBMessageUtils.close();
  }

  /** Test on update valid member. */
  @Test
  public void testOnUpdateValidMember() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn("A valid description");

    handler.onUpdate(updateEvent);
  }

  /** Test on update null member. */
  @Test(expected = OBException.class)
  public void testOnUpdateNullMember() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(null);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberNull"))
        .thenReturn("Team member is null");

    handler.onUpdate(updateEvent);
  }

  /** Test on update empty description. */
  @Test(expected = OBException.class)
  public void testOnUpdateEmptyDescription() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn("");
    when(memberApp.getName()).thenReturn(TESTMEMBER);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(ETCOP_TEAMMEMBERDESC))
        .thenReturn(MEMBER_S_HAS_NO_DESCRIPTION);

    handler.onUpdate(updateEvent);
  }

  /** Test on update null description. */
  @Test(expected = OBException.class)
  public void testOnUpdateNullDescription() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn(null);
    when(memberApp.getName()).thenReturn(TESTMEMBER);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(ETCOP_TEAMMEMBERDESC))
        .thenReturn(MEMBER_S_HAS_NO_DESCRIPTION);

    handler.onUpdate(updateEvent);
  }

  /** Test on save valid member. */
  @Test
  public void testOnSaveValidMember() {
    when(newEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn("Description");

    handler.onSave(newEvent);
  }

  /** Test on save null member. */
  @Test(expected = OBException.class)
  public void testOnSaveNullMember() {
    when(newEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(null);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberNull"))
        .thenReturn("Team member is null");

    handler.onSave(newEvent);
  }

  /** Test on save empty description. */
  @Test(expected = OBException.class)
  public void testOnSaveEmptyDescription() {
    when(newEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn("");
    when(memberApp.getName()).thenReturn(TESTMEMBER);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(ETCOP_TEAMMEMBERDESC))
        .thenReturn(MEMBER_S_HAS_NO_DESCRIPTION);

    handler.onSave(newEvent);
  }
}
