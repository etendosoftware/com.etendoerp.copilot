package com.etendoerp.copilot.eventhandler;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppMCP;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import org.apache.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;

import javax.enterprise.event.Observes;
import java.util.Objects;

/**
 * Handles synchronization status updates for the Copilot agents whenever
 * certain events occur on CopilotAppMCP entity, such as creation, update, or deletion.
 */
public class AgentMCPSyncStatusHandler extends EntityPersistenceEventObserver {

    private static Entity[] entities = {
            ModelProvider.getInstance().getEntity(CopilotAppMCP.class)
    };

    protected Logger logger = Logger.getLogger(AgentMCPSyncStatusHandler.class);

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
     * Handles the update event for CopilotAppMCP entity. If the CopilotMCP property
     * of the CopilotAppMCP entity has changed, it updates the synchronization status
     * of the associated Agent to 'Pending Synchronization'.
     *
     * @param event the entity update event to be observed
     */
    public void onUpdate(@Observes EntityUpdateEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        final CopilotAppMCP currentAppMCP = (CopilotAppMCP) event.getTargetInstance();
        Object previousValue = event.getPreviousState(currentAppMCP.getEntity().getProperty(CopilotAppMCP.PROPERTY_COPILOTMCP));
        Object currentValue = event.getCurrentState(currentAppMCP.getEntity().getProperty(CopilotAppMCP.PROPERTY_COPILOTMCP));
        if (!Objects.equals(previousValue, currentValue)) {
            changeAssistantStatus(currentAppMCP);
            CopilotUtils.logIfDebug("The AppMCP was updated and the sync status of " + currentAppMCP.getAssistant() + " changed to PS");
        }
    }

    /**
     * Handles the save event for CopilotAppTool entities. When a new CopilotAppTool entity
     * is saved, it updates the synchronization status of the associated CopilotApp to
     * 'Pending Synchronization'.
     *
     * @param event the entity save event to be observed
     */
    public void onSave(@Observes EntityNewEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        final CopilotAppMCP currentAppMCP = (CopilotAppMCP) event.getTargetInstance();
        changeAssistantStatus(currentAppMCP);
        CopilotUtils.logIfDebug("The AppTool was saved and the sync status of " + currentAppMCP.getAssistant() + " changed to PS");
    }

    /**
     * Handles the delete event for CopilotAppTool entities. When a CopilotAppTool entity
     * is deleted, it updates the synchronization status of the associated CopilotApp to
     * 'Pending Synchronization'.
     *
     * @param event the entity delete event to be observed
     */
    public void onDelete(@Observes EntityDeleteEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        final CopilotAppMCP currentAppMCP = (CopilotAppMCP) event.getTargetInstance();
        changeAssistantStatus(currentAppMCP);
        CopilotUtils.logIfDebug("The AppTool was deleted and the sync status of " + currentAppMCP.getAssistant() + " changed to PS");
    }

    /**
     * Updates the synchronization status of the CopilotApp entity associated with the
     * given CopilotAppTool to 'Pending Synchronization'.
     *
     * @param currentAppTool the CopilotAppTool entity for which to update the associated CopilotApp
     */
    private static void changeAssistantStatus(CopilotAppMCP currentAppTool) {
        CopilotApp currentAssistant = currentAppTool.getAssistant();
        currentAssistant.setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        OBDal.getInstance().save(currentAssistant);
    }
}