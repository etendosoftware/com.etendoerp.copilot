/**
 * Handles events related to the {@link CopilotFile} entity, such as updates and saves.
 * Observes changes to the entity and validates its properties to ensure consistency.
 */
package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Handles events for the {@link CopilotFile} entity, ensuring data integrity and synchronization.
 */
public class KBFEventHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotFile.class) };

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
   * Handles the entity update event. Validates the {@link CopilotFile} entity and logs the update.
   *
   * @param event
   *     The {@link EntityUpdateEvent} to be handled.
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotFile currentFile = (CopilotFile) event.getTargetInstance();
    validateChunkSize(currentFile);

    CopilotUtils.logIfDebug("The KB was updated");
  }

  /**
   * Handles the entity save event. Validates the {@link CopilotFile} entity upon creation.
   *
   * @param event
   *     The {@link EntityNewEvent} to be handled.
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotFile currentFile = (CopilotFile) event.getTargetInstance();
    validateChunkSize(currentFile);
  }

  /**
   * Validates the chunk size of the given {@link CopilotFile} instance.
   * Ensures that the maximum chunk size is not smaller than the chunk overlap.
   * If the validation fails, an {@link OBException} is thrown with an appropriate error message.
   *
   * @param currentFile
   *     The {@link CopilotFile} instance whose chunk size is to be validated.
   * @throws OBException
   *     If the chunk overlap exceeds the maximum chunk size, indicating an invalid configuration.
   */
  private static void validateChunkSize(CopilotFile currentFile) {
    if (currentFile.getMaxChunkSize() == null) {
      return;
    }
    if (currentFile.getChunkOverlap() != null && currentFile.getMaxChunkSize() < currentFile.getChunkOverlap()) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_OverlapMaxSizeError"));
    }
  }
}
