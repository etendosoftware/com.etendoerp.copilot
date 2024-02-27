package com.etendoerp.copilot.hook;

import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotFile;

public interface OpenAIPromptHook {

  String exec(CopilotApp app) throws OBException;

  default int getPriority() {
    return 100;
  }

  boolean typeCheck(CopilotApp app);
}

