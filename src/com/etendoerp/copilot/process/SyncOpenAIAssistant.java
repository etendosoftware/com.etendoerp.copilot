package com.etendoerp.copilot.process;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.OpenAIUtils;

public class SyncOpenAIAssistant extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger(SyncOpenAIAssistant.class);
  public static final String OPENAI_API_KEY = "OPENAI_API_KEY";

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {

    // Declare json to be returned
    JSONObject result = new JSONObject();
    try {

      // Get request parameters
      JSONObject request = new JSONObject(content);
      JSONArray selecterRecords = request.optJSONArray("recordIds");
      int totalRecords = (selecterRecords == null) ? 0 : selecterRecords.length();
      if (totalRecords == 0) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_NoSelectedRecords")); //TODO: msg
      }
      int syncCount = 0;
      //print the current folder of this class
      String filePath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
      File file = new File(filePath + "../../gradle.properties");
      //read the file "gradle.properties" in the current folder
      String configs;
      try (FileReader filereader = new FileReader(file)) {
        int j;
        configs = "";
        while ((j = filereader.read()) != -1) {
          configs += (char) j;
        }
      }
      //convert the string to a properties object
      Properties properties = new Properties();
      properties.load(new java.io.StringReader(configs));
      //get the value of the property "openaiApiKey"

      String openaiApiKey = properties.getProperty(OPENAI_API_KEY);

      List<CopilotApp> appList = new ArrayList<CopilotApp>();

      for (int i = 0; i < totalRecords; i++) {
        CopilotApp app = OBDal.getInstance().get(CopilotApp.class, selecterRecords.getString(i));
        appList.add(app);
      }
      Set<CopilotFile> filesToSync = new HashSet<CopilotFile>();
      for (CopilotApp app : appList) {
        filesToSync.addAll(
            app.getETCOPAppSourceList().stream().map(CopilotAppSource::getFile).collect(Collectors.toList()));
      }
      for (CopilotFile fileToSync : filesToSync) {
        OpenAIUtils.syncFile(fileToSync, openaiApiKey);
      }

      for (CopilotApp app : appList) {
        OBDal.getInstance().refresh(app);
        syncCount = callSync(syncCount, openaiApiKey, app);
      }
      returnSuccessMsg(result, syncCount, totalRecords);

    } catch (Exception e) {
      log.error("Error in process", e);
      try {
        OBDal.getInstance().getConnection().rollback();
        result = new JSONObject();
        JSONObject errorMessage = new JSONObject();
        Throwable ex = DbUtility.getUnderlyingSQLException(e);
        String message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
        errorMessage.put("severity", "error");
        errorMessage.put("title", OBMessageUtils.messageBD("Error"));
        errorMessage.put("text", message);
        result.put("message", errorMessage);
      } catch (Exception ignore) {
      }
    }
    return result;
  }

  private int callSync(int syncCount, String openai_api_key, CopilotApp app) {
    try {
      OpenAIUtils.syncAssistant(openai_api_key, app);
      syncCount++;
    } catch (Exception e) {
      log.error(" Error in syncAssistant", e);
    }
    return syncCount;
  }

  private void returnSuccessMsg(JSONObject result, int syncCount,
      int totalRecords) throws JSONException {

    // Message in tab from where the process is executed
    JSONArray actions = new JSONArray();
    JSONObject showMsgInProcessView = new JSONObject();
    showMsgInProcessView.put("msgType", "success");
    showMsgInProcessView.put("msgTitle", OBMessageUtils.messageBD("Success"));
    showMsgInProcessView.put("msgText",
        String.format(OBMessageUtils.messageBD("a"), syncCount, totalRecords - syncCount)); //TODO: msg
    showMsgInProcessView.put("wait", true);
    JSONObject showMsgInProcessViewAction = new JSONObject();
    showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
    actions.put(showMsgInProcessViewAction);
    result.put("responseActions", actions);
  }
}
