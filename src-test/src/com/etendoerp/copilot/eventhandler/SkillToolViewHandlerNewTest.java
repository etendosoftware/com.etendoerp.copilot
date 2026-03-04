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

  @Before
  public void setUp() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);

    lenient().when(modelProvider.getEntity(CopilotAppTool.class)).thenReturn(entity);
    lenient().when(modelProvider.getEntity(TeamMember.class)).thenReturn(entity);
    lenient().when(obContext.getCurrentClient()).thenReturn(currentClient);

    handler = new SkillToolViewHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  @After
  public void tearDown() {
    mockedOBDal.close();
    mockedModelProvider.close();
    mockedOBContext.close();
    mockedOBMessageUtils.close();
  }

  @Test
  public void testOnUpdateCopilotAppToolSameClient() {
    String clientId = "100";
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(objClient);
    when(objClient.getId()).thenReturn(clientId);
    when(currentClient.getId()).thenReturn(clientId);
    when(obDal.get(Client.class, clientId)).thenReturn(objClient);

    handler.onUpdate(updateEvent);
    // No exception means same client check passed
  }

  @Test
  public void testOnUpdateTeamMemberSameClient() {
    String clientId = "100";
    when(updateEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(objClient);
    when(objClient.getId()).thenReturn(clientId);
    when(currentClient.getId()).thenReturn(clientId);
    when(obDal.get(Client.class, clientId)).thenReturn(objClient);

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateDifferentClientThrows() {
    Client diffClient = mock(Client.class);
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(diffClient);
    when(diffClient.getId()).thenReturn("200");
    when(currentClient.getId()).thenReturn("100");
    when(obDal.get(Client.class, "200")).thenReturn(diffClient);
    when(diffClient.getName()).thenReturn("Other Client");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_errorClient"))
        .thenReturn("Error client");

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateNullObjClientThrows() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(objClient);
    when(objClient.getId()).thenReturn("999");
    when(obDal.get(Client.class, "999")).thenReturn(null);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_errorClient"))
        .thenReturn("Error client");

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnSaveSameClient() {
    String clientId = "100";
    when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(objClient);
    when(objClient.getId()).thenReturn(clientId);
    when(currentClient.getId()).thenReturn(clientId);
    when(obDal.get(Client.class, clientId)).thenReturn(objClient);

    handler.onSave(newEvent);
  }

  @Test
  public void testOnDeleteSameClient() {
    String clientId = "100";
    when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(objClient);
    when(objClient.getId()).thenReturn(clientId);
    when(currentClient.getId()).thenReturn(clientId);
    when(obDal.get(Client.class, clientId)).thenReturn(objClient);

    handler.onDelete(deleteEvent);
  }

  @Test(expected = OBException.class)
  public void testOnSaveDifferentClientThrows() {
    Client diffClient = mock(Client.class);
    when(newEvent.getTargetInstance()).thenReturn(teamMember);
    when(teamMember.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(diffClient);
    when(diffClient.getId()).thenReturn("200");
    when(currentClient.getId()).thenReturn("100");
    when(obDal.get(Client.class, "200")).thenReturn(diffClient);
    when(diffClient.getName()).thenReturn("Other");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_errorClient"))
        .thenReturn("Error");

    handler.onSave(newEvent);
  }

  @Test(expected = OBException.class)
  public void testOnDeleteDifferentClientThrows() {
    Client diffClient = mock(Client.class);
    when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);
    when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
    when(copilotApp.getClient()).thenReturn(diffClient);
    when(diffClient.getId()).thenReturn("200");
    when(currentClient.getId()).thenReturn("100");
    when(obDal.get(Client.class, "200")).thenReturn(diffClient);
    when(diffClient.getName()).thenReturn("Other");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_errorClient"))
        .thenReturn("Error");

    handler.onDelete(deleteEvent);
  }
}
