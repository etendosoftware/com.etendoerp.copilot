package com.etendoerp.copilot.eventhandler;

import java.math.BigDecimal;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;


public class AssistantValidations extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotApp.class) };
  protected Logger logger = Logger.getLogger(AssistantValidations.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotApp curr = (CopilotApp) event.getTargetInstance();
    validateTemperature(curr);
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotApp curr = (CopilotApp) event.getTargetInstance();
    validateTemperature(curr);
  }

  private void validateTemperature(CopilotApp app) {
    BigDecimal temp = app.getTemperature();
    if (temp != null && (temp.compareTo(BigDecimal.ZERO) < 0 || temp.compareTo(new BigDecimal(2)) > 0)) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_TemperatureRange"), app.getName()));
    }

  }
}