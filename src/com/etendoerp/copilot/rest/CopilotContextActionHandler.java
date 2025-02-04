package com.etendoerp.copilot.rest;

import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.BaseActionHandler;

import kong.unirest.json.JSONArray;

/**
 * Handles actions for the Copilot context.
 */
public class CopilotContextActionHandler extends BaseActionHandler {

  /**
   * Executes an action in the Copilot context.
   *
   * @param parameters the parameters passed to the action
   * @param content the JSON content passed as a string
   * @return a JSONObject with the response
   */
  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    try {
      JSONObject request = new JSONObject(content);
      JSONObject activeWindow = request.optJSONObject("activeWindow");

      String windowId = "";
      String tabId = "";
      String windowTitle = "";

      if (activeWindow != null) {
        windowId = activeWindow.optString("windowId", "");
        tabId = activeWindow.optString("tabId", "");
        windowTitle = activeWindow.optString("title", "");
      }

      org.codehaus.jettison.json.JSONArray selectedRecordsContext =
          request.optJSONArray("selectedRecordsContext");

      StringBuilder recordsInfo = new StringBuilder();
      if (selectedRecordsContext != null) {
        for (int i = 0; i < selectedRecordsContext.length(); i++) {
          JSONObject row = selectedRecordsContext.optJSONObject(i);
          if (row != null) {
            String recordId = row.optString("id", "");
            recordsInfo.append("ID: " + recordId + "\n");
          }
        }
      }

      JSONObject response = new JSONObject();
      response.put("activeWindowId", windowId);
      response.put("activeTabId", tabId);
      response.put("activeWindowTitle", windowTitle);
      response.put("selectedRecordsInfo", recordsInfo.toString());
      response.put("message", "Action executed successfully");

      return response;
    } catch (Exception e) {
      throw new OBException("Error executing action in CopilotContextActionHandler", e);
    }
  }
}
