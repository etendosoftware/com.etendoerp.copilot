package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.TeamMember;

/**
 * This class is responsible for handling validations related to team members.
 * It extends the EntityPersistenceEventObserver class and observes updates and new events for the TeamMember entity.
 */
/**
 * This class is responsible for handling validations related to team members.
 * It extends the EntityPersistenceEventObserver class and observes updates and new events for the TeamMember entity.
 */
public class TeamMemberValidations extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(TeamMember.class) };
  protected Logger logger = Logger.getLogger(TeamMemberValidations.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final TeamMember currentTeamMember = (TeamMember) event.getTargetInstance();
    doValidation(currentTeamMember);
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final TeamMember currentTeamMember = (TeamMember) event.getTargetInstance();
    doValidation(currentTeamMember);
  }

  private void doValidation(TeamMember currentTeamMember) {
    if (currentTeamMember.getMember() == null) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_TeamMemberNull"));
    }
    if (StringUtils.isEmpty(currentTeamMember.getMember().getDescription())) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_TeamMemberDesc"), currentTeamMember.getMember().getName()));
    }
  }
}