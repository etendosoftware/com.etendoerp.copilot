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
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;

/**
 * Handles synchronization status updates for the Copilot application whenever
 * certain events occur on CopilotAppTool entities, such as creation, update, or deletion.
 */
public class AssistantToolSyncStatusHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotAppTool.class)
  };

  protected Logger logger = Logger.getLogger(AssistantToolSyncStatusHandler.class);

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
   * Handles the update event for CopilotAppTool entities. If the CopilotTool property
   * of the CopilotAppTool entity has changed, it updates the synchronization status
   * of the associated CopilotApp to 'Pending Synchronization'.
   *
   * @param event the entity update event to be observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppTool currentAppTool = (CopilotAppTool) event.getTargetInstance();
    Object previousValue = event.getPreviousState(currentAppTool.getEntity().getProperty(CopilotAppTool.PROPERTY_COPILOTTOOL));
    Object currentValue = event.getCurrentState(currentAppTool.getEntity().getProperty(CopilotAppTool.PROPERTY_COPILOTTOOL));
    if (!Objects.equals(previousValue, currentValue)) {
      changeAssistantStatus(currentAppTool);
      CopilotUtils.logIfDebug("The AppTool was updated and the sync status of " + currentAppTool.getCopilotApp() + " changed to PS");
    }
  }

  /**
   * Handles the save event for CopilotAppTool entities. When a new CopilotAppTool entity
   * is saved, it updates the synchronization status of the associated CopilotApp to
   * 'Pending Synchronization'.
   *
   * @param event the entity save event to be observed
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppTool currentAppTool = (CopilotAppTool) event.getTargetInstance();
    changeAssistantStatus(currentAppTool);
    CopilotUtils.logIfDebug("The AppTool was saved and the sync status of " + currentAppTool.getCopilotApp() + " changed to PS");
  }

  /**
   * Handles the delete event for CopilotAppTool entities. When a CopilotAppTool entity
   * is deleted, it updates the synchronization status of the associated CopilotApp to
   * 'Pending Synchronization'.
   *
   * @param event the entity delete event to be observed
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppTool currentAppTool = (CopilotAppTool) event.getTargetInstance();
    changeAssistantStatus(currentAppTool);
    CopilotUtils.logIfDebug("The AppTool was deleted and the sync status of " + currentAppTool.getCopilotApp() + " changed to PS");
  }

  /**
   * Updates the synchronization status of the CopilotApp entity associated with the
   * given CopilotAppTool to 'Pending Synchronization'.
   *
   * @param currentAppTool the CopilotAppTool entity for which to update the associated CopilotApp
   */
  private static void changeAssistantStatus(CopilotAppTool currentAppTool) {
    CopilotApp currentAssistant = currentAppTool.getCopilotApp();
    CopilotAppInfoUtils.markAsPendingSynchronization(currentAssistant);
  }
}
