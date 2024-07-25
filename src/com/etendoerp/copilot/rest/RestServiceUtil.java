package com.etendoerp.copilot.rest;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.TransferQueue;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.ToolsUtil;
import com.etendoerp.copilot.util.TrackingUtil;
import com.etendoerp.copilot.util.OpenAIUtils;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
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
import javax.servlet.http.HttpServletResponse;

public class RestServiceUtil {


  private RestServiceUtil() {
  }

  private static final Logger log = LogManager.getLogger(RestServiceUtil.class);

  public static final String QUESTION = "/question";
  public static final String GRAPH = "/graph";
  public static final String AQUESTION = "/aquestion";
  public static final String AGRAPH = "/agraph";
  public static final String GET_ASSISTANTS = "/assistants";
  public static final String APP_ID = "app_id";
  public static final String PROP_ASSISTANT_ID = "assistant_id";
  public static final String PROP_RESPONSE = "response";
  public static final String PROP_TEMPERATURE = "temperature";
  public static final String PROP_DESCRIPTION = "description";
  public static final String PROP_CONVERSATION_ID = "conversation_id";
  public static final String PROP_QUESTION = "question";
  public static final String PROP_TYPE = "type";
  public static final String PROP_HISTORY = "history";
  public static final String COPILOT_MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";
  public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json;charset=UTF-8";
  public static final String FILE = "/file";
  public static final String PROP_PROVIDER = "provider";
  public static final String PROP_MODEL = "model";
  public static final String PROP_SYSTEM_PROMPT = "system_prompt";
  private static final String PROP_TOOLS = "tools";

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
    String modulePath = sourcePath + "/modules";
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

  public static void sendMsg(TransferQueue<String> queue, String role, String msg)
      throws JSONException {
    JSONObject data = new JSONObject();
    data.put("message_id", "");
    data.put("assistant_id", "");
    data.put("response", msg);
    data.put("conversation_id", "");
    data.put("role", role);
    sendData(queue, data.toString());
  }

  public static void sendData(TransferQueue<String> queue, String data) {
    if (queue == null) {
      return;
    }
    try {
      queue.transfer(data);
    } catch (InterruptedException e) {
      log.error(e);
    }
  }

  static JSONObject handleQuestion(HttpServletResponse queue,
      JSONObject jsonRequest) throws JSONException, IOException {
    String conversationId = jsonRequest.optString(PROP_CONVERSATION_ID);
    String appId = jsonRequest.getString(APP_ID);
    String question = jsonRequest.getString(PROP_QUESTION);
    String questionAttachedFileId = jsonRequest.optString("file");
    CopilotApp copilotApp = OBDal.getInstance().get(CopilotApp.class, appId);
    if (copilotApp == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound"), appId));
    }
    if (StringUtils.equalsIgnoreCase(copilotApp.getAppType(), CopilotConstants.APP_TYPE_OPENAI)
        && StringUtils.isEmpty(copilotApp.getOpenaiIdAssistant())) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_OpenAIAppNotSync"), appId));
    }

