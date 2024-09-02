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
import com.etendoerp.copilot.util.CopilotConstants;


public class AssistantSyncStatusHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotApp.class) };
  protected Logger logger = Logger.getLogger(AssistantSyncStatusHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final Entity appEntity = ModelProvider.getInstance().getEntity(CopilotApp.ENTITY_NAME);
    final Property syncStatusProp = appEntity.getProperty(CopilotApp.PROPERTY_SYNCSTATUS);
    if (checkPropertiesChanged(event, appEntity)) {
      event.setCurrentState(syncStatusProp, CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
    } else {
      event.setCurrentState(syncStatusProp, CopilotConstants.SYNCHRONIZED_STATE);
    }
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

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
