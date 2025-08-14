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

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;

/**
 * Handles synchronization status updates for the Copilot application when certain
 * events occur on CopilotTool entities, such as creation, update, or deletion.
 */
public class ToolSyncStatusHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotTool.class)
  };

  protected Logger logger = Logger.getLogger(ToolSyncStatusHandler.class);

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
   * Handles the update event for CopilotTool entities. If any relevant properties of
   * the CopilotTool entity have changed, it updates the synchronization status of the
   * associated CopilotApp to 'Pending Synchronization'.
   *
   * @param event the entity update event to be observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotTool currentTool = (CopilotTool) event.getTargetInstance();
    if (checkToolPropertiesChanged(event, currentTool.getEntity())) {
      updateAppSyncStatus(currentTool);
    }
    CopilotUtils.logIfDebug("The Tool was updated");
  }

  /**
   * Handles the save event for CopilotTool entities. Currently, this method does not
   * perform any specific actions but is implemented for future use.
   *
   * @param event the entity save event to be observed
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  /**
   * Handles the delete event for CopilotTool entities. When a CopilotTool entity is deleted,
   * this method updates the synchronization status of the associated CopilotApp to
   * 'Pending Synchronization'.
   *
   * @param event the entity delete event to be observed
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotTool currentTool = (CopilotTool) event.getTargetInstance();
    updateAppSyncStatus(currentTool);
    CopilotUtils.logIfDebug("The Tool was deleted");
  }

  /**
   * Updates the synchronization status of the CopilotApp entities associated with the
   * given CopilotTool to 'Pending Synchronization'.
   *
   * @param currentTool the CopilotTool entity for which to update the associated CopilotApp entities
   */
  private static void updateAppSyncStatus(CopilotTool currentTool) {
    OBCriteria<CopilotAppTool> appToolsCrit = OBDal.getInstance().createCriteria(CopilotAppTool.class);
    appToolsCrit.add(Restrictions.eq(CopilotAppTool.PROPERTY_COPILOTTOOL, currentTool));
    List<CopilotAppTool> appToolList = appToolsCrit.list();
    for (CopilotAppTool appTool : appToolList) {
      CopilotAppInfoUtils.markAsPendingSynchronization(appTool.getCopilotApp());
      CopilotUtils.logIfDebug("The sync status of " + appTool.getCopilotApp().getName() + " changed to PS");
    }
  }

  /**
   * Checks if any relevant properties of the CopilotTool entity have changed during an update event.
   * If any changes are detected, it returns true.
   *
   * @param event the entity update event
   * @param toolEntity the entity definition of CopilotTool
   * @return true if any relevant properties have changed, false otherwise
   */
  private static boolean checkToolPropertiesChanged(EntityUpdateEvent event, Entity toolEntity) {
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
      Object previousValue = event.getPreviousState(toolEntity.getProperty(property));
      Object currentValue = event.getCurrentState(toolEntity.getProperty(property));

      if (!Objects.equals(previousValue, currentValue)) {
        CopilotUtils.logIfDebug("The property: " + property + " was modified.");
        return true;
      }
    }

    return false;
  }
}
