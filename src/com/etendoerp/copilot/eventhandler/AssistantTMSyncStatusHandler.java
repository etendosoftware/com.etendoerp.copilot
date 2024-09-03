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

/**
 * This class is responsible for handling validations related to team members.
 * It extends the EntityPersistenceEventObserver class and observes updates and new events for the TeamMember entity.
 */
public class AssistantTMSyncStatusHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(TeamMember.class) };
  protected Logger logger = Logger.getLogger(AssistantTMSyncStatusHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final TeamMember currentTeamMember = (TeamMember) event.getTargetInstance();
    Object previousMember = event.getPreviousState(currentTeamMember.getEntity().getProperty(TeamMember.PROPERTY_MEMBER));
    Object previousApp = event.getPreviousState(currentTeamMember.getEntity().getProperty(TeamMember.PROPERTY_COPILOTAPP));
    Object currentMember = event.getCurrentState(currentTeamMember.getEntity().getProperty(TeamMember.PROPERTY_MEMBER));
    Object currentApp = event.getCurrentState(currentTeamMember.getEntity().getProperty(TeamMember.PROPERTY_COPILOTAPP));

    if (previousMember != currentMember || previousApp != currentApp) {
      CopilotApp currentAssistant = currentTeamMember.getCopilotApp();
      currentAssistant.setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
      OBDal.getInstance().save(currentAssistant);
      CopilotUtils.logIfDebug("The Member or App was updated and the sync status changed to PS");
    }
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final TeamMember currentTeamMember = (TeamMember) event.getTargetInstance();
    CopilotApp currentAssistant = currentTeamMember.getCopilotApp();
    currentAssistant.setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
    OBDal.getInstance().save(currentAssistant);
    CopilotUtils.logIfDebug("The Member was saved and the sync status changed to PS");

  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final TeamMember currentTeamMember = (TeamMember) event.getTargetInstance();
    CopilotApp currentAssistant = currentTeamMember.getCopilotApp();
    currentAssistant.setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
    OBDal.getInstance().save(currentAssistant);
    CopilotUtils.logIfDebug("The member was deleted and the sync status changed to PS");
  }
}