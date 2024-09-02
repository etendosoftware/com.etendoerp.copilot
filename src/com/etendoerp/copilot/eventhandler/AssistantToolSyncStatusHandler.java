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
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.util.CopilotConstants;


public class AssistantToolSyncStatusHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotAppTool.class) };
  protected Logger logger = Logger.getLogger(AssistantToolSyncStatusHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    /*final CopilotAppTool currentAppTool = (CopilotAppTool) event.getTargetInstance();
    final Entity appEntity = currentAppTool.getCopilotApp().getEntity();
    final Property syncStatusProp = appEntity.getProperty(CopilotApp.PROPERTY_SYNCSTATUS);
    Object previousTool = appEntity.getProperty()
    if (!Objects.equals(previousTool, ))

    if (checkPropertiesChanged(event, appEntity)) {
      event.setCurrentState(syncStatusProp, CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
    } else {
      event.setCurrentState(syncStatusProp, CopilotConstants.SYNCHRONIZED_STATE);
    }*/
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
}
