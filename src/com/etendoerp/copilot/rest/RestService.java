package com.etendoerp.copilot.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.copilot.data.CopilotApp;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Message;
import org.openbravo.model.ad.ui.MessageTrl;

public class RestService extends HttpSecureAppServlet {

  public static final String QUESTION = "/question";
  public static final String GET_ASSISTANTS = "/assistants";
  public static final String APP_ID = "app_id";
  public static final String PROP_ASSISTANT_ID = "assistant_id";
  private static final String APP_TYPE_LANGCHAIN = "langchain";
  private static final String APP_TYPE_OPENAI = "openai-assistant";
  public static final String PROP_RESPONSE = "response";

  public static final String PROP_CONVERSATION_ID = "conversation_id";
  public static final String PROP_QUESTION = "question";
  public static final String PROP_TYPE = "type";
  public static final String COPILOT_MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";


  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    String path = request.getPathInfo();
    if (StringUtils.equalsIgnoreCase(path, GET_ASSISTANTS)) {
      handleAssistants(response);
      return;

    }  // add /labels to get the labels of the module
    else if (StringUtils.equalsIgnoreCase(path, "/labels")) {
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write(getJSONLabels().toString());
      return;
    }
    //if not a valid path, throw a error status
    response.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  private JSONObject getJSONLabels() {
    try {
      OBContext.setAdminMode(false);
      Language lang = OBContext.getOBContext().getLanguage();
      Module module = OBDal.getInstance().get(Module.class, COPILOT_MODULE_ID);
      JSONObject jsonLabels = new JSONObject();

      if (StringUtils.equals(module.getLanguage().getId(), lang.getId())) {
        OBCriteria<Message> msgCrit = OBDal.getInstance().createCriteria(Message.class);
        msgCrit.add(Restrictions.eq(Message.PROPERTY_MODULE, module));
        List<Message> msgList = msgCrit.list();
        for (Message msg : msgList) {
          try {
            jsonLabels.put(msg.getIdentifier(), msg.getMessageText());
          } catch (JSONException e) {
            log4j.error(e);
          }
        }
        return jsonLabels;
      } else {
        OBCriteria<MessageTrl> msgTrlCrit = OBDal.getInstance().createCriteria(MessageTrl.class);
        msgTrlCrit.add(Restrictions.eq(MessageTrl.PROPERTY_LANGUAGE, lang));
        msgTrlCrit.createAlias(MessageTrl.PROPERTY_MESSAGE, "msg");
        msgTrlCrit.add(Restrictions.eq("msg." + Message.PROPERTY_MODULE, module));
        List<MessageTrl> msgTrlList = msgTrlCrit.list();
        for (MessageTrl msgTrl : msgTrlList) {
          try {
            jsonLabels.put(msgTrl.getMessage().getIdentifier(), msgTrl.getMessageText());
          } catch (JSONException e) {
            log4j.error(e);
          }
        }
        return jsonLabels;
      }


    } finally {
      OBContext.restorePreviousMode();
    }

  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    String path = request.getPathInfo();
    if (StringUtils.equalsIgnoreCase(path, QUESTION)) {
      try {
        handleQuestion(request, response);
      } catch (JSONException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      }
      return;
    }
    //if not a valid path, throw a error status
    response.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  private void handleQuestion(HttpServletRequest request,
      HttpServletResponse response) throws IOException, JSONException {
    // read the json sent
    BufferedReader reader = request.getReader();
    StringBuilder sb = new StringBuilder();
    for (String line; (line = reader.readLine()) != null; ) {
      sb.append(line);
    }
    HttpResponse<String> responseFromCopilot = null;
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String appType;
    String appId;
    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
      String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
      String jsonRequestStr = sb.toString();
      JSONObject jsonRequestOriginal = new JSONObject(jsonRequestStr);
      JSONObject jsonRequestForCopilot = new JSONObject();
      jsonRequestForCopilot.put(PROP_QUESTION, jsonRequestOriginal.get(PROP_QUESTION));
      String conversationId = jsonRequestOriginal.optString(PROP_CONVERSATION_ID);
      if (StringUtils.isNotEmpty(conversationId)) {
        jsonRequestForCopilot.put(PROP_CONVERSATION_ID, conversationId);
      }
      //the app_id is the id of the CopilotApp, must be converted to the id of the openai assistant (if it is an openai assistant)
      // and we need to add the type of the assistant (openai or langchain)
      appId = jsonRequestOriginal.getString(APP_ID);
      CopilotApp copilotApp = OBDal.getInstance().get(CopilotApp.class, appId);
      if (copilotApp == null) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound"), appId));
      }
      appType = copilotApp.getAppType();
      if (StringUtils.equalsIgnoreCase(appType, APP_TYPE_LANGCHAIN)) {
        jsonRequestForCopilot.put(PROP_TYPE, APP_TYPE_LANGCHAIN);
      } else if (StringUtils.equalsIgnoreCase(appType, APP_TYPE_OPENAI)) {
        jsonRequestForCopilot.put(PROP_TYPE, APP_TYPE_OPENAI);
        jsonRequestForCopilot.put(PROP_ASSISTANT_ID, copilotApp.getOpenaiIdAssistant());
      } else {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("ETCOP_MissingAppType"), appType));
      }
      String bodyReq = jsonRequestForCopilot.toString();
      HttpRequest copilotRequest = HttpRequest.newBuilder()
          .uri(new URI(String.format("http://%s:%s/question", copilotHost, copilotPort)))
          .headers("Content-Type", "application/json;charset=UTF-8")
          .version(HttpClient.Version.HTTP_1_1)
          .POST(HttpRequest.BodyPublishers.ofString(bodyReq))
          .build();

      responseFromCopilot = client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
    } catch (URISyntaxException | InterruptedException e) {
      log4j.error(e);
      Thread.currentThread().interrupt();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
    }
    JSONObject responseJsonFromCopilot = new JSONObject(responseFromCopilot.body());
    JSONObject responseOriginal = new JSONObject();
    responseOriginal.put(APP_ID, appId);
    JSONObject answer = (JSONObject) responseJsonFromCopilot.get("answer");
    String conversationId = answer.optString(PROP_CONVERSATION_ID);
    if (StringUtils.isNotEmpty(conversationId)) {
      responseOriginal.put(PROP_CONVERSATION_ID, conversationId);
    }
    responseOriginal.put(PROP_RESPONSE, answer.get(PROP_RESPONSE));
    Date date = new Date();
    //getting the object of the Timestamp class
    Timestamp tms = new Timestamp(date.getTime());
    responseOriginal.put("timestamp", tms.toString());
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(responseOriginal.toString());
  }


  private void handleAssistants(HttpServletResponse response) {
    try {
      //send json of assistants
      JSONArray assistants = new JSONArray();
      for (CopilotApp copilotApp : OBDal.getInstance().createQuery(CopilotApp.class, "").list()) {
        JSONObject assistantJson = new JSONObject();
        assistantJson.put(APP_ID, copilotApp.getId());
        assistantJson.put("name", copilotApp.getName());
        assistants.put(assistantJson);
      }
      response.getWriter().write(assistants.toString());
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
