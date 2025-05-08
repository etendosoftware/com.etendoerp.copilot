package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.commons.lang3.StringUtils;
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

/**
 * Event handler for ToolWebhook entity.
 * This class observes the persistence events (create, update, delete) for the ToolWebhook entity
 * and ensures that only the system administrator can perform these operations.
 */
public class ToolWebhookAccess extends EntityPersistenceEventObserver {
  private static Entity[] entities = { ModelProvider.getInstance().getEntity(ToolWebhook.class) };
  protected Logger logger = Logger.getLogger(ToolWebhookAccess.class);

  /**
   * Returns the list of entities observed by this event handler.
   *
   * @return An array of observed entities.
   */
  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  /**
   * Handles the update event for the ToolWebhook entity.
   * This method checks if the event is valid and then verifies the system administrator role.
   *
   * @param event
   *     The entity update event.
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    checkSysAdminRole();
  }

  /**
   * Handles the save event for the ToolWebhook entity.
   * This method checks if the event is valid and then verifies the system administrator role.
   *
   * @param event
   *     The entity new event.
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    checkSysAdminRole();
  }

  /**
   * Handles the delete event for the ToolWebhook entity.
   * This method checks if the event is valid and then verifies the system administrator role.
   *
   * @param event
   *     The entity delete event.
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    checkSysAdminRole();
  }

  /**
   * Checks if the current user has the system administrator role.
   * If the current user is not the system administrator, an OBException is thrown.
   *
   * @throws OBException
   *     If the current user is not the system administrator.
   */
  private void checkSysAdminRole() {
    Client currentClient = OBContext.getOBContext().getCurrentClient();
    Client sysClient = OBDal.getInstance().get(Client.class, "0");
    if (!StringUtils.equalsIgnoreCase(currentClient.getId(), sysClient.getId())) {
      throw new OBException(OBMessageUtils.messageBD("smfwhe_errorSysAdminRole"));
    }
  }
}