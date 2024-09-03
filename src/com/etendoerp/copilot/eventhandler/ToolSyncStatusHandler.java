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


public class ToolSyncStatusHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotTool.class) };
  protected Logger logger = Logger.getLogger(ToolSyncStatusHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

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

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotTool currentTool = (CopilotTool) event.getTargetInstance();
    updateAppSyncStatus(currentTool);
    CopilotUtils.logIfDebug("The Tool was deleted");
  }

  private static void updateAppSyncStatus(CopilotTool currentTool) {
    OBCriteria<CopilotAppTool> appToolsCrit = OBDal.getInstance().createCriteria(CopilotAppTool.class);
    appToolsCrit.add(Restrictions.eq(CopilotAppTool.PROPERTY_COPILOTTOOL, currentTool));
    List<CopilotAppTool> appToolList = appToolsCrit.list();
    for (CopilotAppTool appTool : appToolList) {
      appTool.getCopilotApp().setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
      OBDal.getInstance().save(appTool.getCopilotApp());
      CopilotUtils.logIfDebug("The sync status of " + appTool.getCopilotApp().getName() + " changed to PS");
    }
  }


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
