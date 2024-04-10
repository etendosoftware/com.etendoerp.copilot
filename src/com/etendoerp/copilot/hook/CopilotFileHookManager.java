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

/**
 * This class manages the execution of hooks for CopilotFile.
 */
public class CopilotFileHookManager {

  // Injected instance of CopilotFileHook
  @Inject
  @Any
  private Instance<CopilotFileHook> copFileHooks;

  // Logger for this class
  private static final Logger log = LogManager.getLogger(CopilotFileHookManager.class);

  /**
   * Executes hooks for a given CopilotFile.
   *
   * @param copilotFile
   *     The CopilotFile for which to execute hooks.
   * @throws OBException
   *     If there is an error executing the hooks.
   */
  public void executeHooks(CopilotFile copilotFile) throws OBException {
    try {
      // Sort hooks by priority
      List<Object> hookList = sortHooksByPriority(copFileHooks);
      // Execute each hook
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

  /**
   * Sorts hooks by their priority.
   *
   * @param hooks
   *     The hooks to be sorted.
   * @return A list of hooks sorted by priority.
   */
  protected List<Object> sortHooksByPriority(Instance<? extends Object> hooks) {
    List<Object> hookList = new ArrayList<>();
    // Add all CopilotFileHook instances to the list
    for (Object hookToAdd : hooks) {
      if (hookToAdd instanceof CopilotFileHook) {
        hookList.add(hookToAdd);
      }
    }

    // Sort the list by hook priority
    hookList.sort((o1, o2) -> {
      int o1Priority = (o1 instanceof CopilotFileHook) ? ((CopilotFileHook) o1).getPriority() : 100;
      int o2Priority = (o2 instanceof CopilotFileHook) ? ((CopilotFileHook) o2).getPriority() : 100;
      int diff = o1Priority - o2Priority;
      return (int) Math.signum(diff);
    });
    return hookList;
  }
}