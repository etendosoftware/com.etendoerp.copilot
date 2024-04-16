package com.etendoerp.copilot.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.util.OpenAIUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.SystemInfo;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Message;
import org.openbravo.model.ad.ui.MessageTrl;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.hook.CopilotQuestionHookManager;
import com.etendoerp.copilot.util.CopilotConstants;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

public class RestService extends HttpSecureAppServlet {

  public static final String QUESTION = "/question";
  public static final String GET_ASSISTANTS = "/assistants";
  public static final String APP_ID = "app_id";
  public static final String PROP_ASSISTANT_ID = "assistant_id";
  public static final String PROP_RESPONSE = "response";

  public static final String PROP_CONVERSATION_ID = "conversation_id";
  public static final String PROP_QUESTION = "question";
  public static final String PROP_TYPE = "type";
  public static final String COPILOT_MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";
  public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json;charset=UTF-8";
  public static final String FILE = "/file";
  public static final String PROP_PROVIDER = "provider";
  public static final String PROP_MODEL = "model";
  public static final String PROP_SYSTEM_PROMPT = "system_prompt";
  private static final String PROVIDER_OPENAI = "openai";
  private static final String PROVIDER_GEMINI = "gemini";
  public static final String PROVIDER_OPENAI_VALUE = "O";
  public static final String PROVIDER_GEMINI_VALUE = "G";
  public static final String ERROR = "error";

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    String path = request.getPathInfo();
    try {
      if (StringUtils.equalsIgnoreCase(path, GET_ASSISTANTS)) {
        handleAssistants(response);
        return;

      }  //  /labels to get the labels of the module
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
      OBContext.setAdminMode();
      if (StringUtils.equalsIgnoreCase(path, QUESTION)) {
        handleQuestion(request, response);
        return;
      } else if (StringUtils.equalsIgnoreCase(path, FILE)) {
        handleFile(request, response);
        return;
      }
      //if not a valid path, throw a error status
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } catch (Exception e) {
      log4j.error(e);
      try {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (Exception e2) {
        log4j.error(e2);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e2.getMessage());
      }
      Thread.currentThread().interrupt();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private void sendErrorResponse(HttpServletResponse response, int status,
      String message) throws IOException, JSONException {
    response.setStatus(status);
    JSONObject error = new JSONObject();
    error.put(ERROR, message);
    response.getWriter().write(error.toString());
  }

  private void handleFile(HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    logIfDebug("handleFile");
    // in the request we will receive a form-data with the field file with the file

    boolean isMultipart = ServletFileUpload.isMultipartContent(request);
    logIfDebug(String.format("isMultipart: %s", isMultipart));
    FileItemFactory factory = new DiskFileItemFactory();

    ServletFileUpload upload = new ServletFileUpload(factory);
    List<FileItem> items = upload.parseRequest(request);
    logIfDebug(String.format("items: %d", items.size()));
    JSONObject responseJson = new JSONObject();
    //create a list of files, for delete them later when the process finish
    ArrayList<File> fileListToDelete = new ArrayList<>();

    for (FileItem item : items) {
      if (item.isFormField()) {
        continue;
      }
      DiskFileItem itemDisk = (DiskFileItem) item;
      String originalFileName = item.getName();
      String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
      String filenameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf("."));
      //check if the file is in memory or in disk and create a temp file,
      File f = File.createTempFile(filenameWithoutExt + "_", extension);
      f.deleteOnExit();
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
      checkSizeFile(f);
      String fileUUID = UUID.randomUUID().toString();
      fileListToDelete.add(f);
      //print the current directory of the class
      String sourcePath = OBPropertiesProvider.getInstance()
          .getOpenbravoProperties()
          .getProperty("source.path");
      String buildCopilotPath = sourcePath + "/build/copilot";

      String modulePath = sourcePath + "/modules/com.etendoerp.copilot";
      // copy the file to the buildCopilotPath folder, in a subfolder with the name of the file_id
      String filePath = String.format("/copilotTempFiles/%s/%s", fileUUID, originalFileName);
      saveFileTemp(f, filePath);
      String pathForStandardCopy = buildCopilotPath + filePath;
      File fileCopilotFolder = new File(pathForStandardCopy);
      fileCopilotFolder.getParentFile().mkdirs();
      Files.copy(f.toPath(), fileCopilotFolder.toPath());
      //copy the file to the module folder, for the development
      if (isDevelopment()) {
        String pathForDevCopy = modulePath + filePath;
        File fileModuleFolder = new File(pathForDevCopy);
        fileModuleFolder.getParentFile().mkdirs();
        Files.copy(f.toPath(), fileModuleFolder.toPath());
      }
      responseJson.put(item.getFieldName(), filePath);
    }
    OBDal.getInstance().flush();
    //delete the temp files
    for (File f : fileListToDelete) {
      try {
        logIfDebug(String.format("deleting file: %s", f.getName()));
        Files.deleteIfExists(f.toPath());
      } catch (Exception e) {
        log4j.error(e);
      }
    }
    response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
    response.getWriter().write(responseJson.toString());
  }

