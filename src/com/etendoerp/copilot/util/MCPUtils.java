package com.etendoerp.copilot.util;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppMCP;
import com.etendoerp.copilot.data.CopilotMCP;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import java.util.List;
import java.util.Properties;

/**
 * Utility class for handling Model Context Protocol (MCP) server configurations.
 * Provides methods to retrieve and process MCP configurations associated with Copilot applications.
 */
public class MCPUtils {

    private MCPUtils() {
        // Private constructor to prevent instantiation
    }

    private static final Logger log = LogManager.getLogger(MCPUtils.class);

    /**
     * This method retrieves all MCP configurations associated with a given CopilotApp instance.
     * It creates a JSONArray containing the MCP server configurations that the agent will connect to.
     *
     * @param copilotApp
     *     The CopilotApp instance for which the MCP configurations are to be retrieved.
     * @return A JSONArray containing the MCP server configurations.
     * @throws JSONException
     *     If an error occurs while creating the JSON object.
     */
    public static JSONArray getMCPConfigurations(CopilotApp copilotApp) throws JSONException {
        JSONArray mcpConfigurations = new JSONArray();

        // Get all CopilotAppMCP relationships for this assistant
        OBCriteria<CopilotAppMCP> appMcpCriteria = OBDal.getInstance().createCriteria(CopilotAppMCP.class);
        appMcpCriteria.add(Restrictions.eq(CopilotAppMCP.PROPERTY_ASSISTANT, copilotApp));
        appMcpCriteria.add(Restrictions.eq(CopilotAppMCP.PROPERTY_ACTIVE, true));

        List<CopilotAppMCP> appMcpList = appMcpCriteria.list();

        for (CopilotAppMCP appMcp : appMcpList) {
            CopilotMCP mcpConfig = appMcp.getMCPServer();
            if (mcpConfig != null && mcpConfig.isActive() && StringUtils.isNotEmpty(mcpConfig.getJsonStructure())) {
                try {
                    String mcpJson = replaceVariables(mcpConfig);
                    JSONObject raw = new JSONObject(mcpJson);
                    JSONArray normalized = MCPConfigNormalizer.normalizeToArray(raw, mcpConfig.getName());
                    for (int i = 0; i < normalized.length(); i++) {
                        JSONObject item = normalized.getJSONObject(i);
                        if (!item.has("name") || StringUtils.isBlank(item.optString("name"))) {
                            item.put("name", mcpConfig.getName());
                        }
                        mcpConfigurations.put(item);
                    }
                } catch (JSONException e) {
                    String errorMsg = "Invalid JSON structure in MCP configuration: " + mcpConfig.getName();
                    log.error(errorMsg, e);
                }
            }
        }
        return mcpConfigurations;
    }

    private static String replaceVariables(CopilotMCP mcpConfig) {
        try {
            return CopilotUtils.replaceCopilotPromptVariables(mcpConfig.getJsonStructure(), null, false);
        } catch (Exception ex) {
            String errorMsg = "Failed to replace variables in MCP: " + mcpConfig.getName();
            log.error(errorMsg);
            log.error(ex.getMessage());
            return mcpConfig.getJsonStructure();
        }
    }
}
