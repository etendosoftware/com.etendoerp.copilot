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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.client.application.process.ResponseActionsBuilder;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.rest.RestServiceUtil;

public class SyncGraphImg extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger(SyncGraphImg.class);
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

      OBCriteria<CopilotApp> selectedToolsCriteria = OBDal.getInstance().createCriteria(
          CopilotApp.class);

      selectedToolsCriteria.add(Restrictions.in(CopilotAppTool.PROPERTY_ID, selectedRecordsList));
      List<CopilotApp> appList = selectedToolsCriteria.list();

      for (CopilotApp app : appList) {
        String imgBase64 = RestServiceUtil.getGraphImg(app);
        if (StringUtils.isNotBlank(imgBase64)) {
          String htmlTemplate = String.format(
              "<!DOCTYPE html>\n<html>\n\n<head>\n    <title>Imagen en base64</title>\n</head>\n\n<body>\n    <img src=\"data:image/jpeg;base64,%s\" \n    \n  style=\"max-width: 100%%; height: auto;\"\n    \n     />\n</body>\n\n</html>",
              imgBase64);
          app.setGraphImg(htmlTemplate);
          OBDal.getInstance().save(app);
          syncCount++;
        }
      }

      OBDal.getInstance().flush();
      result = returnSuccessMsg(result, syncCount, totalRecords);
      result.remove("refreshParent");

    } catch (Exception e) {
      log.error("Error in process", e);
      try {
        OBDal.getInstance().getConnection().rollback();
        result = new JSONObject();
        Throwable ex = DbUtility.getUnderlyingSQLException(e);
        String message;
        if (e instanceof ConnectException) {
          message = OBMessageUtils.messageBD("ETCOP_ConnCopilotError");
        } else {
          message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
        }
        result = getResponseBuilder().showMsgInProcessView(ResponseActionsBuilder.MessageType.ERROR,
            OBMessageUtils.messageBD("Error"), message, true).build();
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


  private JSONObject returnSuccessMsg(JSONObject result, int syncCount,
      int totalRecords) throws JSONException {
    // Message in tab from where the process is executed
    return getResponseBuilder().showMsgInProcessView(ResponseActionsBuilder.MessageType.SUCCESS,
        OBMessageUtils.messageBD("Success"),
        String.format(OBMessageUtils.messageBD("ETCOP_SuccessSync"), syncCount, totalRecords),
        true).build();
  }
}
