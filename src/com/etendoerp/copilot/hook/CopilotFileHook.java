package com.etendoerp.copilot.hook;

import com.etendoerp.copilot.data.CopilotFile;

public interface CopilotFileHook {

  void exec(CopilotFile hookObject) throws Exception;

  default int getPriority() {
    return 100;
  }
  boolean typeCheck(String type);
}

