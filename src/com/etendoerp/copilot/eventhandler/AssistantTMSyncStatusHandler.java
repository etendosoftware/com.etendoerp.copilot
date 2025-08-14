package com.etendoerp.copilot.eventhandler;

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
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;

/**
 * Handles synchronization status updates for the Copilot application whenever
 * certain events occur on TeamMember entities, such as creation, update, or deletion.
 */
public class AssistantTMSyncStatusHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(TeamMember.class)
  };

  protected Logger logger = Logger.getLogger(AssistantTMSyncStatusHandler.class);

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
   * Handles the update event for TeamMember entities. If the TeamMember's associated
   * member or CopilotApp has changed, it updates the synchronization status of the
   * associated CopilotApp to 'Pending Synchronization'.
   *
   * @param event the entity update event to be observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final TeamMember currentTeamMember = (TeamMember) event.getTargetInstance();
    Object previousMember = event.getPreviousState(currentTeamMember.getEntity().getProperty(TeamMember.PROPERTY_MEMBER));
    Object previousApp = event.getPreviousState(currentTeamMember.getEntity().getProperty(TeamMember.PROPERTY_COPILOTAPP));
    Object currentMember = event.getCurrentState(currentTeamMember.getEntity().getProperty(TeamMember.PROPERTY_MEMBER));
    Object currentApp = event.getCurrentState(currentTeamMember.getEntity().getProperty(TeamMember.PROPERTY_COPILOTAPP));

    if (!previousMember.equals(currentMember) || !previousApp.equals(currentApp)) {
      changeAssistantStatus(currentTeamMember);
      CopilotUtils.logIfDebug("The Member or App was updated and the sync status changed to PS");
    }
  }

  /**
   * Handles the save event for TeamMember entities. When a new TeamMember entity
   * is saved, it updates the synchronization status of the associated CopilotApp
   * to 'Pending Synchronization'.
   *
   * @param event the entity save event to be observed
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final TeamMember currentTeamMember = (TeamMember) event.getTargetInstance();
    changeAssistantStatus(currentTeamMember);
    CopilotUtils.logIfDebug("The Member was saved and the sync status changed to PS");
  }

  /**
   * Handles the delete event for TeamMember entities. When a TeamMember entity
   * is deleted, it updates the synchronization status of the associated CopilotApp
   * to 'Pending Synchronization'.
   *
   * @param event the entity delete event to be observed
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final TeamMember currentTeamMember = (TeamMember) event.getTargetInstance();
    changeAssistantStatus(currentTeamMember);
    CopilotUtils.logIfDebug("The member was deleted and the sync status changed to PS");
  }

  /**
   * Updates the synchronization status of the CopilotApp entity associated with the
   * given TeamMember to 'Pending Synchronization'.
   *
   * @param currentTeamMember the TeamMember entity for which to update the associated CopilotApp
   */
  private static void changeAssistantStatus(TeamMember currentTeamMember) {
    CopilotApp currentAssistant = currentTeamMember.getCopilotApp();
    CopilotAppInfoUtils.markAsPendingSynchronization(currentAssistant);
  }
}
