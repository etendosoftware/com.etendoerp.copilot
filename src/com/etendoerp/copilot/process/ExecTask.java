package com.etendoerp.copilot.process;


import static com.etendoerp.copilot.rest.RestServiceUtil.APP_ID;
import static com.etendoerp.copilot.util.CopilotConstants.PROP_QUESTION;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.task.data.Task;

/**
 * ExampleButtonProcess class to handle the process execution for a button.
 */
public class ExecTask extends BaseProcessActionHandler {

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    JSONObject result = new JSONObject();
    try {
      // Example of custom logic executed when the button is clicked
      JSONObject paramValuesJson = new JSONObject(content);
      execTask(OBDal.getInstance().get(Task.class, paramValuesJson.get("Etask_Task_ID")));
      result.put("message", "Process executed successfully");
      result.put("severity", "success");
    } catch (Exception e) {
      try {
        result.put("message", "Error during process execution: " + e.getMessage());
        result.put("severity", "error");
      } catch (Exception ex) {
        // Logging in case of failure in error handling
      }
    }
    return result;
  }

  public static void execTask(Task task) throws JSONException, IOException {
    String question = task.getEtcopQuestion();
    var agent = task.getETCOPAgent();
    JSONObject body = new JSONObject();
    body.put(APP_ID, agent.getId());
    body.put(PROP_QUESTION, question);
    var responseQuest = RestServiceUtil.handleQuestion(false, null, body);
    if (StringUtils.equalsIgnoreCase(agent.getAppType(), CopilotConstants.APP_TYPE_LANGGRAPH)) {
      JSONArray msgs = responseQuest.getJSONObject("response").getJSONArray("messages");
      //the last message is from the graph
      task.setEtcopResponse(msgs.getJSONObject(msgs.length() - 1).getString("content"));
    } else {
      task.setEtcopResponse(responseQuest.toString());
    }
    OBDal.getInstance().save(task);
    OBDal.getInstance().flush();
  }
}
