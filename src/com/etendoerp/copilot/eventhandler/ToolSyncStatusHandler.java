package com.etendoerp.copilot.eventhandler;

import java.util.Objects;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;


public class ToolSyncStatusHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotTool.class) };
  protected Logger logger = Logger.getLogger(ToolSyncStatusHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotTool currentTool = (CopilotTool) event.getTargetInstance();
    

  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotTool currentTool = (CopilotTool) event.getTargetInstance();
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotTool currentTool = (CopilotTool) event.getTargetInstance();
  }


  private static boolean checkToolPropertiesChanged(EntityUpdateEvent event, Entity appEntity) {
    String[] properties = {
        CopilotTool.PROPERTY_ACTIVE,
        CopilotTool.PROPERTY_JSONSTRUCTURE,
        CopilotTool.PROPERTY_DESCRIPTION,
        CopilotTool.PROPERTY_MODULE,
        CopilotTool.PROPERTY_NAME,
        CopilotTool.PROPERTY_VALUE,
        CopilotTool.PROPERTY_ORGANIZATION
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
