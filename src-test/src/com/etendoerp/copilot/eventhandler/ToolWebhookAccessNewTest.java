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
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.ToolWebhook;

/**
 * Unit tests for ToolWebhookAccess.
 */
@RunWith(MockitoJUnitRunner.class)
public class ToolWebhookAccessNewTest {

  private ToolWebhookAccess handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private OBDal obDal;
  @Mock private OBContext obContext;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;
  @Mock private Client currentClient;
  @Mock private Client sysClient;

  @Before
  public void setUp() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    lenient().when(modelProvider.getEntity(ToolWebhook.class)).thenReturn(entity);
    lenient().when(obContext.getCurrentClient()).thenReturn(currentClient);
    lenient().when(obDal.get(Client.class, "0")).thenReturn(sysClient);

    handler = new ToolWebhookAccess() {
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
  public void testOnUpdateSysAdmin() {
    when(currentClient.getId()).thenReturn("0");
    when(sysClient.getId()).thenReturn("0");

    handler.onUpdate(updateEvent);
    // No exception
  }

  @Test(expected = OBException.class)
  public void testOnUpdateNonSysAdmin() {
    when(currentClient.getId()).thenReturn("100");
    when(sysClient.getId()).thenReturn("0");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("smfwhe_errorSysAdminRole"))
        .thenReturn("Not sys admin");

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnSaveSysAdmin() {
    when(currentClient.getId()).thenReturn("0");
    when(sysClient.getId()).thenReturn("0");

    handler.onSave(newEvent);
  }

  @Test(expected = OBException.class)
  public void testOnSaveNonSysAdmin() {
    when(currentClient.getId()).thenReturn("100");
    when(sysClient.getId()).thenReturn("0");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("smfwhe_errorSysAdminRole"))
        .thenReturn("Not sys admin");

    handler.onSave(newEvent);
  }

  @Test
  public void testOnDeleteSysAdmin() {
    when(currentClient.getId()).thenReturn("0");
    when(sysClient.getId()).thenReturn("0");

    handler.onDelete(deleteEvent);
  }

  @Test(expected = OBException.class)
  public void testOnDeleteNonSysAdmin() {
    when(currentClient.getId()).thenReturn("100");
    when(sysClient.getId()).thenReturn("0");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("smfwhe_errorSysAdminRole"))
        .thenReturn("Not sys admin");

    handler.onDelete(deleteEvent);
  }
}
