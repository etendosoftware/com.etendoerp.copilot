package com.etendoerp.copilot.hook;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;

/**
 * This interface defines the methods that a CopilotQuestionHook must implement.
 */
public interface CopilotQuestionHook {

  /**
   * Executes the hook for a given CopilotApp and JSON object.
   *
   * @param app
   *     The CopilotApp for which to execute the hook.
   * @param json
   *     The JSON object to be processed by the hook.
   * @throws OBException
   *     If there is an error executing the hook.
   */
  void exec(CopilotApp app, JSONObject json) throws OBException;

  /**
   * Returns the priority of the hook. Hooks with lower priority values are executed first.
   * By default, the priority is 100.
   *
   * @return The priority of the hook.
   */
  default int getPriority() {
    return 100;
  }

  /**
   * Checks if the hook is applicable for the given CopilotApp.
   *
   * @param app
   *     The CopilotApp to check.
   * @return true if the hook is applicable, false otherwise.
   */
  boolean typeCheck(CopilotApp app);
}