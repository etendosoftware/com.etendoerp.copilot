package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotMCP;

/**
 * Handles JSON structure validation for MCP Server records when they are saved
 * to the etcop_mcp table. This event handler ensures that the JSON structure
 * field contains valid JSON before the record is persisted to the database.
 */
public class MCPValidationHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotMCP.class)
  };

  private static final Logger log = LogManager.getLogger(MCPValidationHandler.class);

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
   * Handles the save event for CopilotMCP entities. Validates that the JSON structure
   * field contains valid JSON before the record is saved to the database.
   *
   * @param event the entity save event to be observed
   * @throws OBException if the JSON structure is invalid
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    
    final CopilotMCP mcpRecord = (CopilotMCP) event.getTargetInstance();
    validateJsonStructure(mcpRecord);
    log.debug("MCP record validated successfully on save: {}", mcpRecord.getName());
  }

  /**
   * Handles the update event for CopilotMCP entities. Validates that the JSON structure
   * field contains valid JSON when the record is updated.
   *
   * @param event the entity update event to be observed
   * @throws OBException if the JSON structure is invalid
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    
    final CopilotMCP mcpRecord = (CopilotMCP) event.getTargetInstance();
    validateJsonStructure(mcpRecord);
    log.debug("MCP record validated successfully on update: {}", mcpRecord.getName());
  }

  /**
   * Validates the JSON structure field of a CopilotMCP record.
   * 
   * @param mcpRecord the CopilotMCP record to validate
   * @throws OBException if the JSON structure is invalid or empty when required
   */
  private void validateJsonStructure(CopilotMCP mcpRecord) {
    String jsonStructure = mcpRecord.getJsonStructure();
    
    // If the record is active and JSON structure is provided, validate it
    if (mcpRecord.isActive() && StringUtils.isNotEmpty(jsonStructure)) {
      try {
        // Attempt to parse the JSON to validate its structure
        new JSONObject(jsonStructure);
        log.debug("Valid JSON structure found for MCP record: {}", mcpRecord.getName());
      } catch (JSONException e) {
        String errorMessage = String.format(
            OBMessageUtils.messageBD("ETCOP_InvalidMCPJsonStructure"),
            e.getMessage()
        );
        throw new OBException(errorMessage);
      }
    } else if (mcpRecord.isActive() && StringUtils.isEmpty(jsonStructure)) {
      // Log warning if active MCP record has no JSON structure
      log.warn("Active MCP Server '{}' has no JSON structure defined", mcpRecord.getName());
    }
  }
}
