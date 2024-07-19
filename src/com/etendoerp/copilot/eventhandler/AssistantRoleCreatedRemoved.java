package com.etendoerp.copilot.eventhandler;

import java.util.List;

import javax.enterprise.event.Observes;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;


public class AssistantRoleCreatedRemoved extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotApp.ENTITY_NAME) };
  protected Logger logger = Logger.getLogger(AssistantRoleCreatedRemoved.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotApp currentAssistant = (CopilotApp) event.getTargetInstance();
    OBCriteria<CopilotRoleApp> crit = OBDal.getInstance().createCriteria(CopilotRoleApp.class);
    crit.add(Restrictions.eq(CopilotRoleApp.PROPERTY_COPILOTAPP, currentAssistant));
    List<CopilotRoleApp> roleAppList = crit.list();
    for (CopilotRoleApp roleApp : roleAppList) {
      OBDal.getInstance().remove(roleApp);
    }
  }
}
