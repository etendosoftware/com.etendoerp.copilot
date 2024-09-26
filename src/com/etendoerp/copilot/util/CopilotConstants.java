package com.etendoerp.copilot.util;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

import org.apache.commons.lang.StringUtils;

public class CopilotConstants {
  public static final String APP_TYPE_LANGCHAIN = "langchain";
  public static final String APP_TYPE_OPENAI = "openai-assistant";
  public static final String APP_TYPE_LANGGRAPH = "langgraph";
  public static final String ERROR = "error";
  public static final String FILE_BEHAVIOUR_SYSTEM = "system";
  public static final String FILE_BEHAVIOUR_QUESTION = "question";
  public static final String FILE_BEHAVIOUR_ATTACH = "attach";
  public static final String FILE_BEHAVIOUR_KB = "kb";
  public static final String FILE_TYPE_RF = "RF";
  public static final String FILE_TYPE_F = "F";
  public static final String MESSAGE_ASSISTANT = "ASSISTANT";
  public static final String MESSAGE_USER = "USER";
  public static final String PROVIDER_OPENAI_VALUE = "O";
  public static final String PROVIDER_GEMINI_VALUE = "G";
  public static final String MESSAGE_ERROR = "ERROR";
  public static final int LANGCHAIN_MAX_LENGTH_PROMPT = 256000;
  public static final int LANGCHAIN_MAX_LENGTH_QUESTION = 1000000;
  private static final String FILE_TYPE_HQL = "HQL";
  public static final String PROVIDER_OPENAI = "openai";
  public static final String PROVIDER_GEMINI = "gemini";
  public static final String PENDING_SYNCHRONIZATION_STATE = "PS";
  public static final String SYNCHRONIZED_STATE = "S";
  public static final String[] KB_FILE_VALID_EXTENSIONS = {
      "pdf", "md", "markdown", "txt", "zip", "java", "py", "js", "xml"
  };

  public static boolean isSystemPromptBehaviour(CopilotAppSource source) {
    return StringUtils.equals(source.getBehaviour(), FILE_BEHAVIOUR_SYSTEM);
  }

  public static boolean isQuestionBehaviour(CopilotAppSource source) {
    return StringUtils.equals(source.getBehaviour(), FILE_BEHAVIOUR_QUESTION);
  }

  public static boolean isAttachBehaviour(CopilotAppSource source) {
    return StringUtils.equals(source.getBehaviour(), FILE_BEHAVIOUR_ATTACH);
  }

  public static boolean isKbBehaviour(CopilotAppSource source) {
    return StringUtils.isEmpty(source.getBehaviour()) || StringUtils.equals(source.getBehaviour(),
        FILE_BEHAVIOUR_KB);
  }

  public static boolean isFileTypeLocalOrRemoteFile(CopilotFile file) {
    return file.getType() != null && (StringUtils.equals(file.getType(),
        FILE_TYPE_RF) || StringUtils.equals(file.getType(), FILE_TYPE_F));
  }

  public static boolean isFileTypeRemoteFile(CopilotFile file) {
    return file.getType() != null && (StringUtils.equals(file.getType(),
        FILE_TYPE_RF));
  }

  public static boolean isHQLQueryFile(CopilotFile file) {
    return file.getType() != null && (StringUtils.equals(file.getType(),
        FILE_TYPE_HQL));
  }
}
