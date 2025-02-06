package com.etendoerp.copilot.rest;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;

import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Handles actions for the Copilot context.
 */
public class CopilotContextActionHandler extends BaseActionHandler {

  private static final String DEFAULT_PROMPT_PREFERENCE_KEY = "ETCOP_DefaultContextPrompt";
  private static final String JSON_IS_FORM_EDITING = "isFormEditing";
  private static final String JSON_ACTIVE_WINDOW = "activeWindow";
  private static final String JSON_SELECTED_RECORDS_CONTEXT = "selectedRecordsContext";
  private static final String JSON_WINDOW_ID = "windowId";
  private static final String JSON_TAB_ID = "tabId";
  private static final String JSON_TITLE = "title";
  private static final String JSON_ID = "id";
  private static final String RECORD_ID_LABEL = "ID: ";
  private static final String RESPONSE_ACTIVE_WINDOW_ID = "@ACTIVE_WINDOW_ID@";
  private static final String RESPONSE_ACTIVE_TAB_ID = "@ACTIVE_TAB_ID@";
  private static final String RESPONSE_WINDOW_TITLE = "@WINDOW_TITLE@";
  private static final String RESPONSE_IS_FORM_EDITING = "@IS_FORM_EDITING@";
  private static final String RESPONSE_SELECTED_RECORDS = "@SELECTED_RECORDS@";
  private static final String RESPONSE_MESSAGE = "message";

  /**
   * Executes an action in the Copilot context.
   *
   * @param parameters the parameters passed to the action
   * @param content    the JSON content passed as a string
   * @return a JSONObject with the response
   */
  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    try {
      // Parse the incoming JSON content
      JSONObject requestJson = new JSONObject(content);

      // Extract relevant data from the request
      boolean isFormEditing = requestJson.optBoolean(JSON_IS_FORM_EDITING, false);
      JSONObject activeWindow = requestJson.optJSONObject(JSON_ACTIVE_WINDOW);
      JSONArray selectedRecordsContext = requestJson.optJSONArray(JSON_SELECTED_RECORDS_CONTEXT);

      // Extract window and tab information
      String windowId = activeWindow != null ? activeWindow.optString(JSON_WINDOW_ID, StringUtils.EMPTY)
          : StringUtils.EMPTY;
      String tabId = activeWindow != null ? activeWindow.optString(JSON_TAB_ID, StringUtils.EMPTY) : StringUtils.EMPTY;
      String windowTitle = activeWindow != null ? activeWindow.optString(JSON_TITLE, StringUtils.EMPTY)
          : StringUtils.EMPTY;

      // Build selected records information
      StringBuilder recordsInfo = new StringBuilder();
      if (selectedRecordsContext != null) {
        for (int i = 0; i < selectedRecordsContext.length(); i++) {
          JSONObject row = selectedRecordsContext.optJSONObject(i);
          if (row != null) {
            recordsInfo.append(RECORD_ID_LABEL).append(row.optString(JSON_ID, StringUtils.EMPTY)).append(" ");
          }
        }
      }

      // Create the response object
      JSONObject response = new JSONObject();
      response.put(RESPONSE_ACTIVE_WINDOW_ID, windowId);
      response.put(RESPONSE_ACTIVE_TAB_ID, tabId);
      response.put(RESPONSE_WINDOW_TITLE, windowTitle);
      response.put(RESPONSE_IS_FORM_EDITING, isFormEditing);
      response.put(RESPONSE_SELECTED_RECORDS, recordsInfo.toString());

      // Get the current context
      OBContext context = OBContext.getOBContext();

      // Retrieve the default prompt preference
      String defaultPrompt = Preferences.getPreferenceValue(
          DEFAULT_PROMPT_PREFERENCE_KEY,
          true,
          context.getCurrentClient().getId(),
          context.getCurrentOrganization().getId(),
          context.getUser().getId(),
          context.getRole().getId(),
          windowId);

      // Replace placeholders in the default prompt
      if (defaultPrompt != null) {
        defaultPrompt = CopilotUtils.replaceCopilotPromptVariables(defaultPrompt, response);
      }

      // Add the processed prompt to the response
      response.put(RESPONSE_MESSAGE, defaultPrompt);

      return response;
    } catch (Exception error) {
      throw new OBException(error);
    }
  }
}
