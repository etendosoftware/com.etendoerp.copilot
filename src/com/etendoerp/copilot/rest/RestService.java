package com.etendoerp.copilot.rest;


import java.io.BufferedReader;
import java.io.File;
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

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
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

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;

public class RestService extends HttpSecureAppServlet {

  public static final String QUESTION = "/question";
  public static final String GET_ASSISTANTS = "/assistants";
  public static final String APP_ID = "app_id";
  public static final String PROP_ASSISTANT_ID = "assistant_id";
  public static final String APP_TYPE_LANGCHAIN = "langchain";
  public static final String APP_TYPE_OPENAI = "openai-assistant";
  public static final String PROP_RESPONSE = "response";

  public static final String PROP_CONVERSATION_ID = "conversation_id";
  public static final String PROP_QUESTION = "question";
  public static final String PROP_TYPE = "type";
  public static final String COPILOT_MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";
  public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json;charset=UTF-8";


  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    String path = request.getPathInfo();
    try {
      if (StringUtils.equalsIgnoreCase(path, GET_ASSISTANTS)) {
        handleAssistants(response);
        return;

      }  // add /labels to get the labels of the module
      else if (StringUtils.equalsIgnoreCase(path, "/labels")) {
        response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
        response.getWriter().write(getJSONLabels().toString());
        return;
      }
      //if not a valid path, throw a error status
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } catch (Exception e) {
      log4j.error(e);
      try {

        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());

      } catch (IOException ioException) {
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    }
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
    try {
      if (StringUtils.equalsIgnoreCase(path, QUESTION)) {
        handleQuestion(request, response);
        return;
      } else if (StringUtils.equalsIgnoreCase(path, "/file")) {
        handleFile(request, response);
        return;
      }
      //if not a valid path, throw a error status
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } catch (Exception e) {
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    }
  }

  private void handleFile(HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    logIfDebug("handleFile");
    //En la request nos enviaran un form-data con  el campo file con el archivo

    boolean isMultipart = ServletFileUpload.isMultipartContent(request);
    logIfDebug(String.format("isMultipart: %s", isMultipart));
    FileItemFactory factory = new DiskFileItemFactory();

    ServletFileUpload upload = new ServletFileUpload(factory);
    List<FileItem> items = upload.parseRequest(request);
    logIfDebug(String.format("items: %d", items.size()));
    JSONObject responseJson = new JSONObject();

    for (FileItem item : items) {
      if (!item.isFormField()) {
        DiskFileItem itemDisk = (DiskFileItem) item;
        String originalFileName = item.getName();
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String filenameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf("."));
        //check if the file is in memory or in disk and create a temp file,
        File f = File.createTempFile(filenameWithoutExt + "_", extension);
        if (itemDisk.isInMemory()) {
          //if the file is in memory, write it to the temp file
          itemDisk.write(f);
        } else {
          //if the file is in disk, copy it to the temp file
          boolean successRename = itemDisk.getStoreLocation().renameTo(f);
          if (!successRename) {
            throw new OBException(
                String.format(OBMessageUtils.messageBD("ETCOP_ErrorSavingFile"), item.getName()));
          }
        }
        responseJson.put(item.getFieldName(), f.getName());

      }
    }
    response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
    response.getWriter().write(responseJson.toString());

  }

  private void logIfDebug(String msg) {
    if (log4j.isDebugEnabled()) {
      log4j.debug(msg);
    }
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
          .headers("Content-Type", APPLICATION_JSON_CHARSET_UTF_8)
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
    response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
    response.getWriter().write(responseOriginal.toString());
  }


  private void handleAssistants(HttpServletResponse response) {
    try {
      //send json of assistants
      JSONArray assistants = new JSONArray();
      List<CopilotRoleApp> appList = OBDal.getInstance().createCriteria(CopilotRoleApp.class)
          .add(Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, OBContext.getOBContext().getRole()))
          .list();
      for (CopilotRoleApp roleApp : appList) {
        JSONObject assistantJson = new JSONObject();
        assistantJson.put(APP_ID, roleApp.getCopilotApp().getId());
        assistantJson.put("name", roleApp.getCopilotApp().getName());
        assistants.put(assistantJson);
      }
      response.getWriter().write(assistants.toString());
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
