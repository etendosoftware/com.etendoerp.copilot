package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.dal.service.OBDal;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;

public class LineNoConversationHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = { ModelProvider.getInstance().getEntity(Message.class) };

  protected Logger logger = LogManager.getLogger(LineNoConversationHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    final Message currentMessage = (Message) event.getTargetInstance();

    Message maxLineNoMsg = (Message) OBDal.getInstance().createCriteria(Message.class)

        .add(Restrictions.eq(Message.PROPERTY_ETCOPCONVERSATION, currentMessage.getEtcopConversation()))
        .addOrderBy(Message.PROPERTY_LINENO, false)
        .setMaxResults(1)
        .uniqueResult();

    long newLineNo = (maxLineNoMsg == null || maxLineNoMsg.getLineno() == null) ? 10 : (maxLineNoMsg.getLineno() + 10);
    Property lineNo = currentMessage.getEntity().getProperty(Message.PROPERTY_LINENO);

    event.setCurrentState(lineNo, newLineNo);

  }
}