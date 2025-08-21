package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;

/**
 * This class handles the synchronization status updates for CopilotAppSource entities.
 * It observes and reacts to create, update, and delete events for the CopilotAppSource entity.
 */
public class AssistantKBSyncStatusHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotAppSource.class)
  };

  protected Logger logger = Logger.getLogger(AssistantKBSyncStatusHandler.class);

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
   * Handles the update event for CopilotAppSource entities. If the file property of the
   * CopilotAppSource entity has been modified, it sets the synchronization status of
   * the associated CopilotApp to 'Pending Synchronization'.
   *
   * @param event
   *     the entity update event to be observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppSource currentAppSource = (CopilotAppSource) event.getTargetInstance();
    Object previousValue = event.getPreviousState(
        currentAppSource.getEntity().getProperty(CopilotAppSource.PROPERTY_FILE));
    Object currentValue = event.getCurrentState(
        currentAppSource.getEntity().getProperty(CopilotAppSource.PROPERTY_FILE));
    if (!previousValue.equals(currentValue)) {
      changeAssistantStatus(currentAppSource);
      CopilotUtils.logIfDebug("The register was updated and the sync status changed to PS");
    }
    validateModuleExport(currentAppSource);
  }

  /**
   * Handles the save event for CopilotAppSource entities. It sets the synchronization status
   * of the associated CopilotApp to 'Pending Synchronization'.
   *
   * @param event
   *     the entity save event to be observed
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppSource currentAppSource = (CopilotAppSource) event.getTargetInstance();
    changeAssistantStatus(currentAppSource);
    CopilotUtils.logIfDebug("The register was saved and the sync status changed to PS");
    validateModuleExport(currentAppSource);
  }

  /**
   * Handles the delete event for CopilotAppSource entities. It sets the synchronization status
   * of the associated CopilotApp to 'Pending Synchronization'.
   *
   * @param event
   *     the entity delete event to be observed
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppSource currentAppSource = (CopilotAppSource) event.getTargetInstance();
    changeAssistantStatus(currentAppSource);
    CopilotUtils.logIfDebug("The register was deleted and the sync status changed to PS");
  }

  /**
   * Changes the synchronization status of the associated CopilotApp to 'Pending Synchronization'.
   *
   * @param currentAppSource
   *     the CopilotAppSource entity whose associated CopilotApp's status needs to be updated
   */
  private static void changeAssistantStatus(CopilotAppSource currentAppSource) {
    CopilotApp currentAssistant = currentAppSource.getEtcopApp();
    CopilotAppInfoUtils.markAsPendingSynchronization(currentAssistant);
  }

  /**
   * Validates the module export for the given CopilotAppSource entity.
   * <p>
   * This method checks if the associated CopilotApp's module and the file's module are not null.
   * If the file's module is null, it throws an OBException indicating that the file does not belong to any module.
   *
   * @param currentAppSource
   *     the CopilotAppSource entity to validate
   * @throws OBException
   *     if the file's module is null
   */
  private void validateModuleExport(CopilotAppSource currentAppSource) {
    Module assistantModule = currentAppSource.getEtcopApp().getModule();
    if (assistantModule == null) {
      return;
    }
    Module fileModule = currentAppSource.getFile().getModule();
    if (fileModule == null) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_FileNotBelonging"), currentAppSource.getFile().getName()));
    }
  }
}
