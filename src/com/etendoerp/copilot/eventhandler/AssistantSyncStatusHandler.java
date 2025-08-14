package com.etendoerp.copilot.eventhandler;

import java.util.Objects;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * This class handles synchronization status updates for CopilotApp entities.
 * It observes and reacts to create, update, and delete events for the CopilotApp entity.
 */
public class AssistantSyncStatusHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotApp.class)
  };

  protected Logger logger = Logger.getLogger(AssistantSyncStatusHandler.class);

  /**
   * Returns the entities that this observer listens to.
   *
   * @return an array of entities observed by this handler
   */
  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  /**
   * Handles the update event for CopilotApp entities. If any important properties
   * of the CopilotApp entity have been modified, it sets the synchronization status
   * of the CopilotApp to 'Pending Synchronization'.
   *
   * @param event the entity update event to be observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final Entity appEntity = ModelProvider.getInstance().getEntity(CopilotApp.ENTITY_NAME);
    if (checkPropertiesChanged(event, appEntity)) {
      CopilotAppInfoUtils.markAsPendingSynchronization((CopilotApp) event.getTargetInstance());

      CopilotUtils.logIfDebug("An important property was changed and the sync status changed to PS");
    }
  }

  /**
   * Handles the save event for CopilotApp entities. This method currently
   * does not perform any specific actions on save.
   *
   * @param event the entity save event to be observed
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  /**
   * Handles the delete event for CopilotApp entities. This method currently
   * does not perform any specific actions on delete.
   *
   * @param event the entity delete event to be observed
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  /**
   * Checks if any of the monitored properties of the CopilotApp entity have changed.
   *
   * @param event the entity update event
   * @param appEntity the CopilotApp entity to be checked
   * @return true if any of the monitored properties have changed, false otherwise
   */
  private static boolean checkPropertiesChanged(EntityUpdateEvent event, Entity appEntity) {
    String[] properties = {
        CopilotApp.PROPERTY_PROMPT,
        CopilotApp.PROPERTY_NAME,
        CopilotApp.PROPERTY_APPTYPE,
        CopilotApp.PROPERTY_ORGANIZATION,
        CopilotApp.PROPERTY_DESCRIPTION,
        CopilotApp.PROPERTY_MODULE,
        CopilotApp.PROPERTY_SYSTEMAPP,
        CopilotApp.PROPERTY_PROVIDER,
        CopilotApp.PROPERTY_MODEL,
        CopilotApp.PROPERTY_TEMPERATURE
    };

    for (String property : properties) {
      Object previousValue = event.getPreviousState(appEntity.getProperty(property));
      Object currentValue = event.getCurrentState(appEntity.getProperty(property));

      if (!Objects.equals(previousValue, currentValue)) {
        return true;
      }
    }

    return false;
  }
}
