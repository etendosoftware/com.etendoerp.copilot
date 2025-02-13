package com.etendoerp.copilot.eventhandler;

import java.util.Arrays;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotModel;

/**
 * Handles synchronization status updates for the Copilot application whenever
 * certain events occur on TeamMember entities, such as creation, update, or deletion.
 */
public class ModelHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = { ModelProvider.getInstance().getEntity(CopilotModel.class) };

  protected Logger logger = Logger.getLogger(ModelHandler.class);

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
   * @param event
   *     the entity update event to be observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    var e = Arrays.stream(entities).findFirst().get();
    var propDefault = e.getProperty(CopilotModel.PROPERTY_DEFAULT);
    var propDefaultOverride = e.getProperty(CopilotModel.PROPERTY_DEFAULTOVERRIDE);
    // is marking the model as default and already exists a default model
    if (((Boolean) event.getCurrentState(propDefault)) && !((Boolean) event.getPreviousState(propDefault))) {
      if (!StringUtils.equalsIgnoreCase(((CopilotModel) event.getTargetInstance()).getProvider(), "openai")) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_OpenAIModelsDefault"));
      }
      OBDal.getInstance().createCriteria(CopilotModel.class).add(
          Restrictions.eq(CopilotModel.PROPERTY_DEFAULT, true)).add(
          Restrictions.ne(CopilotModel.PROPERTY_ID, event.getTargetInstance().getId())).list().forEach(m -> {
        m.setDefault(false);
        OBDal.getInstance().save(m);
      });
    }
    // if the check "default override is be marked, validate that there is no other
    if (((Boolean) event.getCurrentState(propDefaultOverride)) && !((Boolean) event.getPreviousState(
        propDefaultOverride))) {

      var other = OBDal.getInstance().createCriteria(CopilotModel.class).add(
          Restrictions.eq(CopilotModel.PROPERTY_DEFAULTOVERRIDE, true)).add(
          Restrictions.ne(CopilotModel.PROPERTY_ID, event.getTargetInstance().getId())).setMaxResults(1).uniqueResult();
      if (other != null) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_OneModelDefault"));
      }
    }
  }
}
