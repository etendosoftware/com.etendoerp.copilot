package com.etendoerp.copilot.rest;

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

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.util.OpenAIUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
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

import javax.servlet.ServletException;

public class RestServiceUtil {

  private RestServiceUtil() {
  }

  private static final Logger log = LogManager.getLogger(RestServiceUtil.class);

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

  static JSONObject getJSONLabels() {
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
            log.error(e);
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
            log.error(e);
          }
        }
        return jsonLabels;
      }

    } finally {
      OBContext.restorePreviousMode();
    }

  }

  static JSONObject handleFile(List<FileItem> items) throws Exception {
    logIfDebug(String.format("items: %d", items.size()));
    JSONObject responseJson = new JSONObject();
    //create a list of files, for delete them later when the process finish
    List<File> fileListToDelete = new ArrayList<>();
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
      fileListToDelete.add(f);
      responseJson.put(item.getFieldName(), handleFile(f, originalFileName));
    }
    OBDal.getInstance().flush();
    //delete the temp files
    for (File f : fileListToDelete) {
      try {
        logIfDebug(String.format("deleting file: %s", f.getName()));
        Files.deleteIfExists(f.toPath());
      } catch (Exception e) {
        log.error(e);
      }
    }
    return responseJson;
  }

  private static String handleFile(File f, String originalFileName) throws IOException {
    String fileUUID = UUID.randomUUID().toString();
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
    return filePath;
  }

  private static boolean isDevelopment() {
    try {
      SystemInfo.load(new DalConnectionProvider(false));
    } catch (ServletException e) {
      throw new OBException(e);
    }
    String purpose = SystemInfo.getSystemInfo().getProperty("instancePurpose");
    return StringUtils.equalsIgnoreCase("D", purpose);
  }

  private static void checkSizeFile(File f) {
    //check the size of the file: must be max 512mb
    long size = f.length();
    if (size > 512 * 1024 * 1024) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_FileTooBig"), f.getName()));
    }
  }

  private static void logIfDebug(String msg) {
    if (log.isDebugEnabled()) {
      log.debug(msg);
    }
  }

  static JSONObject handleQuestion(JSONObject jsonRequest) throws JSONException, IOException {
    String conversationId = jsonRequest.optString(PROP_CONVERSATION_ID);
    String appId = jsonRequest.getString(APP_ID);
    String question = jsonRequest.getString(PROP_QUESTION);
    String questionAttachedFileId = jsonRequest.optString("file");
    CopilotApp copilotApp = OBDal.getInstance().get(CopilotApp.class, appId);
    if (copilotApp == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound"), appId));
    }
    List<String> qId = new ArrayList<>();
    qId.add(questionAttachedFileId);
    return handleQuestion(copilotApp, conversationId, question, qId);
  }

  public static JSONObject handleQuestion(CopilotApp copilotApp, String conversationId, String question, List<String> questionAttachedFileIds) throws IOException, JSONException {
    if(copilotApp == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound")));
    }
    refreshDynamicFiles(copilotApp);
    // read the json sent
    HttpResponse<String> responseFromCopilot = null;
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String appType;
    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
      String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
      JSONObject jsonRequestForCopilot = new JSONObject();
      if (StringUtils.isNotEmpty(conversationId)) {
        jsonRequestForCopilot.put(PROP_CONVERSATION_ID, conversationId);
      }
      //the app_id is the id of the CopilotApp, must be converted to the id of the openai assistant (if it is an openai assistant)
      // and we need to add the type of the assistant (openai or langchain)
      appType = copilotApp.getAppType();
      StringBuilder prompt = new StringBuilder();
      if (StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_LANGCHAIN)) {
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
          prompt = new StringBuilder(copilotApp.getPrompt() + "\n");
        }
      } else if (StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_OPENAI)) {
        jsonRequestForCopilot.put(PROP_TYPE, CopilotConstants.APP_TYPE_OPENAI);
        jsonRequestForCopilot.put(PROP_ASSISTANT_ID, copilotApp.getOpenaiIdAssistant());
      } else {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("ETCOP_MissingAppType"), appType));
      }
      question += OpenAIUtils.getAppSourceContent(copilotApp, CopilotConstants.FILE_BEHAVIOUR_QUESTION);
      jsonRequestForCopilot.put(PROP_QUESTION, question);
      addAppSourceFileIds(copilotApp, questionAttachedFileIds);
      //handleFileIds(questionAttachedFileIds, jsonRequestForCopilot);
      // Lookup in app sources for the prompt
      prompt.append(OpenAIUtils.getAppSourceContent(copilotApp, CopilotConstants.FILE_BEHAVIOUR_SYSTEM));
      if (!StringUtils.isEmpty(prompt.toString())) {
        jsonRequestForCopilot.put(PROP_SYSTEM_PROMPT, prompt.toString());
      }
      addExtraContextWithHooks(copilotApp, jsonRequestForCopilot);
      String bodyReq = jsonRequestForCopilot.toString();
      HttpRequest copilotRequest = HttpRequest.newBuilder()
          .uri(new URI(String.format("http://%s:%s/question", copilotHost, copilotPort)))
          .headers("Content-Type", APPLICATION_JSON_CHARSET_UTF_8)
          .version(HttpClient.Version.HTTP_1_1)
          .POST(HttpRequest.BodyPublishers.ofString(bodyReq))
          .build();

      responseFromCopilot = client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
    } catch (URISyntaxException | InterruptedException e) {
      log.error(e);
      Thread.currentThread().interrupt();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
    }
    JSONObject responseJsonFromCopilot = new JSONObject(responseFromCopilot.body());
    JSONObject responseOriginal = new JSONObject();
    responseOriginal.put(APP_ID, copilotApp.getId());
    JSONObject answer = (JSONObject) responseJsonFromCopilot.get("answer");
    handleErrorMessagesIfExists(answer);
    conversationId = answer.optString(PROP_CONVERSATION_ID);
    if (StringUtils.isNotEmpty(conversationId)) {
      responseOriginal.put(PROP_CONVERSATION_ID, conversationId);
    }
    responseOriginal.put(PROP_RESPONSE, answer.get(PROP_RESPONSE));
    Date date = new Date();
    //getting the object of the Timestamp class
    Timestamp tms = new Timestamp(date.getTime());
    responseOriginal.put("timestamp", tms.toString());
    return responseOriginal;
  }

  private static void addAppSourceFileIds(CopilotApp copilotApp,
      List<String> questionAttachedFileIds) {
    for (CopilotAppSource source : copilotApp.getETCOPAppSourceList()) {
      if(CopilotConstants.isAttachBehaviour(source)) {
        questionAttachedFileIds.add(source.getFile().getOpenaiIdFile());
      }
    }
  }

  private static void refreshDynamicFiles(CopilotApp copilotApp) throws JSONException, IOException {
    String openaiApiKey = OpenAIUtils.getOpenaiApiKey();
    for (CopilotAppSource appSource : copilotApp.getETCOPAppSourceList()) {
      if (CopilotConstants.isAttachBehaviour(appSource) || CopilotConstants.isQuestionBehaviour(appSource)) {
        OpenAIUtils.syncAppSource(appSource, openaiApiKey);
      }
    }
  }

  private static void handleFileIds(List<String> questionAttachedFileIds,
      JSONObject jsonRequestForCopilot) throws JSONException {
    if (questionAttachedFileIds != null && !questionAttachedFileIds.isEmpty()) {
      JSONArray filesIds = new JSONArray();
      for (String questionAttachedFileId : questionAttachedFileIds) {
        if(StringUtils.isNotEmpty(questionAttachedFileId)) {
          //check if the file exists in the temp folder
          CopilotFile copilotFile = (CopilotFile) OBDal.getInstance()
              .createCriteria(CopilotFile.class)
              .add(Restrictions.eq(CopilotFile.PROPERTY_OPENAIIDFILE, questionAttachedFileId))
              .setMaxResults(1)
              .uniqueResult();
          if (copilotFile == null) {
            throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_FileNotFound"),
                questionAttachedFileId));
          }
          logIfDebug(String.format("questionAttachedFileId: %s", questionAttachedFileId));
          filesIds.put(questionAttachedFileId);
        }
      }
      // send the files to OpenAI and  replace the "file names" with the file_ids returned by OpenAI
      jsonRequestForCopilot.put("file_ids", filesIds);
    }
  }

  private static void handleErrorMessagesIfExists(JSONObject answer) throws JSONException {
    if (answer.has("error")) {
      JSONObject errorJson = answer.getJSONObject("error");
      String message = errorJson.getString("message");
      if (errorJson.has("code")) {
        throw new CopilotRestServiceException(message, errorJson.getInt("code"));
      } else {
        throw new CopilotRestServiceException(message);
      }
    }
  }

  private static void addExtraContextWithHooks(CopilotApp copilotApp, JSONObject jsonRequest) {
    OBContext context = OBContext.getOBContext();
    JSONObject jsonExtraInfo = new JSONObject();
    Role role = context.getRole();
    OBDal.getInstance().refresh(role);
    if (role.isWebServiceEnabled().booleanValue()) {
      try {
        //Refresh to avoid LazyInitializationException
        User user = context.getUser();
        OBDal.getInstance().refresh(user);
        Organization currentOrganization = context.getCurrentOrganization();
        OBDal.getInstance().refresh(currentOrganization);
        Warehouse warehouse = context.getWarehouse();
        OBDal.getInstance().refresh(warehouse);
        jsonExtraInfo.put("auth", new JSONObject().put("ETENDO_TOKEN",
            SecureWebServicesUtils.generateToken(user, role, currentOrganization,
                warehouse)));
        jsonRequest.put("extra_info", jsonExtraInfo);
      } catch (Exception e) {
        log.error("Error adding auth token to extraInfo", e);
      }
    }

    //execute the hooks
    try {
      WeldUtils.getInstanceFromStaticBeanManager(CopilotQuestionHookManager.class).executeHooks(copilotApp,
          jsonRequest);
    } catch (OBException e) {
      log.error("Error executing hooks", e);
    }

  }

  private static void saveFileTemp(File f, String fileId) {
    CopilotFile fileCop = OBProvider.getInstance().get(CopilotFile.class);
    fileCop.setOpenaiIdFile(fileId);
    fileCop.setName(f.getName());
    fileCop.setType("F");
    fileCop.setTemp(true);
    OBDal.getInstance().save(fileCop);
  }

  static JSONArray handleAssistants() {
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
      return assistants;
    } catch (Exception e) {
      throw new OBException(e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

}
