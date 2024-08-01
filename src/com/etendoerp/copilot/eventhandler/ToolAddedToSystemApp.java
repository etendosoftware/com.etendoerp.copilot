package com.etendoerp.copilot.eventhandler;

import java.util.List;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotRoleApp;


public class ToolAddedToSystemApp extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotAppTool.ENTITY_NAME) };
  protected Logger logger = Logger.getLogger(ToolAddedToSystemApp.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppTool currentAppTool = (CopilotAppTool) event.getTargetInstance();
    checkRightClient(currentAppTool);
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppTool currentAppTool = (CopilotAppTool) event.getTargetInstance();
    checkRightClient(currentAppTool);
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  private static void checkRightClient(CopilotAppTool currentAppTool) {
    CopilotApp currentAssistant = currentAppTool.getCopilotApp();
    String currentClient = currentAssistant.getClient().getName();

    String contextClient = OBContext.getOBContext().getCurrentClient().getName();

    if (!StringUtils.equals(contextClient, currentClient)) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_WrongClientApp"));
    }
  }
}
