package com.etendoerp.copilot.process;


import static com.etendoerp.copilot.background.BulkTaskExec.TASK_STATUS_COMPLETED;
import static com.etendoerp.copilot.background.BulkTaskExec.TASK_STATUS_EVAL;
import static com.etendoerp.copilot.background.BulkTaskExec.TASK_STATUS_IN_PROGRESS;
import static com.etendoerp.copilot.process.AddBulkTasks.getStatus;
import static com.etendoerp.copilot.rest.RestServiceUtil.APP_ID;
import static com.etendoerp.copilot.util.CopilotConstants.PROP_QUESTION;

import java.io.IOException;
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
import com.etendoerp.task.data.Task;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * ExampleButtonProcess class to handle the process execution for a button.
 */
public class ExecTask extends Action {

  private static final Logger log = LoggerFactory.getLogger(ExecTask.class);

  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    ActionResult actionResult = new ActionResult();
    try {
      // Example of custom logic executed when the button is clicked
      List<Task> selectedTasks = getTaskList(parameters);
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
   * Retrieves a list of {@link Task} objects based on the provided JSON parameters.
   * <p>
   * If the parameters contain an "asyncProcessId", the method extracts the task ID from the nested
   * "params" JSON object and returns a singleton list containing the corresponding {@link Task}.
   * If the task is not found, a {@link JSONException} is thrown.
   * <p>
   * Otherwise, the method expects a "recordIds" array in the parameters, retrieves each corresponding
   * {@link Task} by ID, and returns a list of these tasks.
   *
   * @param parameters
   *     The JSON object containing the parameters for task retrieval.
   * @return A list of {@link Task} objects corresponding to the provided parameters.
   * @throws JSONException
   *     If required fields are missing, malformed, or if a task cannot be found by ID.
   */
  public static List<Task> getTaskList(JSONObject parameters) throws JSONException {
    if (parameters.has("asyncProcessId")) {
      String paramsJsonString = parameters.getString("params");
      JSONObject paramsJson = new JSONObject(paramsJsonString);
      String taskId = paramsJson.getJSONObject("after").getString("etask_task_id");
      Task task = OBDal.getInstance().get(Task.class, taskId);
      if (task == null) {
        throw new JSONException("Task with ID " + taskId + " not found.");
      }
      return List.of(task);
    }
    JSONObject paramValuesJson = parameters;
    JSONArray recordIdsJson = paramValuesJson.getJSONArray("recordIds");
    ArrayList<String> recordIds = new ArrayList<>();
    for (int i = 0; i < recordIdsJson.length(); i++) {
      recordIds.add(recordIdsJson.getString(i));
    }
    return recordIds.stream().map(id -> OBDal.getInstance().get(Task.class, id)).collect(
        Collectors.toList());
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
  public static void processTask(Task task, ProcessLogger logger) {
    try {
      if (logger != null) {
        logger.log("Processing task " + task.getId() + "\n");
      }

      execTask(task);
      task.setStatus(getStatus(TASK_STATUS_EVAL));
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
   * Executes a task by sending its question to a remote service and storing the response.
   *
   * <p>This method performs the following steps:
   * <ul>
   *   <li>Retrieves the question and agent associated with the given {@link Task}.</li>
   *   <li>Constructs a JSON request body containing the agent ID and the question.</li>
   *   <li>Sends the request to a remote service using {@code RestServiceUtil.handleQuestion}.</li>
   *   <li>Stores the response in the task object.</li>
   *   <li>Saves and flushes the updated task using the OBDal persistence layer.</li>
   * </ul>
   *
   * @param task
   *     The {@link Task} to execute and update with the response.
   * @throws JSONException
   *     If there is an error constructing the JSON request body.
   * @throws IOException
   *     If an I/O error occurs during the remote service call.
   */
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
