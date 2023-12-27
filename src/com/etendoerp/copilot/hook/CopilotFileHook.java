package com.etendoerp.copilot.hook;

import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotFile;

public interface CopilotFileHook {

  void exec(CopilotFile hookObject) throws OBException;

  default int getPriority() {
    return 100;
  }
  boolean typeCheck(String type);
}

