package com.etendoerp.copilot.util;

import org.apache.commons.lang.StringUtils;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

public class CopilotConstants {
  public static final String APP_TYPE_LANGCHAIN = "langchain";
  public static final String APP_TYPE_LANGGRAPH = "langgraph";
  public static final String APP_TYPE_MULTIMODEL = "multimodel";
  public static final String APP_TYPE_OPENAI = "openai-assistant";
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
  public static final String KBF_TYPE_ATTACHED = "F";
  public static final String PROP_QUESTION = "question";
  public static final String PROP_ERROR = "error";
  // Tab ID for CopilotFile
  public static final String COPILOT_FILE_TAB_ID = "09F802E423924081BC2947A64DDB5AF5";
  public static final String COPILOT_FILE_AD_TABLE_ID = "6B246B1B3A6F4DE8AFC208E07DB29CE2";
  private static final String FILE_TYPE_HQL = "HQL";
  public static final String PROVIDER_OPENAI = "openai";
  public static final String PROVIDER_GEMINI = "gemini";
  public static final String PENDING_SYNCHRONIZATION_STATE = "PS";
  public static final String SYNCHRONIZED_STATE = "S";
  public static final String PROP_APP_ID = "app_id";
  public static final String PROP_CONVERSATION_ID = "conversation_id";
  public static final String PROP_FILE = "file";
  public static final String OPENAI_MODELS = "https://api.openai.com/v1/models";

  protected static final String[] KB_FILE_VALID_EXTENSIONS = {
      "pdf", "md", "markdown", "txt", "zip", "java", "py", "js", "xml", "json"
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
