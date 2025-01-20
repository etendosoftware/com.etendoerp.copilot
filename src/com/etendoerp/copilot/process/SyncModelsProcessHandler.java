package com.etendoerp.copilot.process;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Handles the synchronization of models process.
 * <p>
 * This class extends BaseProcessActionHandler to provide the functionality for synchronizing models.
 * It handles the execution of the process and builds the response message.
 */
public class SyncModelsProcessHandler extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger(SyncModelsProcessHandler.class);
  public static final String ERROR = "error";

  /**
   * Executes the synchronization process.
   * <p>
   * This method sets the admin mode, calls the syncModels method, and builds the success message.
   * If an exception occurs, it handles the error, rolls back the transaction, and builds the error message.
   *
   * @param parameters The parameters for the process.
   * @param content The content of the process.
   * @return A JSONObject containing the result of the process execution.
   */
  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    // Declare json to be returned
    JSONObject result = new JSONObject();
    try {
      OBContext.setAdminMode();
      CopilotUtils.syncModels();
      result = buildMessage();
    } catch (Exception e) {
      log.error("Error in process", e);
      try {
        OBDal.getInstance().getConnection().rollback();
        result = new JSONObject();
        JSONObject errorMessage = new JSONObject();
        Throwable ex = DbUtility.getUnderlyingSQLException(e);
        String message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
        errorMessage.put("severity", ERROR);
        errorMessage.put("title", OBMessageUtils.messageBD("Error"));
        errorMessage.put("text", message);
        result.put("message", errorMessage);
      } catch (Exception ex) {
        log.error("Error in process", ex);
      }
    } finally {
      OBContext.restorePreviousMode();
    }

    return result;
  }

  /**
   * Builds a JSON message object to be displayed in the process view.
   * <p>
   * This method constructs a JSON object containing a success message.
   * The message is formatted and added to the response actions to be shown in the process view.
   *
   * @return A JSONObject containing the response actions with the success message.
   * @throws JSONException If an error occurs while creating the JSON object.
   */
  private JSONObject buildMessage() throws JSONException {
    JSONObject result = new JSONObject();
    // Message in tab from where the process is executed
    JSONArray actions = new JSONArray();
    JSONObject showMsgInProcessView = new JSONObject();
    showMsgInProcessView.put("msgType", "success");
    showMsgInProcessView.put("msgTitle", OBMessageUtils.messageBD("Success"));
    showMsgInProcessView.put("msgText", OBMessageUtils.messageBD("success"));
    showMsgInProcessView.put("wait", true);
    JSONObject showMsgInProcessViewAction = new JSONObject();
    showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
    actions.put(showMsgInProcessViewAction);
    result.put("responseActions", actions);
    return result;
  }
}
