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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotOpenAIModel;
import com.etendoerp.copilot.rest.RestService;
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
        throw new OBException(OBMessageUtils.messageBD("ETCOP_NoSelectedRecords"));
      }
      int syncCount = 0;
      Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();

      String openaiApiKey = properties.getProperty(OPENAI_API_KEY);

      syncOpenaiModels(openaiApiKey);
      List<CopilotApp> appList = new ArrayList<>();

      for (int i = 0; i < totalRecords; i++) {
        CopilotApp app = OBDal.getInstance().get(CopilotApp.class, selecterRecords.getString(i));
        appList.add(app);
      }
      Set<CopilotFile> filesToSync = new HashSet<>();
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
        log.error("Error in process", ignore);
      }
    }
    return result;
  }

  private void syncOpenaiModels(String openaiApiKey) {
    //ask to openai for the list of models
    JSONArray modelJSONArray = OpenAIUtils.getModelList(openaiApiKey);
    //transfer ids of json array to a list of strings
    List<String> modelIds = new ArrayList<>();
    for (int i = 0; i < modelJSONArray.length(); i++) {
      try {
        JSONObject modelObj = modelJSONArray.getJSONObject(i);
        if (!StringUtils.equals(modelObj.getString("owned_by"), "openai-dev") &&
            !StringUtils.equals(modelObj.getString("owned_by"), "openai-internal")) {
          modelIds.add(modelObj.getString("id"));
        }
      } catch (JSONException e) {
        log.error("Error in syncOpenaiModels", e);
      }
    }
    //now we have a list of ids, we can get the list of models in the database
    List<CopilotOpenAIModel> modelsInDB = OBDal.getInstance().createCriteria(CopilotOpenAIModel.class).list();

    //now we will check the models of the database that are not in the list of models from openai, to mark them as not active
    for (CopilotOpenAIModel modelInDB : modelsInDB) {
      if (!modelIds.contains(modelInDB.getSearchkey())) {
        modelInDB.setActive(false);
        OBDal.getInstance().save(modelInDB);
      } else {
        modelIds.remove(modelInDB.getSearchkey());
      }
    }
    //the models that are not in the database, we will create them,
    for (String modelId : modelIds) {
      CopilotOpenAIModel model = OBProvider.getInstance().get(CopilotOpenAIModel.class);
      model.setSearchkey(modelId);
      model.setName(modelId);
      model.setActive(true);
      OBDal.getInstance().save(model);
    }
    OBDal.getInstance().flush();
    //check for Apps that needs a model and don't have one, set the Default
    List<CopilotApp> appsWithoutModel = OBDal.getInstance().createCriteria(CopilotApp.class)
        .add(Restrictions.isNull(CopilotApp.PROPERTY_MODEL))
        .add(Restrictions.eq(CopilotApp.PROPERTY_APPTYPE, RestService.APP_TYPE_OPENAI))
        .list();

    CopilotOpenAIModel defaultModel = (CopilotOpenAIModel) OBDal.getInstance().createCriteria(CopilotOpenAIModel.class)
        .add(Restrictions.eq(CopilotOpenAIModel.PROPERTY_SEARCHKEY, "gpt-4-1106-preview")).setMaxResults(1)
        .uniqueResult();
    for (CopilotApp app : appsWithoutModel) {
      app.setModel(defaultModel);
      OBDal.getInstance().save(app);
    }
    OBDal.getInstance().flush();

  }

  private int callSync(int syncCount, String openaiApiKey, CopilotApp app) {
    OpenAIUtils.syncAssistant(openaiApiKey, app);
    syncCount++;

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
        String.format(String.format(OBMessageUtils.messageBD("ETCOP_SuccessSync"), syncCount, totalRecords)));
    showMsgInProcessView.put("wait", true);
    JSONObject showMsgInProcessViewAction = new JSONObject();
    showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
    actions.put(showMsgInProcessViewAction);
    result.put("responseActions", actions);
  }
}
