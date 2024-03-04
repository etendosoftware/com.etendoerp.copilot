package com.etendoerp.copilot.hook;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;

/**
 * This class manages the execution of hooks for OpenAIPromptHook.
 */
public class OpenAIPromptHookManager {

  // Injected instance of OpenAIPromptHook
  @Inject
  @Any
  private Instance<OpenAIPromptHook> promptHooks;

  // Logger for this class
  private static final Logger log = LogManager.getLogger(OpenAIPromptHookManager.class);

  /**
   * Executes hooks for a given CopilotApp and returns the result as a string.
   *
   * @param app
   *     The CopilotApp for which to execute hooks.
   * @return The result of the hook execution.
   * @throws OBException
   *     If there is an error executing the hooks.
   */
  public String executeHooks(CopilotApp app) throws OBException {
    try {
      StringBuilder sb = new StringBuilder();

      // Sort hooks by priority
      List<Object> hookList = sortHooksByPriority(promptHooks);
      if (hookList.isEmpty()) {
        logIfDebug("No hooks found for Hook OpenAIPromptHook");
        return sb.toString();
      }
      sb.append("Extra context information: \n");

      // Execute each hook
      for (Object proc : hookList) {
        if (proc instanceof OpenAIPromptHook && ((OpenAIPromptHook) proc).typeCheck(app)) {
          sb.append(((OpenAIPromptHook) proc).exec(app));
          sb.append("\n");
        }
      }
      return sb.toString();
    } catch (Exception e) {
      log.error("Error executing hooks", e);
      throw new OBException(e);
    }
  }

  /**
   * Sorts hooks by their priority.
   *
   * @param hooks
   *     The hooks to be sorted.
   * @return A list of hooks sorted by priority.
   */
  protected List<Object> sortHooksByPriority(Instance<? extends Object> hooks) {
    List<Object> hookList = new ArrayList<>();
    // Add all OpenAIPromptHook instances to the list
    for (Object hookToAdd : hooks) {
      if (hookToAdd instanceof OpenAIPromptHook) {
        hookList.add(hookToAdd);
      }
    }

    // Sort the list by hook priority
    hookList.sort((o1, o2) -> {
      int o1Priority = (o1 instanceof OpenAIPromptHook) ? ((OpenAIPromptHook) o1).getPriority() : 100;
      int o2Priority = (o2 instanceof OpenAIPromptHook) ? ((OpenAIPromptHook) o2).getPriority() : 100;
      int diff = o1Priority - o2Priority;
      return (int) Math.signum(diff);
    });
    return hookList;
  }
}