    List<String> filesReceived = new ArrayList<>();
    filesReceived.add(questionAttachedFileId); // File path in temp folder. This files were attached in the pop-up.
    return handleQuestion(queue, copilotApp, conversationId, question, filesReceived);
  }

  private static JSONObject serverSideEvents(HttpServletResponse response, InputStream inputStream) {
    response.setContentType("text/event-stream");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Connection", "keep-alive");
    String lastLine = "";
    try (PrintWriter writer = response.getWriter();
         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

      writer.println("data: {}\n\n");
      writer.flush();
      String currentLine;
      while ((currentLine = reader.readLine()) != null) {
        if (currentLine.startsWith("data:")) {
          writer.println(currentLine + "\n\n");
          writer.flush();
          lastLine = currentLine;
        }
      }
      var jsonLastLine = StringUtils.isNotEmpty(lastLine) ? new JSONObject(lastLine.substring(5)) : null;
      if (jsonLastLine != null
          && jsonLastLine.has("answer")
          && jsonLastLine.getJSONObject("answer").has("role")
          && StringUtils.equalsIgnoreCase(jsonLastLine.getJSONObject("answer").optString("role"), "null")) {
        return jsonLastLine.getJSONObject("answer");
      }
      return new JSONObject();
    } catch (JSONException | IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        inputStream.close();
      } catch (Exception e) {
      }
    }
  }

  public static JSONObject handleQuestion(HttpServletResponse queue, CopilotApp copilotApp, String conversationId,
      String question,
      List<String> questionAttachedFileIds) throws IOException, JSONException {
    if (copilotApp == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound")));
    }
    refreshDynamicFiles(copilotApp);
    // read the json sent
    String responseFromCopilot = null;
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String appType;
    JSONObject finalResponseAsync; // For save the response in case of async
    try {
      String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
      String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
      JSONObject jsonRequestForCopilot = new JSONObject();
      boolean isGraph = checkIfGraphQuestion(copilotApp);
      //the app_id is the id of the CopilotApp, must be converted to the id of the openai assistant (if it is an openai assistant)
      // and we need to add the type of the assistant (openai or langchain)
      appType = copilotApp.getAppType();
      List<String> allowedAppTypes = List.of(CopilotConstants.APP_TYPE_LANGCHAIN, CopilotConstants.APP_TYPE_LANGGRAPH,
          CopilotConstants.APP_TYPE_OPENAI);

      if (StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_LANGCHAIN)
          || StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_LANGGRAPH)
      ) {
        if (StringUtils.isEmpty(conversationId)) {
          conversationId = UUID.randomUUID().toString();
        }
        if (!isGraph) {
          buildLangchainRequestForCopilot(copilotApp, conversationId, jsonRequestForCopilot);
        } else {
          buildLangraphRequestForCopilot(copilotApp, conversationId, jsonRequestForCopilot);
        }
      } else if (StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_OPENAI)) {
        buildOpenAIrequestForCopilot(copilotApp, jsonRequestForCopilot);
      } else {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("ETCOP_MissingAppType"), appType));
      }
      if (StringUtils.isNotEmpty(conversationId)) {
        jsonRequestForCopilot.put(PROP_CONVERSATION_ID, conversationId);
      }
      question += OpenAIUtils.getAppSourceContent(copilotApp, CopilotConstants.FILE_BEHAVIOUR_QUESTION);
      jsonRequestForCopilot.put(PROP_QUESTION, question);
      addAppSourceFileIds(copilotApp, questionAttachedFileIds);
      handleFileIds(questionAttachedFileIds, jsonRequestForCopilot);
      addExtraContextWithHooks(copilotApp, jsonRequestForCopilot);
      String bodyReq = jsonRequestForCopilot.toString();
      String endpoint = isGraph ? AGRAPH : AQUESTION;
      logIfDebug("Request to Copilot:);");
      logIfDebug(new JSONObject(bodyReq).toString(2));
      URL url = new URL(String.format("http://%s:%s" + endpoint, copilotHost, copilotPort));
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.getOutputStream().write(jsonRequestForCopilot.toString().getBytes());
      finalResponseAsync = serverSideEvents(queue, connection.getInputStream());
    } catch (Exception e) {
      log.error(e);
      Thread.currentThread().interrupt();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
    }
    if (responseFromCopilot == null) {
      TrackingUtil.getInstance().trackQuestion(finalResponseAsync.optString(PROP_CONVERSATION_ID), question,
          copilotApp);
      TrackingUtil.getInstance().trackResponse(finalResponseAsync.optString(PROP_CONVERSATION_ID),
          finalResponseAsync.optString(PROP_RESPONSE), copilotApp);
      return null;
    }
    JSONObject responseJsonFromCopilot = new JSONObject(responseFromCopilot);
    JSONObject responseOriginal = new JSONObject();
    responseOriginal.put(APP_ID, copilotApp.getId());
    if (!responseJsonFromCopilot.has("answer")) {
      String message = "";
      if (responseJsonFromCopilot.has("detail")) {
        JSONArray detail = responseJsonFromCopilot.getJSONArray("detail");
        if (detail.length() > 0) {
          message = ((JSONObject) detail.get(0)).getString("message");
        }
      }
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_CopilotError"), message));
    }
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
    TrackingUtil.getInstance().trackQuestion(conversationId, question, copilotApp);
    TrackingUtil.getInstance().trackResponse(conversationId, responseOriginal.getString(PROP_RESPONSE), copilotApp);
    return responseOriginal;
  }

  /**
   * This method is used to build a request for the Langraph assistant.
   * It first initializes a HashMap to store the stages and their associated assistants.
   * Then, it calls the loadStagesAssistants method to load the assistants for each stage into the HashMap.
   * Finally, it calls the setStages method to set the stages for the request using the data in the HashMap.
   *
   * @param copilotApp
   *     The CopilotApp instance for which the request is to be built.
   * @param conversationId
   *     The conversation ID to be used in the request.
   * @param jsonRequestForCopilot
   *     The JSONObject to which the request parameters are to be added.
   */
  private static void buildLangraphRequestForCopilot(CopilotApp copilotApp, String conversationId,
      JSONObject jsonRequestForCopilot) throws JSONException {
    HashMap<String, ArrayList<String>> stagesAssistants = new HashMap<>();
    loadStagesAssistants(copilotApp, jsonRequestForCopilot, conversationId, stagesAssistants);
    setStages(jsonRequestForCopilot, stagesAssistants);
  }

  /**
   * This method is used to load the stages and their associated assistants for a given CopilotApp instance.
   * It iterates over the team members of the CopilotApp instance and creates a JSON object for each one.
   * Each team member JSON object contains the name of the team member and the type of the assistant.
   * The team member JSON objects are added to a JSON array, which is then added to the request JSON object under the key "assistants".
   *
   * @param copilotApp
   *     The CopilotApp instance for which the stages and their associated assistants are to be loaded.
   * @param jsonRequestForCopilot
   *     The JSONObject to which the stages and their associated assistants are to be added.
   * @param stagesAssistants
   *     A HashMap mapping stage names to a list of assistant names for each stage.
   */
  private static void loadStagesAssistants(CopilotApp copilotApp, JSONObject jsonRequestForCopilot,
      String conversationId,
      HashMap<String, ArrayList<String>> stagesAssistants) throws JSONException {
    ArrayList<String> teamMembersIdentifier = new ArrayList<>();
    JSONArray assistantsArray = new JSONArray();
    for (CopilotApp teamMember : getTeamMembers(copilotApp)) {
      JSONObject memberData = new JSONObject();
      try {
        //the name is the identifier of the team member, but without any character that is not a letter or a number
        String name = teamMember.getName().replaceAll("[^a-zA-Z0-9]", "");
        memberData.put("name", name);
        teamMembersIdentifier.add(name);
        memberData.put("type", teamMember.getAppType());
        if (StringUtils.equalsIgnoreCase(teamMember.getAppType(), CopilotConstants.APP_TYPE_OPENAI)) {
          memberData.put(PROP_ASSISTANT_ID, teamMember.getOpenaiIdAssistant());
        } else if (StringUtils.equalsIgnoreCase(teamMember.getAppType(), CopilotConstants.APP_TYPE_LANGCHAIN)) {
          buildLangchainRequestForCopilot(teamMember, null, memberData);
        }
        assistantsArray.put(memberData);

      } catch (JSONException e) {
        log.error(e);
      }
    }
    stagesAssistants.put("stage1", teamMembersIdentifier);
    jsonRequestForCopilot.put("assistants", assistantsArray);
    if (StringUtils.isNotEmpty(conversationId)) {
      jsonRequestForCopilot.put(PROP_HISTORY, TrackingUtil.getHistory(conversationId));
    }
    //prompt of the graph supervisor
    if (StringUtils.isNotEmpty(copilotApp.getPrompt())) {
      jsonRequestForCopilot.put(PROP_SYSTEM_PROMPT, copilotApp.getPrompt());
    }
    //temperature of the graph supervisor
    jsonRequestForCopilot.put(PROP_TEMPERATURE, copilotApp.getTemperature());
  }


  /**
   * This method is used to set the stages for a given request to the Langchain assistant.
   * It iterates over the stages and their associated assistants, creating a JSON object for each stage.
   * Each stage JSON object contains the name of the stage and a JSON array of the assistants for that stage.
   * The stage JSON objects are added to a JSON array, which is then added to a new JSON object under the key "stages".
   * This new JSON object is then added to the request JSON object under the key "graph".
   *
   * @param jsonRequestForCopilot
   *     The JSONObject to which the stages are to be added.
   * @param stagesAssistants
   *     A HashMap mapping stage names to a list of assistant names for each stage.
   */
  private static void setStages(JSONObject jsonRequestForCopilot, HashMap<String, ArrayList<String>> stagesAssistants) {
    try {
      JSONArray stages = new JSONArray();
      for (String stage : stagesAssistants.keySet()) {
        JSONObject stageJson = new JSONObject();
        stageJson.put("name", stage);
        JSONArray assistants = new JSONArray();
        for (String assistantsList : stagesAssistants.get(stage)) {
          assistants.put(assistantsList);
        }
        stageJson.put("assistants", assistants);
        stages.put(stageJson);
      }
      JSONObject graph = new JSONObject();
      graph.put("stages", stages);
      jsonRequestForCopilot.put("graph", graph);
    } catch (JSONException e) {
      log.error(e);
    }
  }

  /**
   * This method is used to build a request for the Langchain assistant.
   * It sets the assistant ID to the ID of the CopilotApp instance and the type of the assistant to "Langchain".
   * If a conversation ID is provided, it adds the conversation history to the request.
   * It also adds the toolset of the CopilotApp instance to the request.
   * Depending on the provider of the CopilotApp instance, it sets the provider and model in the request.
   * If the provider is "OPENAI", it sets the provider to "openai" and the model to the name of the model of the CopilotApp instance.
   * If the provider is "GEMINI", it sets the provider to "gemini" and the model to "gemini-1.5-pro-latest".
   * If the provider is neither "OPENAI" nor "GEMINI", it throws an exception.
   * If the CopilotApp instance has a prompt, it adds the prompt and the content of the app source file with the behaviour "system" to the request.
   *
   * @param copilotApp
   *     The CopilotApp instance for which the request is to be built.
   * @param conversationId
   *     The conversation ID to be used in the request. If it is not empty, the conversation history will be added to the request.
   * @param jsonRequestForCopilot
   *     The JSONObject to which the request parameters are to be added.
   * @throws JSONException
   *     If an error occurs while processing the JSON data.
   */

  private static void buildLangchainRequestForCopilot(CopilotApp copilotApp, String conversationId,
      JSONObject jsonRequestForCopilot) throws JSONException {
    StringBuilder prompt = new StringBuilder();
    prompt.append(copilotApp.getPrompt());
    jsonRequestForCopilot.put(PROP_ASSISTANT_ID, copilotApp.getId());
    jsonRequestForCopilot.put(PROP_TYPE, CopilotConstants.APP_TYPE_LANGCHAIN);
    if (StringUtils.isNotEmpty(conversationId)) {
      jsonRequestForCopilot.put(PROP_HISTORY, TrackingUtil.getHistory(conversationId));
    }
    jsonRequestForCopilot.put(PROP_TEMPERATURE, copilotApp.getTemperature());
    jsonRequestForCopilot.put(PROP_TOOLS, ToolsUtil.getToolSet(copilotApp));
    jsonRequestForCopilot.put(PROP_PROVIDER, CopilotUtils.getProvider(copilotApp));
    jsonRequestForCopilot.put(PROP_MODEL, CopilotUtils.getAppModel(copilotApp));

    if (!StringUtils.isEmpty(copilotApp.getPrompt())) {
      prompt = new StringBuilder(copilotApp.getPrompt() + "\n");
      // Lookup in app sources for the prompt
      prompt.append(OpenAIUtils.getAppSourceContent(copilotApp, CopilotConstants.FILE_BEHAVIOUR_SYSTEM));
      if (!StringUtils.isEmpty(prompt.toString())) {
        jsonRequestForCopilot.put(PROP_SYSTEM_PROMPT, prompt.toString());
      }
      if (StringUtils.isNotEmpty(copilotApp.getDescription())) {
        jsonRequestForCopilot.put(PROP_DESCRIPTION, copilotApp.getOpenaiIdAssistant());
      }
    }
  }

  /**
   * This method is used to build a request for the OpenAI assistant.
   * It sets the type of the assistant to "OpenAI" and the assistant ID to the OpenAI ID of the CopilotApp instance.
   *
   * @param copilotApp
   *     The CopilotApp instance for which the request is to be built.
   * @param jsonRequestForCopilot
   *     The JSONObject to which the request parameters are to be added.
   * @throws JSONException
   *     If an error occurs while processing the JSON data.
   */
  private static void buildOpenAIrequestForCopilot(CopilotApp copilotApp,
      JSONObject jsonRequestForCopilot) throws JSONException {
    jsonRequestForCopilot.put(PROP_TYPE, CopilotConstants.APP_TYPE_OPENAI);
    jsonRequestForCopilot.put(PROP_ASSISTANT_ID, copilotApp.getOpenaiIdAssistant());
  }

  /**
   * This method checks if the given CopilotApp instance is of type "LANGCHAIN" and has associated team members.
   * It returns true if both conditions are met, otherwise it returns false.
   *
   * @param copilotApp
   *     The CopilotApp instance to be checked.
   * @return A boolean value indicating whether the CopilotApp instance is of type "LANGCHAIN" and has associated team members.
   */
  private static boolean checkIfGraphQuestion(CopilotApp copilotApp) {
    return StringUtils.equalsIgnoreCase(copilotApp.getAppType(), CopilotConstants.APP_TYPE_LANGGRAPH);

  }

  /**
   * This method retrieves all the team members associated with a given CopilotApp instance.
   * It uses Java 8 streams to map each TeamMember instance to its associated CopilotApp instance and collects the results into a list.
   *
   * @param copilotApp
   *     The CopilotApp instance for which the team members are to be retrieved.
   * @return A list of CopilotApp instances representing the team members of the given CopilotApp instance.
   */
  private static List<CopilotApp> getTeamMembers(CopilotApp copilotApp) {
    return copilotApp.getETCOPTeamMemberList().stream().map(TeamMember::getMember).collect(
        java.util.stream.Collectors.toList());
  }

  private static void addAppSourceFileIds(CopilotApp copilotApp, List<String> questionAttachedFileIds) {
    for (CopilotAppSource source : copilotApp.getETCOPAppSourceList()) {
      if (CopilotConstants.isAttachBehaviour(source)) {
        questionAttachedFileIds.add(source.getOpenaiIdFile());
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
        if (StringUtils.isNotEmpty(questionAttachedFileId)) {
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
      jsonRequestForCopilot.put("local_file_ids", filesIds);
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
    Role role = OBDal.getInstance().get(Role.class, context.getRole().getId());
    if (role.isWebServiceEnabled().booleanValue()) {
      try {
        //Refresh to avoid LazyInitializationException
        User user = OBDal.getInstance().get(User.class, context.getUser().getId());
        Organization currentOrganization = OBDal.getInstance().get(Organization.class,
            context.getCurrentOrganization().getId());
        Warehouse warehouse = OBDal.getInstance().get(Warehouse.class, context.getWarehouse().getId());
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
    fileCop.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
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
      OBContext context = OBContext.getOBContext();
      List<CopilotApp> appList = new HashSet<>(OBDal.getInstance()
          .createCriteria(CopilotRoleApp.class)
          .add(Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, context.getRole()))
          .list()).stream().map(CopilotRoleApp::getCopilotApp)
          .collect(Collectors.toList());
      appList.sort((app1, app2) -> getLastConversation(context.getUser(), app2)
          .compareTo(getLastConversation(context.getUser(), app1)));
      for (CopilotApp app : appList) {
        JSONObject assistantJson = new JSONObject();
        assistantJson.put(APP_ID, app.getId());
        assistantJson.put("name", app.getName());
        assistants.put(assistantJson);
      }
      return assistants;
    } catch (Exception e) {
      throw new OBException(e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private static Date getLastConversation(User user, CopilotApp copilotApp) {
    OBCriteria<Conversation> convCriteria = OBDal.getInstance().createCriteria(Conversation.class);
    convCriteria.add(Restrictions.eq(Conversation.PROPERTY_COPILOTAPP, copilotApp));
    convCriteria.add(Restrictions.eq(Conversation.PROPERTY_USERCONTACT, user));
    convCriteria.addOrder(Order.desc(Conversation.PROPERTY_LASTMSG));
    convCriteria.setMaxResults(1);
    Conversation conversation = (Conversation) convCriteria.uniqueResult();
    if (conversation == null) {
      return Date.from(Instant.parse("2024-01-01T00:00:00Z"));
    }
    if (conversation.getLastMsg() == null) {
      return conversation.getCreationDate();
    }
    return conversation.getLastMsg();
  }

  public static String getGraphImg(CopilotApp copilotApp) throws JSONException, IOException {
    if (copilotApp == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound")));
    }
    // read the json sent
    HttpResponse<String> responseFromCopilot = null;
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String appType;
    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
      String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
      JSONObject jsonRequestForCopilot = new JSONObject();
      //the app_id is the id of the CopilotApp, must be converted to the id of the openai assistant (if it is an openai assistant)
      // and we need to add the type of the assistant (openai or langchain)
      appType = copilotApp.getAppType();
      String conversationId = UUID.randomUUID().toString();

      buildLangraphRequestForCopilot(copilotApp, conversationId, jsonRequestForCopilot);
      jsonRequestForCopilot.put("generate_image", true);
      if (StringUtils.isNotEmpty(conversationId)) {
        jsonRequestForCopilot.put(PROP_CONVERSATION_ID, conversationId);
      }
      String question = "placeholder";
      jsonRequestForCopilot.put(PROP_QUESTION, question);
      addExtraContextWithHooks(copilotApp, jsonRequestForCopilot);
      String bodyReq = jsonRequestForCopilot.toString();
      logIfDebug("Request to Copilot:);");
      logIfDebug(new JSONObject(bodyReq).toString(2));
      HttpRequest copilotRequest = HttpRequest.newBuilder()
          .uri(new URI(String.format("http://%s:%s" + GRAPH, copilotHost, copilotPort)))
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
    if (!responseJsonFromCopilot.has("answer")) {
      String message = "";
      if (responseJsonFromCopilot.has("detail")) {
        JSONArray detail = responseJsonFromCopilot.getJSONArray("detail");
        if (detail.length() > 0) {
          message = ((JSONObject) detail.get(0)).getString("message");
        }
      }
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_CopilotError"), message));
    }
    JSONObject answer = (JSONObject) responseJsonFromCopilot.get("answer");
    handleErrorMessagesIfExists(answer);
    return answer.getString("response");
  }
}
