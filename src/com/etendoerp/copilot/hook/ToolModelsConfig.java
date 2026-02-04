package com.etendoerp.copilot.hook;

import java.util.HashSet;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.TeamMember;

/**
 * Hook that adds tool model configurations to the JSON response for copilot apps.
 *
 * <p>This hook processes the copilot app and its team members to extract tool model
 * configurations and add them to the response JSON under the "tool_config" key in extra_info.
 * It supports parsing model strings in the format "provider/modelName" and defaults to
 * "openai" provider when no provider is specified.</p>
 *
 * <p>Example output structure:
 * <pre>
 * {
 *   "tool_config": {
 *     "ID_copilot_app_1": {
 *       "ID_TOOL": {
 *         "model": "gpt-4-turbo",
 *         "provider": "openai"
 *       }
 *     },
 *     "ID_copilot_app_2": {
 *       "ID_TOOL": {
 *         "model": "gemini-2.5-pro",
 *         "provider": "gemini"
 *       }
 *     }
 *   }
 * }
 * </pre>
 * </p>
 */
public class ToolModelsConfig implements CopilotQuestionHook {

  /**
   * Logger for this class.
   */
  private static final Logger log = LogManager.getLogger(ToolModelsConfig.class);

  /**
   * Executes the hook to add tool model configurations to the JSON response.
   *
   * <p>This method processes the provided copilot app and all its team member apps,
   * extracting tool model configurations for each app. For each tool with a configured
   * model, it parses the model string (expected format: "provider/modelName") and
   * creates a JSON object with the provider and model information.</p>
   *
   * <p>The method adds the configurations to the JSON response under
   * {@code extra_info.tool_config}, organized by app ID and tool ID.</p>
   *
   * <p>Model string parsing rules:
   * <ul>
   *   <li>Format "provider/modelName" splits into provider and model</li>
   *   <li>Format "modelName" defaults to provider "openai"</li>
   *   <li>Multiple slashes: only the first slash is used as separator (e.g., "openai/gpt-4/turbo" becomes provider="openai", model="gpt-4/turbo")</li>
   * </ul>
   * </p>
   *
   * <p>If any exception occurs during processing, it is caught and logged, but does not
   * interrupt the execution flow.</p>
   *
   * @param app
   *     The copilot app to process, including its tools and team members
   * @param json
   *     The JSON object to which the tool configurations will be added.
   *     Must contain an "extra_info" object.
   * @throws OBException
   *     if there's a critical error processing the hook
   */
  @Override
  public void exec(CopilotApp app, JSONObject json) throws OBException {
    var toolModelsConfig = new JSONObject();
    try {
      var set = new HashSet<CopilotApp>();
      set.add(app);
      set.addAll(app.getETCOPTeamMemberList().stream()
          .map(TeamMember::getCopilotApp
          ).collect(Collectors.toList()));

      for (CopilotApp copilotApp : set) {
        logIfDebug(String.format("Adding tool models config to app: %s", copilotApp.getName()));
        var appToolList = copilotApp.getETCOPAppToolList();
        var appToolModelsConfigJson = new JSONObject();
        for (var appTool : appToolList) {
          addToolConfig(appTool, appToolModelsConfigJson);
        }
        toolModelsConfig.put(copilotApp.getId(), appToolModelsConfigJson);
      }
      json.getJSONObject("extra_info").put("tool_config", toolModelsConfig);
    } catch (Exception e) {
      log.error("Error adding tool models config", e);
      throw new OBException(e);
    }
  }

  /**
   * Adds a tool configuration to the JSON object if the tool has a model configured.
   *
   * <p>This method extracts the model information from the provided app tool and
   * adds it to the JSON object in the format expected by the copilot system.
   * The model string is parsed to separate the provider from the model name.</p>
   *
   * <p>Model parsing behavior:
   * <ul>
   *   <li>If model contains a slash (/), splits on first occurrence:
   *       provider is the first part, model is the remaining part</li>
   *   <li>If no slash present, uses entire string as model name and
   *       defaults provider to "openai"</li>
   *   <li>If model is null or blank, no configuration is added</li>
   * </ul>
   * </p>
   *
   * @param appTool
   *     The copilot app tool containing the model configuration to process
   * @param appToolModelsConfigJson
   *     The JSON object where the tool configuration will be added,
   *     keyed by the tool ID
   * @throws JSONException
   *     if there's an error creating or adding to the JSON object
   */
  private static void addToolConfig(CopilotAppTool appTool, JSONObject appToolModelsConfigJson) throws JSONException {
    if (appTool.getModel() == null) {
      return;
    }
    var toolConfigJson = new JSONObject();
    var tool = appTool.getCopilotTool();
    var modelStr = appTool.getModel();
    if (modelStr == null) {
      return;
    }
    toolConfigJson.put("model", modelStr.getName());
    toolConfigJson.put("provider", modelStr.getProvider() != null ? modelStr.getProvider() : "openai");
    appToolModelsConfigJson.put(tool.getId(), toolConfigJson);
  }

  /**
   * Logs a debug message if debug logging is enabled.
   *
   * @param string
   *     The message to log.
   */
  private static void logIfDebug(String string) {
    if (log.isDebugEnabled()) {
      log.debug(string);
    }
  }

  /**
   * Type check for the hook - always returns true.
   *
   * <p>This hook is always applicable to all copilot apps, so it always returns true
   * regardless of the app type or configuration.</p>
   *
   * @param app
   *     The copilot app to check (not used in this implementation)
   * @return Always returns {@code true}
   */
  @Override
  public boolean typeCheck(CopilotApp app) {
    return true;
  }

}
