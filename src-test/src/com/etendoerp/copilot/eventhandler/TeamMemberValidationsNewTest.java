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

  private TeamMemberValidations handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private TeamMember teamMember;
  @Mock private CopilotApp memberApp;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;

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

  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedOBMessageUtils.close();
  }

  @Test
  public void testOnUpdateValidMember() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn("A valid description");

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateNullMember() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(null);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberNull"))
        .thenReturn("Team member is null");

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateEmptyDescription() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn("");
    when(memberApp.getName()).thenReturn("TestMember");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberDesc"))
        .thenReturn("Member %s has no description");

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateNullDescription() {
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn(null);
    when(memberApp.getName()).thenReturn("TestMember");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberDesc"))
        .thenReturn("Member %s has no description");

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnSaveValidMember() {
    when(newEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn("Description");

    handler.onSave(newEvent);
  }

  @Test(expected = OBException.class)
  public void testOnSaveNullMember() {
    when(newEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(null);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberNull"))
        .thenReturn("Team member is null");

    handler.onSave(newEvent);
  }

  @Test(expected = OBException.class)
  public void testOnSaveEmptyDescription() {
    when(newEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getMember()).thenReturn(memberApp);
    when(memberApp.getDescription()).thenReturn("");
    when(memberApp.getName()).thenReturn("TestMember");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberDesc"))
        .thenReturn("Member %s has no description");

    handler.onSave(newEvent);
  }
}
