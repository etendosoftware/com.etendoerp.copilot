package com.etendoerp.copilot.util;

import com.etendoerp.copilot.data.CopilotAppSource;
import org.apache.commons.lang.StringUtils;

public class CopilotConstants {
  public static final String APP_TYPE_LANGCHAIN = "langchain";
  public static final String APP_TYPE_OPENAI = "openai-assistant";
  public static final String ERROR = "error";
  public static final String FILE_BEHAVIOUR_SYSTEM = "system";
  public static final String FILE_BEHAVIOUR_QUESTION = "question";
  public static final String FILE_BEHAVIOUR_ATTACH = "attach";
  public static final String FILE_BEHAVIOUR_KB = "kb";

  public static boolean isQuestionBehaviour(CopilotAppSource source) {
    return StringUtils.equals(source.getBehaviour(), FILE_BEHAVIOUR_QUESTION);
  }

  public static boolean isAttachBehaviour(CopilotAppSource source) {
    return StringUtils.equals(source.getBehaviour(), FILE_BEHAVIOUR_ATTACH);
  }

  public static boolean isKbBehaviour(CopilotAppSource source) {
    return StringUtils.isEmpty(source.getBehaviour()) ||
        StringUtils.equals(source.getBehaviour(), FILE_BEHAVIOUR_KB);
  }
}
