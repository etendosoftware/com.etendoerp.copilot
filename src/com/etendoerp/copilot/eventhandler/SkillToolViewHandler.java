package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.data.ToolWebhook;


public class SkillToolViewHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = { ModelProvider.getInstance().getEntity(
      CopilotAppTool.class), ModelProvider.getInstance().getEntity(
      TeamMember.class) };
  protected Logger logger = Logger.getLogger(SkillToolViewHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    String clientID = getClientID(event);
    checkSysAdminRole(clientID);
  }

  private String getClientID(EntityPersistenceEvent event) {
    var obj = event.getTargetInstance();
    String clientID = null;
    if (obj instanceof CopilotAppTool) {
      clientID = ((CopilotAppTool) obj).getCopilotApp().getClient().getId();
    } else if (obj instanceof TeamMember) {
      clientID = ((TeamMember) obj).getCopilotApp().getClient().getId();
    }
    return clientID;
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    String clientID = getClientID(event);
    checkSysAdminRole(clientID);
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    String clientID = getClientID(event);
    checkSysAdminRole(clientID);
  }

  private void checkSysAdminRole(String clientID) {
    Client currentClient = OBContext.getOBContext().getCurrentClient();
    Client objClient = OBDal.getInstance().get(Client.class, clientID);
    if (objClient == null || !StringUtils.equalsIgnoreCase(currentClient.getId(), objClient.getId())) {
      String name = objClient == null ? "" : objClient.getName();
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_errorClient"), name));
    }
  }
}
