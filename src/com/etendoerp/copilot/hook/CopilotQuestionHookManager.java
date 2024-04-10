package com.etendoerp.copilot.hook;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;

/**
 * This class manages the execution of hooks for CopilotApp.
 */
public class CopilotQuestionHookManager {

  // Injected instance of CopilotQuestionHook
  @Inject
  @Any
  private Instance<CopilotQuestionHook> questionHooks;

  // Logger for this class
  private static final Logger log = LogManager.getLogger(CopilotQuestionHookManager.class);

  /**
   * Executes hooks for a given CopilotApp and JSON request.
   *
   * @param app         The CopilotApp for which to execute hooks.
   * @param jsonRequest The JSON request to be processed by the hooks.
   * @throws OBException If there is an error executing the hooks.
   */
  public void executeHooks(CopilotApp app, JSONObject jsonRequest) throws OBException {
    try {
      // Sort hooks by priority
      List<Object> hookList = sortHooksByPriority(questionHooks);
      if (hookList.isEmpty()) {
        logIfDebug(String.format("No hooks found for app: %s", app));
      }
      logIfDebug("Extra information for question:");

      // Execute each hook
      for (Object hookImpl : hookList) {
        if (hookImpl instanceof CopilotQuestionHook && ((CopilotQuestionHook) hookImpl).typeCheck(app)) {
          logIfDebug(String.format("Executing hook: %s", hookImpl.getClass().getSimpleName()));
          ((CopilotQuestionHook) hookImpl).exec(app, jsonRequest);
        }
      }
    } catch (Exception e) {
      log.error("Error executing hooks", e);
      throw new OBException(e);
    }
  }

  /**
   * Sorts hooks by their priority.
   *
   * @param hooks The hooks to be sorted.
   * @return A list of hooks sorted by priority.
   */
  protected List<Object> sortHooksByPriority(Instance<? extends Object> hooks) {
    List<Object> hookList = new ArrayList<>();
    for (Object hookToAdd : hooks) {
      if (hookToAdd instanceof CopilotQuestionHook) {
        hookList.add(hookToAdd);
      }
    }

    // Sort the list by hook priority
    hookList.sort((o1, o2) -> {
      int o1Priority = (o1 instanceof CopilotQuestionHook) ? ((CopilotQuestionHook) o1).getPriority() : 100;
      int o2Priority = (o2 instanceof CopilotQuestionHook) ? ((CopilotQuestionHook) o2).getPriority() : 100;
      int diff = o1Priority - o2Priority;
      return (int) Math.signum(diff);
    });
    return hookList;
  }
}
