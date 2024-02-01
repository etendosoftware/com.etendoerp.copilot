package com.etendoerp.copilot.process;

import static com.etendoerp.copilot.util.OpenAIUtils.HEADER_CONTENT_TYPE;
import static com.etendoerp.copilot.util.OpenAIUtils.wrappWithJSONSchema;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;

public class SyncToolStructure extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger(SyncToolStructure.class);
  public static final String OPENAI_API_KEY = "OPENAI_API_KEY";
  public static final String DESCRIPTION = "description";

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {

    // Declare json to be returned
    JSONObject result = new JSONObject();
    try {

      // Get request parameters
      JSONObject request = new JSONObject(content);
      JSONArray selectedRecords = request.optJSONArray("recordIds");
      int totalRecords = (selectedRecords == null) ? 0 : selectedRecords.length();
      if (totalRecords == 0) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_NoSelectedRecords"));
      }
      int syncCount = 0;
      Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();

      //convert the JSONArray to a list of strings
      List<String> selectedRecordsList = new ArrayList<>();
      for (int i = 0; i < totalRecords; i++) {
        selectedRecordsList.add(selectedRecords.getString(i));
      }

      OBCriteria<CopilotTool> selectedToolsCriteria = OBDal.getInstance().createCriteria(
          CopilotTool.class);

      selectedToolsCriteria.add(Restrictions.in(CopilotAppTool.PROPERTY_ID, selectedRecordsList));
      List<CopilotTool> appToolsList = selectedToolsCriteria.list();
      //get the tools info from copilot

      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
      String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
      HttpRequest copilotRequest = HttpRequest.newBuilder()
          .uri(new URI(String.format("http://%s:%s/tools", copilotHost, copilotPort)))
          .headers(HEADER_CONTENT_TYPE, "application/json;charset=UTF-8")
          .version(HttpClient.Version.HTTP_1_1)
          .GET()
          .build();
      java.net.http.HttpResponse<String> responseFromCopilot = client.send(copilotRequest,
          java.net.http.HttpResponse.BodyHandlers.ofString());

      JSONObject responseJsonFromCopilot = new JSONObject(responseFromCopilot.body());
      logIfDebug(responseJsonFromCopilot.toString());

      for (CopilotTool erpTool : appToolsList) {
        JSONObject toolInfo = responseJsonFromCopilot.optJSONObject("answer").optJSONObject(erpTool.getValue());
        if (toolInfo != null) {
          JSONObject toolInfoJson = new JSONObject();
          toolInfoJson.put("type", "function");
          JSONObject funtionJson = new JSONObject();
          funtionJson.put("name", erpTool.getValue());
          funtionJson.put(DESCRIPTION, toolInfo.getString(DESCRIPTION));
          funtionJson.put("parameters", wrappWithJSONSchema(toolInfo.getJSONObject("parameters")));
          toolInfoJson.put("function", funtionJson);
          erpTool.setJsonStructure(toolInfoJson.toString(2));
          erpTool.setDescription(toolInfo.getString(DESCRIPTION));
          OBDal.getInstance().save(erpTool);
        }
        syncCount++;
      }
      OBDal.getInstance().flush();
      returnSuccessMsg(result, syncCount, totalRecords);

    } catch (InterruptedException e) {
      log.error(e);
      Thread.currentThread().interrupt();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
    } catch (Exception e) {
      log.error("Error in process", e);
      try {
        OBDal.getInstance().getConnection().rollback();
        result = new JSONObject();
        JSONObject errorMessage = new JSONObject();
        Throwable ex = DbUtility.getUnderlyingSQLException(e);
        String message;
        if (e instanceof ConnectException) {
          message = OBMessageUtils.messageBD("ETCOP_ConnCopilotError");
        } else {
          message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
        }
        errorMessage.put("severity", "error");
        errorMessage.put("title", OBMessageUtils.messageBD("Error"));
        errorMessage.put("text", message);
        result.put("message", errorMessage);
      } catch (Exception ignore) {
        log.error("Error in process", ignore);
      }
    }
    return result;
  }

  private static void logIfDebug(String text) {
    if (log.isDebugEnabled()) {
      log.debug(text);
    }
  }


  private void returnSuccessMsg(JSONObject result, int syncCount,
      int totalRecords) throws JSONException {

    // Message in tab from where the process is executed
    JSONArray actions = new JSONArray();
    JSONObject showMsgInProcessView = new JSONObject();
    showMsgInProcessView.put("msgType", "success");
    showMsgInProcessView.put("msgTitle", OBMessageUtils.messageBD("Success"));
    showMsgInProcessView.put("msgText",
        String.format(String.format(OBMessageUtils.messageBD("ETCOP_SuccessSync"), syncCount, totalRecords)));
    showMsgInProcessView.put("wait", true);
    JSONObject showMsgInProcessViewAction = new JSONObject();
    showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
    actions.put(showMsgInProcessViewAction);
    result.put("responseActions", actions);
  }
}
