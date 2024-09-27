package com.etendoerp.copilot.eventhandler;


import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.ToolWebhook;


public class ToolWebhookAccess extends EntityPersistenceEventObserver {
  private static Entity[] entities = { ModelProvider.getInstance().getEntity(ToolWebhook.class) };
  protected Logger logger = Logger.getLogger(ToolWebhookAccess.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    checkSysAdminRole();
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    checkSysAdminRole();
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    checkSysAdminRole();
  }

  private void checkSysAdminRole() {
    Client currentClient = OBContext.getOBContext().getCurrentClient();
    Client sysClient = OBDal.getInstance().get(Client.class, "0");
    if (!StringUtils.equalsIgnoreCase(currentClient.getId(), sysClient.getId())) {
      throw new OBException(OBMessageUtils.messageBD("smfwhe_errorSysAdminRole"));
    }
  }
}