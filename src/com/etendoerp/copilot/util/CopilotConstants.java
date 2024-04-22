package com.etendoerp.copilot.util;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import org.apache.commons.lang.StringUtils;

public class CopilotConstants {
  public static final String APP_TYPE_LANGCHAIN = "langchain";
  public static final String APP_TYPE_OPENAI = "openai-assistant";
  public static final String ERROR = "error";
  public static final String FILE_BEHAVIOUR_SYSTEM = "system";
  public static final String FILE_BEHAVIOUR_QUESTION = "question";
  public static final String FILE_BEHAVIOUR_ATTACH = "attach";
  public static final String FILE_BEHAVIOUR_KB = "kb";
  public static final String FILE_TYPE_RF = "RF";
  public static final String FILE_TYPE_F = "F";

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

  public static boolean isFileTypeFile(CopilotFile file) {
    return file.getType() != null && (StringUtils.equals(file.getType(),
        FILE_TYPE_RF) || StringUtils.equals(file.getType(), FILE_TYPE_F));
  }
}
