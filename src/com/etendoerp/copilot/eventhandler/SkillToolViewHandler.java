package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
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

/**
 * Event handler for SkillToolView.
 * This class observes the persistence events (create, update, delete) for the CopilotAppTool and TeamMember entities
 * and ensures that only the system administrator can perform these operations.
 */
public class SkillToolViewHandler extends EntityPersistenceEventObserver {
  private static final Entity[] entities = { ModelProvider.getInstance().getEntity(
      CopilotAppTool.class), ModelProvider.getInstance().getEntity(
      TeamMember.class) };
  protected Logger logger = Logger.getLogger(SkillToolViewHandler.class);

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
   * Handles the update event for the observed entities.
   * This method checks if the event is valid and then verifies the system administrator role.
   *
   * @param event
   *     The entity update event.
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    checkSameClient(event);
  }

  /**
   * Retrieves the client ID from the event.
   * This method extracts the client ID from the target instance of the event.
   *
   * @param event
   *     The entity persistence event.
   * @return The client ID.
   */
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

  /**
   * Handles the save event for the observed entities.
   * This method checks if the event is valid and then verifies the system administrator role.
   *
   * @param event
   *     The entity new event.
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    checkSameClient(event);
  }

  /**
   * Handles the delete event for the observed entities.
   * This method checks if the event is valid and then verifies the system administrator role.
   *
   * @param event
   *     The entity delete event.
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    checkSameClient(event);
  }

  /**
   * Checks if the current user has the system administrator role.
   * If the current user is not the system administrator, an OBException is thrown.
   *
   * @param event
   *     The entity delete event.
   * @throws OBException
   *     If the current user is not the system administrator.
   */
  private void checkSameClient(EntityPersistenceEvent event) {
    String clientID = getClientID(event);
    Client currentClient = OBContext.getOBContext().getCurrentClient();
    Client objClient = OBDal.getInstance().get(Client.class, clientID);
    if (objClient == null || !StringUtils.equalsIgnoreCase(currentClient.getId(), objClient.getId())) {
      String name = objClient == null ? "" : objClient.getName();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_errorClient"));
    }
  }
}
