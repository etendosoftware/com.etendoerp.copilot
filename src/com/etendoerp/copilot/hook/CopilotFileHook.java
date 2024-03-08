package com.etendoerp.copilot.hook;

import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotFile;

/**
 * This interface defines the methods that a CopilotFileHook must implement.
 */
public interface CopilotFileHook {

  /**
   * Executes the hook for a given CopilotFile.
   *
   * @param hookObject
   *     The CopilotFile for which to execute the hook.
   * @throws OBException
   *     If there is an error executing the hook.
   */
  void exec(CopilotFile hookObject) throws OBException;

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
   * Checks if the hook is applicable for the given type.
   *
   * @param type
   *     The type to check.
   * @return true if the hook is applicable, false otherwise.
   */
  boolean typeCheck(String type);
}