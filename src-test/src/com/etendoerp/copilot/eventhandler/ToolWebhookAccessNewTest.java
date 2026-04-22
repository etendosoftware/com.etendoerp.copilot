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
  private static final String NOT_SYS_ADMIN = "Not sys admin";
  private static final String SMFWHE_ERRORSYSADMINROLE = "smfwhe_errorSysAdminRole";
  private static final String WEBHOOK_SYS_CLIENT_ID = "0";

  private ToolWebhookAccess handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<ModelProvider> mockedModelProvider;
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

  /** Set up. */
  @Before
  public void setUp() {
    initWebhookAccessStaticMocks();
    configureWebhookAccessCoreMocks();

    handler = new ToolWebhookAccess() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  private void initWebhookAccessStaticMocks() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
  }

  private void configureWebhookAccessCoreMocks() {
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    lenient().when(modelProvider.getEntity(ToolWebhook.class)).thenReturn(entity);
    lenient().when(obDal.get(Client.class, WEBHOOK_SYS_CLIENT_ID)).thenReturn(sysClient);
    lenient().when(obContext.getCurrentClient()).thenReturn(currentClient);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedOBMessageUtils.close();
    mockedOBContext.close();
    mockedOBDal.close();
    mockedModelProvider.close();
  }

  /** Test on update sys admin. */
  @Test
  public void testOnUpdateSysAdmin() {
    setupSysAdminClient();

    handler.onUpdate(updateEvent);
    // No exception
  }

  /** Test on update non sys admin. */
  @Test(expected = OBException.class)
  public void testOnUpdateNonSysAdmin() {
    setupNonSysAdminClient();

    handler.onUpdate(updateEvent);
  }

  /** Test on save sys admin. */
  @Test
  public void testOnSaveSysAdmin() {
    setupSysAdminClient();

    handler.onSave(newEvent);
  }

  /** Test on save non sys admin. */
  @Test(expected = OBException.class)
  public void testOnSaveNonSysAdmin() {
    setupNonSysAdminClient();

    handler.onSave(newEvent);
  }

  /** Test on delete sys admin. */
  @Test
  public void testOnDeleteSysAdmin() {
    setupSysAdminClient();

    handler.onDelete(deleteEvent);
  }

  /** Test on delete non sys admin. */
  @Test(expected = OBException.class)
  public void testOnDeleteNonSysAdmin() {
    setupNonSysAdminClient();

    handler.onDelete(deleteEvent);
  }

  private void setupSysAdminClient() {
    when(sysClient.getId()).thenReturn(WEBHOOK_SYS_CLIENT_ID);
    when(currentClient.getId()).thenReturn(WEBHOOK_SYS_CLIENT_ID);
  }

  private void setupNonSysAdminClient() {
    when(sysClient.getId()).thenReturn(WEBHOOK_SYS_CLIENT_ID);
    when(currentClient.getId()).thenReturn("100");
    mockedOBMessageUtils.when(
        () -> OBMessageUtils.messageBD(SMFWHE_ERRORSYSADMINROLE)).thenReturn(NOT_SYS_ADMIN);
  }
}