  private boolean isDevelopment() throws ServletException {
    SystemInfo.load(new DalConnectionProvider(false));
    String purpose = SystemInfo.getSystemInfo().getProperty("instancePurpose");
    return StringUtils.equalsIgnoreCase("D", purpose);
  }

  private void checkSizeFile(File f) {
    //check the size of the file: must be max 512mb
    long size = f.length();
    if (size > 512 * 1024 * 1024) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_FileTooBig"), f.getName()));
    }
  }

  private void logIfDebug(String msg) {
    if (log4j.isDebugEnabled()) {
      log4j.debug(msg);
    }
  }

  private void handleQuestion(HttpServletRequest request, HttpServletResponse response)
      throws IOException, JSONException, URISyntaxException, InterruptedException {
    String requestBody = readRequestBody(request);
    JSONObject jsonRequestOriginal = new JSONObject(requestBody);
    JSONObject jsonRequestForCopilot = new JSONObject();

    // The app_id is the id of the CopilotApp, must be converted to the id of the openai assistant (if it is an openai assistant)
    // and we need to add the type of the assistant (openai or langchain)
    String appId = jsonRequestOriginal.getString(APP_ID);
    CopilotApp copilotApp = getCopilotApp(appId);

    String conversationId = jsonRequestOriginal.optString(PROP_CONVERSATION_ID);
    if (StringUtils.isNotEmpty(conversationId)) {
      jsonRequestForCopilot.put(PROP_CONVERSATION_ID, conversationId);
    }

    String appType = copilotApp.getAppType();
    StringBuilder prompt = handleAppType(jsonRequestForCopilot, copilotApp, appType);

    jsonRequestForCopilot.put(PROP_QUESTION, jsonRequestOriginal.get(PROP_QUESTION));

    String questionAttachedFileId = jsonRequestOriginal.optString("file");
    handleAttachedFileId(jsonRequestForCopilot, questionAttachedFileId, copilotApp, prompt);

    addExtraContextWithHooks(copilotApp, jsonRequestForCopilot);

    String bodyReq = jsonRequestForCopilot.toString();
    HttpResponse<String> responseFromCopilot = sendCopilotRequest(bodyReq);

    handleResponseFromCopilot(response, responseFromCopilot, appId);
  }

  private String readRequestBody(HttpServletRequest request) throws IOException {
    BufferedReader reader = request.getReader();
    return reader.lines().collect(Collectors.joining());
  }

  private CopilotApp getCopilotApp(String appId) {
    CopilotApp copilotApp = OBDal.getInstance().get(CopilotApp.class, appId);
    if (copilotApp == null) {
      throw new IllegalArgumentException("App not found: " + appId);
    }
    return copilotApp;
  }

  private StringBuilder handleAppType(JSONObject jsonRequestForCopilot, CopilotApp copilotApp,
      String appType) throws JSONException {
    StringBuilder prompt = new StringBuilder();
    if (StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_LANGCHAIN)) {
      handleLangChainAppType(jsonRequestForCopilot, copilotApp, prompt);
    } else if (StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_OPENAI)) {
      handleOpenAIAppType(jsonRequestForCopilot, copilotApp);
    } else {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_MissingAppType"), appType));
    }
    return prompt;
  }

  private void handleLangChainAppType(JSONObject jsonRequestForCopilot, CopilotApp copilotApp,
      StringBuilder prompt) throws JSONException {
    jsonRequestForCopilot.put(PROP_TYPE, CopilotConstants.APP_TYPE_LANGCHAIN);
    if (StringUtils.equals(copilotApp.getProvider(), PROVIDER_OPENAI_VALUE)) {
      jsonRequestForCopilot.put(PROP_PROVIDER, PROVIDER_OPENAI);
    } else if (StringUtils.equals(copilotApp.getProvider(), PROVIDER_GEMINI_VALUE)) {
      jsonRequestForCopilot.put(PROP_PROVIDER, PROVIDER_GEMINI);
      jsonRequestForCopilot.put(PROP_MODEL, "gemini-1.5-pro-latest");
    } else {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MissingProvider"),
          copilotApp.getProvider()));
    }
    if (!StringUtils.isEmpty(copilotApp.getPrompt())) {
      prompt.append(copilotApp.getPrompt()).append("\n");
    }
  }

  private void handleOpenAIAppType(JSONObject jsonRequestForCopilot, CopilotApp copilotApp)
      throws JSONException {
    jsonRequestForCopilot.put(PROP_TYPE, CopilotConstants.APP_TYPE_OPENAI);
    jsonRequestForCopilot.put(PROP_ASSISTANT_ID, copilotApp.getOpenaiIdAssistant());
  }

  private void handleAttachedFileId(JSONObject jsonRequestForCopilot, String questionAttachedFileId,
      CopilotApp copilotApp, StringBuilder prompt) throws JSONException {
    if (StringUtils.isNotEmpty(questionAttachedFileId)) {
      CopilotFile copilotFile = (CopilotFile) OBDal.getInstance()
          .createCriteria(CopilotFile.class)
          .add(Restrictions.eq(CopilotFile.PROPERTY_OPENAIIDFILE, questionAttachedFileId))
          .setMaxResults(1)
          .uniqueResult();
      if (copilotFile == null) {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("ETCOP_FileNotFound"), questionAttachedFileId));
      }
      logIfDebug(String.format("questionAttachedFileId: %s", questionAttachedFileId));
      jsonRequestForCopilot.put("file_ids", new JSONArray().put(questionAttachedFileId));
    }
    for (CopilotAppSource appSource : copilotApp.getETCOPAppSourceList()) {
      if (BooleanUtils.isTrue(appSource.isAppend()) && appSource.getFile() != null) {
        appendFileSourcesToSystemPrompt(appSource, prompt);
      }
    }
    if (!StringUtils.isEmpty(prompt.toString())) {
      jsonRequestForCopilot.put(PROP_SYSTEM_PROMPT, prompt.toString());
    }
  }

  private HttpResponse<String> sendCopilotRequest(String bodyReq)
      throws URISyntaxException, IOException, InterruptedException {
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
    String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
    HttpClient client = HttpClient.newBuilder().build();
    HttpRequest copilotRequest = HttpRequest.newBuilder()
        .uri(new URI(String.format("http://%s:%s/question", copilotHost, copilotPort)))
        .headers("Content-Type", APPLICATION_JSON_CHARSET_UTF_8)
        .version(HttpClient.Version.HTTP_1_1)
        .POST(HttpRequest.BodyPublishers.ofString(bodyReq))
        .build();
    return client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
  }

  private void handleResponseFromCopilot(HttpServletResponse response,
      HttpResponse<String> responseFromCopilot, String appId) throws IOException, JSONException {
    JSONObject responseJsonFromCopilot = new JSONObject(responseFromCopilot.body());
    JSONObject responseOriginal = new JSONObject();
    responseOriginal.put(APP_ID, appId);
    JSONObject answer = (JSONObject) responseJsonFromCopilot.get("answer");
    if (answer.has(ERROR)) {
      handleErrorResponse(response, answer);
      return;
    }
    String conversationId = answer.optString(PROP_CONVERSATION_ID);
    if (StringUtils.isNotEmpty(conversationId)) {
      responseOriginal.put(PROP_CONVERSATION_ID, conversationId);
    }
    responseOriginal.put(PROP_RESPONSE, answer.get(PROP_RESPONSE));
    Date date = new Date();
    Timestamp tms = new Timestamp(date.getTime());
    responseOriginal.put("timestamp", tms.toString());
    response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
    response.getWriter().write(responseOriginal.toString());
  }

  private void handleErrorResponse(HttpServletResponse response, JSONObject answer)
      throws IOException, JSONException {
    JSONObject errorJson = answer.getJSONObject(ERROR);
    if (errorJson.has("code")) {
      response.setStatus(errorJson.getInt("code"));
    } else {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    response.getWriter()
        .write(new JSONObject().put(ERROR, errorJson.getString("message")).toString());
  }

  private static void appendFileSourcesToSystemPrompt(CopilotAppSource appSource,
      StringBuilder prompt) {
    try {
      File tempFile = OpenAIUtils.getFileFromCopilotFile(appSource.getFile());
      String content = Files.readString(tempFile.toPath());
      prompt.append(content).append("\n");
    } catch (IOException e) {
      throw new OBException(e);
    }
  }

  private void addExtraContextWithHooks(CopilotApp copilotApp, JSONObject jsonRequest) {
    OBContext context = OBContext.getOBContext();
    JSONObject jsonExtraInfo = new JSONObject();
    Role role = context.getRole();
    OBDal.getInstance().refresh(role);
    if (role.isWebServiceEnabled()) {
      try {
        //Refresh to avoid LazyInitializationException
        User user = context.getUser();
        OBDal.getInstance().refresh(user);
        Organization currentOrganization = context.getCurrentOrganization();
        OBDal.getInstance().refresh(currentOrganization);
        Warehouse warehouse = context.getWarehouse();
        OBDal.getInstance().refresh(warehouse);
        jsonExtraInfo.put("auth", new JSONObject().put("ETENDO_TOKEN",
            SecureWebServicesUtils.generateToken(user, role, currentOrganization, warehouse)));
        jsonRequest.put("extra_info", jsonExtraInfo);
      } catch (Exception e) {
        log4j.error("Error adding auth token to extraInfo", e);
      }

    }

    //execute the hooks
    try {
      WeldUtils.getInstanceFromStaticBeanManager(CopilotQuestionHookManager.class)
          .executeHooks(copilotApp, jsonRequest);
    } catch (OBException e) {
      log4j.error("Error executing hooks", e);
    }

  }

  private void saveFileTemp(File f, String fileId) {
    CopilotFile fileCop = OBProvider.getInstance().get(CopilotFile.class);
    fileCop.setOpenaiIdFile(fileId);
    OBContext context = OBContext.getOBContext();
    fileCop.setName(f.getName());
    fileCop.setCreatedBy(context.getUser());
    fileCop.setUpdatedBy(context.getUser());
    fileCop.setCreationDate(new Date());
    fileCop.setUpdated(new Date());
    fileCop.setClient(context.getCurrentClient());
    fileCop.setOrganization(context.getCurrentOrganization());
    fileCop.setType("F");
    fileCop.setTemp(true);
    OBDal.getInstance().save(fileCop);
  }

  private void handleAssistants(HttpServletResponse response) {
    try {
      OBContext.setAdminMode();
      //send json of assistants
      JSONArray assistants = new JSONArray();
      List<CopilotRoleApp> appList = OBDal.getInstance()
          .createCriteria(CopilotRoleApp.class)
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
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}
