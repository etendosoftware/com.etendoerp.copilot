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
import com.etendoerp.copilot.data.CopilotFile;

public class OpenAIPromptHookManager {

  @Inject
  @Any
  private Instance<OpenAIPromptHook> promptHooks;

  private static final Logger log = LogManager.getLogger(OpenAIPromptHookManager.class);

  public String executeHooks(CopilotApp app) throws OBException {
    try {
      StringBuilder sb = new StringBuilder();

      List<Object> hookList = sortHooksByPriority(promptHooks);
      if (hookList.isEmpty()) {
        logIfDebug("No hooks found for Hook OpenAIPromptHook");
        return sb.toString();
      }
      sb.append("Extra context information: \n");

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

  protected List<Object> sortHooksByPriority(Instance<? extends Object> hooks) {
    List<Object> hookList = new ArrayList<>();
    for (Object hookToAdd : hooks) {
      if (hookToAdd instanceof OpenAIPromptHook) {
        hookList.add(hookToAdd);
      }
    }

    hookList.sort((o1, o2) -> {
      int o1Priority = (o1 instanceof OpenAIPromptHook) ? ((OpenAIPromptHook) o1).getPriority() : 100;
      int o2Priority = (o2 instanceof OpenAIPromptHook) ? ((OpenAIPromptHook) o2).getPriority() : 100;
      int diff = o1Priority - o2Priority;
      return (int) Math.signum(diff);
    });
    return hookList;
  }
}
