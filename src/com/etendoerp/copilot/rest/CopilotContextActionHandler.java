package com.etendoerp.copilot.rest;

import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.BaseActionHandler;

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
      JSONObject requestJson = new JSONObject(content);

      boolean isFormEditing = requestJson.optBoolean("isFormEditing", false);

      JSONObject activeWindow = requestJson.optJSONObject("activeWindow");
      JSONObject editedRecordContext = requestJson.optJSONObject("editedRecordContext");
      JSONArray selectedRecordsContext = requestJson.optJSONArray("selectedRecordsContext");

      String windowId = "";
      String tabId = "";
      String windowTitle = "";

      if (activeWindow != null) {
        windowId = activeWindow.optString("windowId", "");
        tabId = activeWindow.optString("tabId", "");
        windowTitle = activeWindow.optString("title", "");
      }

      StringBuilder recordsInfo = new StringBuilder();
      if (selectedRecordsContext != null) {
        for (int i = 0; i < selectedRecordsContext.length(); i++) {
          JSONObject row = selectedRecordsContext.optJSONObject(i);
          if (row != null) {
            String recordId = row.optString("id", "");
            recordsInfo.append("ID: ").append(recordId).append("\n");
          }
        }
      }

      // Create the response JSON
      JSONObject response = new JSONObject();
      response.put("activeTabId", tabId);
      response.put("activeWindowId", windowId);
      response.put("isFormEditing", isFormEditing);
      response.put("activeWindowTitle", windowTitle);
      response.put("selectedRecordsInfo", recordsInfo.toString());
      if (editedRecordContext != null) {
        response.put("editedRecordContext", editedRecordContext);
      }
      response.put("message", "Action executed successfully");

      return response;
    } catch (Exception error) {
      throw new OBException("Error executing action in CopilotContextActionHandler", error);
    }
  }
}
