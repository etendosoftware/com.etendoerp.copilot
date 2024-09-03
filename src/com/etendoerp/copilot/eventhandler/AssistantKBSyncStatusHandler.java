package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;


public class AssistantKBSyncStatusHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotAppSource.class) };
  protected Logger logger = Logger.getLogger(AssistantKBSyncStatusHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppSource currentAppSource = (CopilotAppSource) event.getTargetInstance();
    Object previousValue = event.getPreviousState(currentAppSource.getEntity().getProperty(CopilotAppSource.PROPERTY_FILE));
    Object currentValue = event.getCurrentState(currentAppSource.getEntity().getProperty(CopilotAppSource.PROPERTY_FILE));
    if (previousValue != currentValue) {
      CopilotApp currentAssistant = currentAppSource.getEtcopApp();
      currentAssistant.setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
      CopilotUtils.logIfDebug("The register was updated and the sync status changed to PS");
    }
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppSource currentAppSource = (CopilotAppSource) event.getTargetInstance();
    CopilotApp currentAssistant = currentAppSource.getEtcopApp();
    currentAssistant.setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
    OBDal.getInstance().save(currentAssistant);
    CopilotUtils.logIfDebug("The register was saved and the sync status changed to PS");
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppSource currentAppSource = (CopilotAppSource) event.getTargetInstance();
    CopilotApp currentAssistant = currentAppSource.getEtcopApp();
    currentAssistant.setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
    OBDal.getInstance().save(currentAssistant);
    CopilotUtils.logIfDebug("The register was deleted and the sync status changed to PS");
  }
}
