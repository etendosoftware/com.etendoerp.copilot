package com.etendoerp.copilot;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.utility.Attachment;

public class CopilotFileAttachmentEH  extends EntityPersistenceEventObserver {
  public static final String COPILOT_FILE_ID_TABLE = "6B246B1B3A6F4DE8AFC208E07DB29CE2";
  private static Entity[] entities = { ModelProvider.getInstance().getEntity(Attachment.ENTITY_NAME) };
  private static final Logger logger = LogManager.getLogger();

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Attachment targetInstance = (Attachment) event.getTargetInstance();
    String idTable = targetInstance.getTable().getId();
    if(!StringUtils.equals(idTable, COPILOT_FILE_ID_TABLE)){
      return;
    }
    if(getAttachment(targetInstance) != null){
      throw new OBException(OBMessageUtils.messageBD("ETCOP_UniqueAttachment"));
    }
  }

  public static Attachment getAttachment(Attachment targetInstance) {
    OBCriteria<Attachment> attchCriteria = OBDal.getInstance().createCriteria(
        Attachment.class);
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, targetInstance.getRecord()));
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE, targetInstance.getTable()));
    attchCriteria.add(Restrictions.ne(Attachment.PROPERTY_ID, targetInstance.getId()));
    return (Attachment) attchCriteria.setMaxResults(1).uniqueResult();
  }
}
