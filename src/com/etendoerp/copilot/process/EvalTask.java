package com.etendoerp.copilot.process;


import static com.etendoerp.copilot.background.BulkTaskExec.TASK_STATUS_IN_PROGRESS;
import static com.etendoerp.copilot.process.AddBulkTasks.getStatus;
import static com.etendoerp.copilot.process.ExecTask.getTaskList;
import static com.etendoerp.copilot.rest.RestServiceUtil.APP_ID;
import static com.etendoerp.copilot.util.CopilotConstants.PROP_QUESTION;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.scheduling.ProcessLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * ExampleButtonProcess class to handle the process execution for a button.
 */
public class EvalTask extends Action {

  private static final Logger log = LoggerFactory.getLogger(EvalTask.class);
  private static final String EVALUATOR_ID = "3A583408E7484BF48ED8E45DF09A6044";
  public static final String REQUIRES_REVIEW = "REQ";

  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    ActionResult actionResult = new ActionResult();
    try {
      // Example of custom logic executed when the button is clicked
      List<Task> selectedTasks = getTaskList(parameters);

      for (Task task : selectedTasks) {
        OBDal.getInstance().refresh(task);
        evaluateTask(task, null);
      }
      actionResult.setType(Result.Type.SUCCESS);
      actionResult.setMessage(new JSONObject().put("message",
          String.format(OBMessageUtils.messageBD("ETCOP_ExecTask_Success"), selectedTasks.size())
      ).toString());
    } catch (Exception e) {
      try {
        actionResult.setMessage("Error during process execution: " + e.getMessage());
        actionResult.setType(Result.Type.ERROR);
      } catch (Exception ex) {
        log.error(ex.getMessage());
      }
    }
    return actionResult;
  }


  /**
   * Processes the given {@link Task} by executing it and updating its status.
   * Logs the processing steps and errors using the provided {@link ProcessLogger}.
   *
   * <p>
   * On successful execution, the task status is set to completed. If an exception occurs,
   * the task status is set to in progress and the error is logged. In all cases, the task
   * is saved and the session is committed.
   * </p>
   *
   * @param task
   *     the {@link Task} to be processed
   * @param logger
   *     the {@link ProcessLogger} used for logging processing steps and errors; may be null
   */
  public static void evaluateTask(Task task, ProcessLogger logger) {
    try {
      if (logger != null) {
        logger.log("Processing task " + task.getId() + "\n");
      }

      JSONObject body = new JSONObject();
      body.put(APP_ID, EVALUATOR_ID);
      body.put(PROP_QUESTION, task.getEtcopResponse());
      var responseQuest = RestServiceUtil.handleQuestion(false, null, body);
      Status nextStatus = getStatus(responseQuest.getString("response"));
      if (nextStatus == null) {
        String resp = responseQuest.optString("response");
        log.error("Status not found for response: {}", resp);
        nextStatus = getStatus(REQUIRES_REVIEW);
      }
      task.setStatus(nextStatus);
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


  /**
   * Returns the {@link Class} object associated with the {@link Task} input type.
   * This method is typically used to specify the expected input type for the process.
   *
   * @return the {@code Class} object representing {@code Task}
   */
  @Override
  protected Class<Task> getInputClass() {
    return Task.class;
  }
}
