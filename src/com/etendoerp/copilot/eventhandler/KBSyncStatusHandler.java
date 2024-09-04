package com.etendoerp.copilot.eventhandler;

import java.util.List;
import java.util.Objects;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Handles synchronization status updates for the Knowledge Base (KB) in response to entity events.
 */
public class KBSyncStatusHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotFile.class) };
  protected Logger logger = Logger.getLogger(KBSyncStatusHandler.class);

  /**
   * Returns the list of entities observed by this handler.
   *
   * @return An array of {@link Entity} objects that this handler observes.
   */
  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  /**
   * Handles the entity update event. Checks if any relevant properties of the {@link CopilotFile} entity
   * have changed, and if so, updates the synchronization status of the associated application.
   *
   * @param event The {@link EntityUpdateEvent} to be handled.
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotFile currentFile = (CopilotFile) event.getTargetInstance();

    if (checkFilePropertiesChanged(event, currentFile.getEntity())) {
      updateAppSyncStatus(currentFile);
    }

    CopilotUtils.logIfDebug("The KB was updated");
  }

  /**
   * Handles the entity save event.
   *
   * @param event The {@link EntityNewEvent} to be handled.
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  /**
   * Handles the entity delete event. Updates the synchronization status of the associated application
   * when a {@link CopilotFile} is deleted.
   *
   * @param event The {@link EntityDeleteEvent} to be handled.
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotFile currentFile = (CopilotFile) event.getTargetInstance();
    updateAppSyncStatus(currentFile);

    CopilotUtils.logIfDebug("The KB was deleted");
  }

  /**
   * Updates the synchronization status of the associated application to "Pending Synchronization"
   * when a {@link CopilotFile} is updated or deleted.
   *
   * @param currentFile The {@link CopilotFile} whose associated application's synchronization status
   *                    will be updated.
   */
  private static void updateAppSyncStatus(CopilotFile currentFile) {
    OBCriteria<CopilotAppSource> appSourceCrit = OBDal.getInstance().createCriteria(CopilotAppSource.class);
    appSourceCrit.add(Restrictions.eq(CopilotAppSource.PROPERTY_FILE, currentFile));
    List<CopilotAppSource> appSourceList = appSourceCrit.list();
    for (CopilotAppSource appSource : appSourceList) {
      appSource.getEtcopApp().setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
      OBDal.getInstance().save(appSource.getEtcopApp());
      CopilotUtils.logIfDebug("The sync status of " + appSource.getEtcopApp().getName() + " changed to PS");
    }
  }

  /**
   * Checks if any of the specified properties of the {@link CopilotFile} entity have changed
   * during an update event.
   *
   * @param event      The {@link EntityUpdateEvent} to be checked.
   * @param fileEntity The {@link Entity} representing the {@link CopilotFile}.
   * @return {@code true} if any of the specified properties have changed, {@code false} otherwise.
   */
  private static boolean checkFilePropertiesChanged(EntityUpdateEvent event, Entity fileEntity) {
    String[] properties = {
        CopilotFile.PROPERTY_ORGANIZATION,
        CopilotTool.PROPERTY_ACTIVE,
        CopilotFile.PROPERTY_NAME,
        CopilotFile.PROPERTY_DESCRIPTION,
        CopilotFile.PROPERTY_TYPE,
        CopilotFile.PROPERTY_FILENAME
    };

    for (String property : properties) {
      Object previousValue = event.getPreviousState(fileEntity.getProperty(property));
      Object currentValue = event.getCurrentState(fileEntity.getProperty(property));

      if (!Objects.equals(previousValue, currentValue)) {
        CopilotUtils.logIfDebug("The property: " + property + " was modified.");
        return true;
      }
    }
    return false;
  }
}
