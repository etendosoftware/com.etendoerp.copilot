package com.etendoerp.copilot.hook;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotFile;

public class CopilotFileHookManager {

  @Inject
  @Any
  private Instance<CopilotFileHook> copFileHooks;

  private static final Logger log = LogManager.getLogger(CopilotFileHookManager.class);

  public void executeHooks(CopilotFile copilotFile) throws OBException {
    try {
      List<Object> hookList = sortHooksByPriority(copFileHooks);
      for (Object proc : hookList) {
        if (proc instanceof CopilotFileHook && ((CopilotFileHook) proc).typeCheck(copilotFile.getType())) {
          ((CopilotFileHook) proc).exec(copilotFile);
        }
      }
    } catch (Exception e) {
      log.error("Error executing hooks", e);
      throw new OBException(e);
    }
  }

  protected List<Object> sortHooksByPriority(Instance<? extends Object> hooks) {
    List<Object> hookList = new ArrayList<>();
    for (Object hookToAdd : hooks) {
      if (hookToAdd instanceof CopilotFileHook) {
        hookList.add(hookToAdd);
      }
    }

    hookList.sort((o1, o2) -> {
      int o1Priority = (o1 instanceof CopilotFileHook) ? ((CopilotFileHook) o1).getPriority() : 100;
      int o2Priority = (o2 instanceof CopilotFileHook) ? ((CopilotFileHook) o2).getPriority() : 100;
      int diff = o1Priority - o2Priority;
      return (int) Math.signum(diff);
    });
    return hookList;
  }
}
