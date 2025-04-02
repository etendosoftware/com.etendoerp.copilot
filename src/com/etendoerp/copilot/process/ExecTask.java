package com.etendoerp.copilot.process;


import static com.etendoerp.copilot.background.BulkTaskExec.TASK_STATUS_COMPLETED;
import static com.etendoerp.copilot.background.BulkTaskExec.TASK_STATUS_IN_PROGRESS;
import static com.etendoerp.copilot.process.AddBulkTasks.getStatus;
import static com.etendoerp.copilot.rest.RestServiceUtil.APP_ID;
import static com.etendoerp.copilot.util.CopilotConstants.PROP_QUESTION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.scheduling.ProcessLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.task.data.Task;

/**
 * ExampleButtonProcess class to handle the process execution for a button.
 */
public class ExecTask extends BaseProcessActionHandler {

  private static final Logger log = LoggerFactory.getLogger(ExecTask.class);

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    JSONObject result = new JSONObject();
    try {
      // Example of custom logic executed when the button is clicked
      JSONObject paramValuesJson = new JSONObject(content);
      JSONArray recordIdsJson = paramValuesJson.getJSONArray("recordIds");
      ArrayList<String> recordIds = new ArrayList<String>();
      for (int i = 0; i < recordIdsJson.length(); i++) {
        recordIds.add(recordIdsJson.getString(i));
      }
      List<Task> selectedTasks = recordIds.stream().map(id -> OBDal.getInstance().get(Task.class, id)).collect(
          Collectors.toList());
      for (Task task : selectedTasks) {
        task.setStatus(getStatus(TASK_STATUS_IN_PROGRESS));
        OBDal.getInstance().save(task);
      }
      OBDal.getInstance().flush();
      SessionHandler.getInstance().commitAndStart();
      for (Task task : selectedTasks) {
        OBDal.getInstance().refresh(task);
        processTask(task, null);
      }
      JSONArray actions = new JSONArray();
      JSONObject showMsgInProcessView = new JSONObject();
      showMsgInProcessView.put("msgType", "success");
      showMsgInProcessView.put("msgText", "Tasks executed successfully");
      showMsgInProcessView.put("wait", true);
      JSONObject showMsgInProcessViewAction = new JSONObject();
      showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
      actions.put(showMsgInProcessViewAction);
      return result.put("responseActions", actions);
    } catch (Exception e) {
      try {
        result.put("message", "Error during process execution: " + e.getMessage());
        result.put("severity", "error");
      } catch (Exception ex) {
        log.error(ex.getMessage());
      }
    }
    return result;
  }

  public static void processTask(Task task, ProcessLogger logger) {
    try {
      if (logger != null) {
        logger.log("Processing task " + task.getId() + "\n");
      }

      execTask(task);
      task.setStatus(getStatus(TASK_STATUS_COMPLETED));
    } catch (Exception e) {
      if (logger != null) {
        logger.log("Error processing task " + task.getId() + ": " + e.getMessage() + "\n");
      }
      task.setStatus(getStatus(TASK_STATUS_IN_PROGRESS));
    } finally {
      OBDal.getInstance().save(task);
      OBDal.getInstance().flush();
      SessionHandler.getInstance().commitAndStart();
    }
  }

  public static void execTask(Task task) throws JSONException, IOException {
    String question = task.getEtcopQuestion();
    var agent = task.getETCOPAgent();
    JSONObject body = new JSONObject();
    body.put(APP_ID, agent.getId());
    body.put(PROP_QUESTION, question);
    var responseQuest = RestServiceUtil.handleQuestion(false, null, body);
    task.setEtcopResponse(responseQuest.toString());

    OBDal.getInstance().save(task);
    OBDal.getInstance().flush();
  }
}
