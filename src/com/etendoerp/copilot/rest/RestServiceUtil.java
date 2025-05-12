package com.etendoerp.copilot.rest;

import static com.etendoerp.copilot.util.CopilotConstants.LANGCHAIN_MAX_LENGTH_QUESTION;
import static com.etendoerp.copilot.util.CopilotUtils.getAppSourceContent;
import static com.etendoerp.copilot.util.CopilotUtils.getAssistantPrompt;
import static com.etendoerp.webhookevents.webhook_util.OpenAPISpecUtils.PROP_NAME;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TransferQueue;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.hook.CopilotQuestionHookManager;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotModelUtils;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.OpenAIUtils;
import com.etendoerp.copilot.util.ToolsUtil;
import com.etendoerp.copilot.util.TrackingUtil;

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
  public static final String PROP_CODE_EXECUTION = "code_execution";
  public static final String COPILOT_MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";
  public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json;charset=UTF-8";
  public static final String FILE = "/file";
  public static final String PROP_PROVIDER = "provider";
  public static final String PROP_MODEL = "model";
  public static final String PROP_SYSTEM_PROMPT = "system_prompt";
  private static final String PROP_TOOLS = "tools";
  private static final String PROP_KB_VECTORDB_ID = "kb_vectordb_id";
  public static final String PROP_KB_SEARCH_K = "kb_search_k";

  /**
   * This method is used to add extra context to the request for Copilot, based on the hooks defined
   * for the CopilotApp.
   */
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

  /**
   * Processes a list of file items and handles each file by creating a temporary file
   * and performing necessary operations based on its storage location.
   * <p>
   * This method iterates through the provided list of `FileItem` objects, checks whether
   * each item is a form field or a file, and processes the file accordingly. If the file
   * is in memory, it writes the content to a temporary file. If the file is on disk, it
   * attempts to rename it to a temporary file. The method also validates the file size
   * and adds the processed file's information to a JSON response object.
   *
   * @param items
   *     A {@link List} of {@link FileItem} objects representing the files to be processed.
   * @param endpoint
   *     A {@link String} representing the endpoint to which the file will be sent.
   * @return A {@link JSONObject} containing the processed file information.
   * @throws Exception
   *     If an error occurs during file processing or temporary file creation.
   */
  public static JSONObject handleFile(List<FileItem> items, String endpoint) throws Exception {
    logIfDebug(String.format("items: %d", items.size()));
    JSONObject responseJson = new JSONObject();
    // Create a list of files to delete them later when the process finishes
    for (FileItem item : items) {
      if (item.isFormField()) {
        continue;
      }
      DiskFileItem itemDisk = (DiskFileItem) item;
      String originalFileName = item.getName();
      String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
      String filenameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf("."));
      // Check if the file is in memory or on disk and create a temp file
      File f = File.createTempFile(filenameWithoutExt + "_", extension);
      f.deleteOnExit();
      if (itemDisk.isInMemory()) {
        // If the file is in memory, write it to the temp file
        itemDisk.write(f);
      } else {
        // If the file is on disk, copy it to the temp file
        boolean successRename = itemDisk.getStoreLocation().renameTo(f);
        if (!successRename) {
          throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorSavingFile"), item.getName()));
        }
      }
      checkSizeFile(f);
      responseJson.put(item.getFieldName(), handleFile(f, originalFileName, endpoint));
    }

    return responseJson;
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param f
   * @param originalFileName
   * @param endpoint
   * @throws IOException
   */

  public static String handleFile(File f, String originalFileName, String endpoint) throws IOException, JSONException {
    return handleFile(f, endpoint);
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param f
   * @param endpoint
   * @throws IOException
   */
  public static String handleFile(File f, String endpoint) throws IOException, JSONException {
    var prop = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    var response = CopilotUtils.getResponseFromCopilot(prop, endpoint, new JSONObject(), f);
    if (response == null) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ErrorSavingFile"));
    }
    var jsonResponseStr = response.body();
    logIfDebug("Response from Copilot: " + jsonResponseStr);
    JSONObject jsonObject = new JSONObject(jsonResponseStr);
    return jsonObject.optString("answer");
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   */
  private static boolean isDevelopment() {
    try {
      SystemInfo.load(new DalConnectionProvider(false));
    } catch (ServletException e) {
      throw new OBException(e);
    }
    String purpose = SystemInfo.getSystemInfo().getProperty("instancePurpose");
    return StringUtils.equalsIgnoreCase("D", purpose);
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param f
   */
  private static void checkSizeFile(File f) {
    //check the size of the file: must be max 512mb
    long size = f.length();
    if (size > 512 * 1024 * 1024) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_FileTooBig"), f.getName()));
    }
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param msg
   */
  private static void logIfDebug(String msg) {
    if (log.isDebugEnabled()) {
      log.debug(msg);
    }
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param queue
   * @param role
   * @param msg
   * @throws JSONException
   */
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

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param queue
   * @param data
   */
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

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param isAsyncRequest
   * @param queue
   * @param jsonRequest
   * @throws JSONException
   * @throws IOException
   */
  public static JSONObject handleQuestion(boolean isAsyncRequest, HttpServletResponse queue,
      JSONObject jsonRequest) throws JSONException, IOException {
    String conversationId = jsonRequest.optString(PROP_CONVERSATION_ID);
    String appId = jsonRequest.getString(APP_ID);
    String question = jsonRequest.getString(PROP_QUESTION);
    List<String> filesReceived = getFilesReceived(jsonRequest);
    String questionAttachedFileId = jsonRequest.optString("file");
    CopilotApp copilotApp = getAssistantByIDOrName(appId);
    switch (copilotApp.getAppType()) {
      case CopilotConstants.APP_TYPE_OPENAI:
        if (StringUtils.isEmpty(copilotApp.getOpenaiIdAssistant())) {
          throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_OpenAIAppNotSync"), appId));
        }
        validateOpenAIKey();
        break;
      case CopilotConstants.APP_TYPE_LANGCHAIN:
        validateOpenAIKey();
        break;
      case CopilotConstants.APP_TYPE_MULTIMODEL:
        break;
      case CopilotConstants.APP_TYPE_LANGGRAPH:
        break;
      default:
        log.warn("Unsupported app type: {}", copilotApp.getAppType());
    }
    return handleQuestion(isAsyncRequest, queue, copilotApp, conversationId, question, filesReceived);
  }

  /**
   * Retrieves a CopilotApp instance based on the provided ID or name.
   * <p>
   * This method first attempts to fetch the CopilotApp instance using the provided ID.
   * If no instance is found, it treats the ID as the name of the Assistant and attempts to fetch the CopilotApp instance by the Assistant's name.
   * If still no instance is found, an OBException is thrown.
   *
   * @param idOrName
   *     the ID or name of the CopilotApp to retrieve
   * @return the CopilotApp instance corresponding to the provided ID or name
   * @throws OBException
   *     if no CopilotApp instance is found for the provided ID or name
   */
  public static CopilotApp getAssistantByIDOrName(String idOrName) {
    CopilotApp copilotApp = OBDal.getInstance().get(CopilotApp.class, idOrName);
    if (copilotApp != null) {
      return copilotApp;
    }
    // This is in case the appId provided was the name of the Assistant
    copilotApp = getAppIdByAssistantName(idOrName);
    if (copilotApp != null) {
      return copilotApp;
    }
    throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound"), idOrName));
  }

  /**
   * Retrieves a CopilotApp instance based on the provided ID.
   * <p>
   * This method fetches the CopilotApp instance using the provided ID.
   * If no instance is found, an OBException is thrown.
   *
   * @param id
   *     the ID of the CopilotApp to retrieve
   * @return the CopilotApp instance corresponding to the provided ID
   * @throws OBException
   *     if no CopilotApp instance is found for the provided ID
   */
  public static CopilotApp getAssistantByID(String id) {
    CopilotApp copilotApp = OBDal.getInstance().get(CopilotApp.class, id);
    if (copilotApp == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound"), id));
    }
    return copilotApp;
  }

  /**
   * Extracts a list of file identifiers from a JSON request.
   *
   * @param jsonRequest
   *     the JSON object containing the file identifiers.
   * @return a list of file identifiers. If the "file" field is empty or not a valid JSON array, an empty list is returned.
   */
  private static List<String> getFilesReceived(JSONObject jsonRequest) {
    List<String> result = new ArrayList<>();
    String questionAttachedFileIds = jsonRequest.optString("file");
    if (StringUtils.isEmpty(questionAttachedFileIds)) {
      return result;
    }
    if (!StringUtils.startsWith(questionAttachedFileIds, "[")) {
      result.add(questionAttachedFileIds);
      return result;
    }
    try {
      JSONArray jsonArray = new JSONArray(questionAttachedFileIds);
      for (int i = 0; i < jsonArray.length(); i++) {
        result.add(jsonArray.getString(i));
      }
    } catch (JSONException e) {
      log.error(e);
    }
    return result;
  }

  /**
   * Retrieves a CopilotApp instance based on the provided Assistant name.
   * <p>
   * This method creates a criteria query to fetch the CopilotApp instance
   * where the name matches the provided Assistant name. It sets a maximum
   * result limit of one and returns the unique result.
   *
   * @param appId
   *     the name of the Assistant to retrieve the CopilotApp instance for
   * @return the CopilotApp instance corresponding to the provided Assistant name, or null if no match is found
   */
  public static CopilotApp getAppIdByAssistantName(String appId) {
    OBCriteria<CopilotApp> appCrit = OBDal.getInstance().createCriteria(CopilotApp.class);
    appCrit.add(Restrictions.eq(CopilotApp.PROPERTY_NAME, appId));
    appCrit.setMaxResults(1);
    return (CopilotApp) appCrit.uniqueResult();
  }

  private static void validateOpenAIKey() {
    try {
      String openaiApiKey = OpenAIUtils.getOpenaiApiKey();
      URL url = new URL(CopilotConstants.OPENAI_MODELS);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Authorization", "Bearer " + openaiApiKey);
      if (connection.getResponseCode() != 200) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_OpenAIKeyNotValid")));
      }
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param asyncRequest
   * @param response
   * @param inputStream
   */
  private static JSONObject serverSideEvents(boolean asyncRequest, HttpServletResponse response,
      InputStream inputStream) {
    setEventStreamMode(response);
    String lastLine = "";
    try (PrintWriter writerToFront = response.getWriter(); BufferedReader readerFromCopilot = new BufferedReader(
        new InputStreamReader(inputStream))) {
      if (asyncRequest) {
        sendEventToFront(writerToFront, "{}", true);
      }
      String currentLine;
      while ((currentLine = readerFromCopilot.readLine()) != null) {
        if (asyncRequest) {
          if (currentLine.startsWith("data:")) {
            sendEventToFront(writerToFront, currentLine, false);
          }
        }
        lastLine = currentLine;
      }

      if (!asyncRequest) {
        writerToFront.write(lastLine);
      }
      writerToFront.close();

      var jsonLastLine = StringUtils.isNotEmpty(lastLine) ? new JSONObject(
          asyncRequest ? lastLine.substring(5) : lastLine) : null;
      if (jsonLastLine != null
          && jsonLastLine.has("answer")
          && jsonLastLine.getJSONObject("answer").has("role")
          && (StringUtils.equalsIgnoreCase(jsonLastLine.getJSONObject("answer").optString("role"), "null") ||
          StringUtils.equalsIgnoreCase(jsonLastLine.getJSONObject("answer").optString("role"), "error"))) {
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

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param asyncRequest
   * @param queue
   * @param copilotApp
   * @param conversationId
   * @param question
   * @param questionAttachedFileIds
   * @throws IOException
   * @throws JSONException
   */
  public static JSONObject handleQuestion(boolean asyncRequest, HttpServletResponse queue, CopilotApp copilotApp,
      String conversationId,
      String question,
      List<String> questionAttachedFileIds) throws IOException, JSONException {
    if (copilotApp == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound")));
    }
    refreshDynamicFiles(copilotApp);
    // read the json sent
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String appType;
    JSONObject finalResponseAsync; // For save the response in case of async
    String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
    String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
    JSONObject jsonRequestForCopilot = new JSONObject();
    boolean isGraph = checkIfGraphQuestion(copilotApp);
    //the app_id is the id of the CopilotApp, must be converted to the id of the openai assistant (if it is an openai assistant)
    // and we need to add the type of the assistant (openai or langchain)
    appType = copilotApp.getAppType();
    List<String> allowedAppTypes = List.of(CopilotConstants.APP_TYPE_LANGCHAIN, CopilotConstants.APP_TYPE_LANGGRAPH,
        CopilotConstants.APP_TYPE_OPENAI, CopilotConstants.APP_TYPE_MULTIMODEL);
    if (isLangchainDerivatedAssistant(appType) && StringUtils.isEmpty(conversationId)) {
      conversationId = UUID.randomUUID().toString();
    }
    generateAssistantStructure(copilotApp, conversationId, appType, isGraph, jsonRequestForCopilot);
    if (StringUtils.isNotEmpty(conversationId)) {
      jsonRequestForCopilot.put(PROP_CONVERSATION_ID, conversationId);
    }
    question += getAppSourceContent(copilotApp.getETCOPAppSourceList(), CopilotConstants.FILE_BEHAVIOUR_QUESTION);
    checkQuestionPrompt(question);
    jsonRequestForCopilot.put(PROP_QUESTION, question);
    addAppSourceFileIds(copilotApp, questionAttachedFileIds);
    handleFileIds(questionAttachedFileIds, jsonRequestForCopilot);
    question += appendLocalFileIds(questionAttachedFileIds);
    addExtraContextWithHooks(copilotApp, jsonRequestForCopilot);
    String bodyReq = jsonRequestForCopilot.toString();
    String endpoint;
    if (isGraph) {
      if (asyncRequest) {
        endpoint = AGRAPH;
      } else {
        endpoint = GRAPH;
      }
    } else {
      if (asyncRequest) {
        endpoint = AQUESTION;
      } else {
        endpoint = QUESTION;
      }
    }
    logIfDebug("Request to Copilot:);");
    logIfDebug(new JSONObject(bodyReq).toString(2));
    URL url = new URL(String.format("http://%s:%s" + endpoint, copilotHost, copilotPort));
    try {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.getOutputStream().write(jsonRequestForCopilot.toString().getBytes());
      if (asyncRequest) {
        finalResponseAsync = serverSideEvents(asyncRequest, queue, connection.getInputStream());
      } else {
        String responseFromCopilot = new BufferedReader(new InputStreamReader(connection.getInputStream()))
            .lines().collect(Collectors.joining("\n"));
        finalResponseAsync = new JSONObject(responseFromCopilot);
      }
    } catch (Exception e) {
      log.error(e);
      Thread.currentThread().interrupt();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
    }
    if (finalResponseAsync == null) {
      TrackingUtil.getInstance().trackQuestion(finalResponseAsync.optString(PROP_CONVERSATION_ID), question,
          copilotApp);
      boolean isError = finalResponseAsync.has("role") && StringUtils.equalsIgnoreCase(
          finalResponseAsync.optString("role"), "error");
      TrackingUtil.getInstance().trackResponse(finalResponseAsync.optString(PROP_CONVERSATION_ID),
          finalResponseAsync.optString(PROP_RESPONSE), copilotApp, isError);
      return null;
    }
    JSONObject responseOriginal = new JSONObject();
    responseOriginal.put(APP_ID, copilotApp.getId());
    if (!finalResponseAsync.has("answer")) {
      String message = "";
      if (finalResponseAsync.has("detail")) {
        JSONArray detail = finalResponseAsync.getJSONArray("detail");
        if (detail.length() > 0) {
          message = ((JSONObject) detail.get(0)).getString("message");
        }
      }
      if (!message.isEmpty()) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_CopilotError"), message));
      }
    }
    String response = null;
    if (finalResponseAsync.has("answer")) {
      JSONObject answer = (JSONObject) finalResponseAsync.get("answer");
      handleErrorMessagesIfExists(answer);
      conversationId = answer.optString(PROP_CONVERSATION_ID);
      if (StringUtils.isNotEmpty(conversationId)) {
        responseOriginal.put(PROP_CONVERSATION_ID, conversationId);
      }
      responseOriginal.put(PROP_RESPONSE, answer.get(PROP_RESPONSE));
      response = responseOriginal.getString(PROP_RESPONSE);
    } else if (finalResponseAsync.has(PROP_RESPONSE)) {
      response = finalResponseAsync.getString(PROP_RESPONSE);
      if (finalResponseAsync.has(PROP_CONVERSATION_ID)) {
        responseOriginal.put(PROP_CONVERSATION_ID, finalResponseAsync.get(PROP_CONVERSATION_ID));
        conversationId = finalResponseAsync.optString(PROP_CONVERSATION_ID);
      }
    }
    if (StringUtils.isEmpty(response)) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_CopilotError"), "Empty response"));
    }
    Date date = new Date();
    Timestamp tms = new Timestamp(date.getTime());
    responseOriginal.put("timestamp", tms.toString());
    TrackingUtil.getInstance().trackQuestion(conversationId, question, copilotApp);
    TrackingUtil.getInstance().trackResponse(conversationId, response, copilotApp);
    return responseOriginal;
  }

  /**
   * Generates the assistant structure for the given CopilotApp.
   * <p>
   * This method determines the type of assistant and builds the appropriate request structure.
   * If the assistant type is derived from Langchain, it builds either a Langchain or Langraph request.
   * If the assistant type is OpenAI, it builds an OpenAI request.
   * If the assistant type is not recognized, it throws an OBException.
   *
   * @param copilotApp
   *     the CopilotApp instance for which the assistant structure is generated
   * @param conversationId
   *     the conversation ID to be used in the request
   * @param appType
   *     the type of the assistant
   * @param isGraph
   *     a boolean indicating if the assistant is a graph-based assistant
   * @param jsonRequestForCopilot
   *     the JSON object to which the request parameters are added
   * @throws JSONException
   *     if an error occurs while processing the JSON data
   * @throws IOException
   *     if an I/O error occurs
   */
  private static void generateAssistantStructure(CopilotApp copilotApp, String conversationId, String appType,
      boolean isGraph,
      JSONObject jsonRequestForCopilot) throws JSONException, IOException {
    if (isLangchainDerivatedAssistant(appType)) {
      if (!isGraph) {
        buildLangchainRequestForCopilot(copilotApp, conversationId, jsonRequestForCopilot, appType);
      } else {
        buildLangraphRequestForCopilot(copilotApp, conversationId, jsonRequestForCopilot);
      }
    } else if (StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_OPENAI)) {
      buildOpenAIrequestForCopilot(copilotApp, jsonRequestForCopilot);
    } else {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_MissingAppType"), appType));
    }
  }

  /**
   * Generates the assistant structure for the given CopilotApp.
   * <p>
   * This method is an overloaded version that does not require a conversation ID.
   * It determines the type of assistant and builds the appropriate request structure.
   *
   * @param copilotApp
   *     the CopilotApp instance for which the assistant structure is generated
   * @param jsonRequestForCopilot
   *     the JSON object to which the request parameters are added
   * @throws JSONException
   *     if an error occurs while processing the JSON data
   * @throws IOException
   *     if an I/O error occurs
   */
  public static void generateAssistantStructure(CopilotApp copilotApp,
      JSONObject jsonRequestForCopilot) throws JSONException, IOException {
    generateAssistantStructure(copilotApp, null, copilotApp.getAppType(), checkIfGraphQuestion(copilotApp),
        jsonRequestForCopilot);
  }

  /**
   * Checks if the given assistant type is derived from Langchain.
   *
   * @param appType
   *     the type of the assistant
   * @return true if the assistant type is derived from Langchain, false otherwise
   */
  private static boolean isLangchainDerivatedAssistant(String appType) {
    return StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_LANGCHAIN)
        || StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_LANGGRAPH)
        || StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_MULTIMODEL);
  }

  /**
   * Checks if the given question exceeds the maximum allowed length for Langchain questions.
   * <p>
   * If the question length exceeds the defined maximum length, an OBException is thrown.
   *
   * @param question
   *     the question to be checked
   * @throws OBException
   *     if the question length exceeds the maximum allowed length
   */
  private static void checkQuestionPrompt(String question) {
    if (question.length() > LANGCHAIN_MAX_LENGTH_QUESTION) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_MaxLengthQuestion"));
    }
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
    //add data for the supervisor
    jsonRequestForCopilot.put(PROP_TEMPERATURE, copilotApp.getTemperature());
    jsonRequestForCopilot.put(PROP_ASSISTANT_ID, copilotApp.getId());
    jsonRequestForCopilot.put(PROP_SYSTEM_PROMPT, copilotApp.getPrompt());
    jsonRequestForCopilot.put(PROP_TOOLS, ToolsUtil.getToolSet(copilotApp));
    jsonRequestForCopilot.put(PROP_NAME, copilotApp.getName());
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
      String conversationId, HashMap<String, ArrayList<String>> stagesAssistants) throws JSONException {
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
        memberData.put("description", teamMember.getDescription());

        if (StringUtils.equalsIgnoreCase(teamMember.getAppType(), CopilotConstants.APP_TYPE_OPENAI)) {
          String assistantId = teamMember.getOpenaiIdAssistant();
          if (StringUtils.isEmpty(assistantId)) {
            throw new OBException(
                String.format(OBMessageUtils.messageBD("ETCOP_ErrTeamMembNotSync"), teamMember.getName()));
          }
          memberData.put(PROP_ASSISTANT_ID, assistantId);
        } else if (StringUtils.equalsIgnoreCase(teamMember.getAppType(), CopilotConstants.APP_TYPE_LANGCHAIN)
            || StringUtils.equalsIgnoreCase(teamMember.getAppType(), CopilotConstants.APP_TYPE_MULTIMODEL)) {
          buildLangchainRequestForCopilot(teamMember, null, memberData, teamMember.getAppType());
        }

        assistantsArray.put(memberData);

      } catch (JSONException | IOException e) {
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
   * @param appType
   * @throws JSONException
   *     If an error occurs while processing the JSON data.
   */

  private static void buildLangchainRequestForCopilot(CopilotApp copilotApp, String conversationId,
      JSONObject jsonRequestForCopilot, String appType) throws JSONException, IOException {
    StringBuilder prompt = new StringBuilder();
    prompt.append(copilotApp.getPrompt());
    jsonRequestForCopilot.put(PROP_ASSISTANT_ID, copilotApp.getId());
    jsonRequestForCopilot.put(PROP_TYPE, appType);
    if (StringUtils.isNotEmpty(conversationId)) {
      jsonRequestForCopilot.put(PROP_HISTORY, TrackingUtil.getHistory(conversationId));
    }
    jsonRequestForCopilot.put(PROP_TEMPERATURE, copilotApp.getTemperature());
    jsonRequestForCopilot.put(PROP_TOOLS, ToolsUtil.getToolSet(copilotApp));
    jsonRequestForCopilot.put(PROP_PROVIDER, CopilotModelUtils.getProvider(copilotApp));
    jsonRequestForCopilot.put(PROP_MODEL, CopilotModelUtils.getAppModel(copilotApp));
    jsonRequestForCopilot.put(PROP_CODE_EXECUTION, copilotApp.isCodeInterpreter());
    jsonRequestForCopilot.put(PROP_KB_VECTORDB_ID, "KB_" + copilotApp.getId());
    jsonRequestForCopilot.put(PROP_KB_SEARCH_K,
        copilotApp.getSearchResultQty() != null ? copilotApp.getSearchResultQty().intValue() : 4);
    String promptApp = getAssistantPrompt(copilotApp);
    if (StringUtils.isNotEmpty(prompt.toString())) {
      CopilotUtils.checkPromptLength(prompt);
      jsonRequestForCopilot.put(PROP_SYSTEM_PROMPT, promptApp);
    }
    if (StringUtils.isNotEmpty(copilotApp.getDescription())) {
      jsonRequestForCopilot.put(PROP_DESCRIPTION, copilotApp.getDescription());
    }
    JSONArray appSpecs = new JSONArray();
    for (CopilotAppSource appSource : copilotApp.getETCOPAppSourceList()) {
      if (StringUtils.equals(appSource.getBehaviour(), CopilotConstants.FILE_BEHAVIOUR_SPECS)) {
        JSONObject spec = new JSONObject();
        try {
          spec.put("name", appSource.getFile().getName());
          spec.put("type", appSource.getFile().getType());
          spec.put("spec", CopilotUtils.getAppSourceContent(appSource));
          appSpecs.put(spec);
        } catch (JSONException e) {
          throw new OBException("Error while building the app specs", e);
        }
      }
    }
    jsonRequestForCopilot.put("specs", appSpecs);
  }

  public static StringBuilder replaceAliasInPrompt(StringBuilder prompt,
      List<CopilotAppSource> appSourcesWithAlias) throws IOException {
    String tempPrompt = prompt.toString();
    for (CopilotAppSource appSource : appSourcesWithAlias) {
      String aliasToReplace = "@" + appSource.getAlias() + "@";
      tempPrompt = StringUtils.replace(tempPrompt, aliasToReplace, CopilotUtils.getAppSourceContent(appSource));
    }
    return new StringBuilder(tempPrompt);
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

  /**
   * This method is used to add the file IDs of the app sources with the behaviour "attach" to the
   * list of question attached file IDs.
   *
   * @param copilotApp
   * @param questionAttachedFileIds
   */
  private static void addAppSourceFileIds(CopilotApp copilotApp, List<String> questionAttachedFileIds) {
    for (CopilotAppSource source : copilotApp.getETCOPAppSourceList()) {
      if (CopilotConstants.isAttachBehaviour(source)) {
        questionAttachedFileIds.add(source.getOpenaiIdFile());
      }
    }
  }

  /**
   * This method is used to refresh the dynamic files of the given CopilotApp instance.
   *
   * @param copilotApp
   * @throws JSONException
   * @throws IOException
   */
  private static void refreshDynamicFiles(CopilotApp copilotApp) throws JSONException, IOException {
    String openaiApiKey = OpenAIUtils.getOpenaiApiKey();
    for (CopilotAppSource appSource : copilotApp.getETCOPAppSourceList()) {
      if (CopilotConstants.isAttachBehaviour(appSource) || CopilotConstants.isQuestionBehaviour(appSource)) {
        OpenAIUtils.syncAppSource(appSource, openaiApiKey);
      }
    }
  }

  /**
   * This method is used to handle the file IDs of the question attached files.
   *
   * @param questionAttachedFileIds
   * @param jsonRequestForCopilot
   * @throws JSONException
   */
  private static void handleFileIds(List<String> questionAttachedFileIds,
      JSONObject jsonRequestForCopilot) throws JSONException {
    if (questionAttachedFileIds != null && !questionAttachedFileIds.isEmpty()) {
      JSONArray filesIds = new JSONArray();
      for (String questionAttachedFileId : questionAttachedFileIds) {
        if (StringUtils.isNotEmpty(questionAttachedFileId)) {

          logIfDebug(String.format("questionAttachedFileId: %s", questionAttachedFileId));
          filesIds.put(questionAttachedFileId);
        }
      }
      // send the files to OpenAI and  replace the "file names" with the file_ids returned by OpenAI
      jsonRequestForCopilot.put("local_file_ids", filesIds);
    }
  }

  private static String appendLocalFileIds(List<String> questionAttachedFileIds) throws JSONException {
    if (questionAttachedFileIds == null || questionAttachedFileIds.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (String questionAttachedFileId : questionAttachedFileIds) {
      if (StringUtils.isNotEmpty(questionAttachedFileId)) {
        CopilotFile copilotFile = (CopilotFile) OBDal.getInstance()
            .createCriteria(CopilotFile.class)
            .add(Restrictions.eq(CopilotFile.PROPERTY_OPENAIIDFILE, questionAttachedFileId))
            .setMaxResults(1)
            .uniqueResult();

        if (copilotFile == null || StringUtils.startsWith(questionAttachedFileId, "file")) {
          if (sb.length() == 0) {
            sb.append("\n Local files: ");
          }
          sb.append(questionAttachedFileId).append("\n");
        }
      }
    }
    return sb.length() > 0 ? sb.toString() : "";
  }

  /**
   * This method is used to handle the error messages in the response from Copilot.
   *
   * @param answer
   * @throws JSONException
   */
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

  /**
   * This method is used to add extra context to the request for Copilot, based on the hooks defined
   * for the CopilotApp.
   *
   * @param copilotApp
   * @param jsonRequest
   */
  private static void addExtraContextWithHooks(CopilotApp copilotApp, JSONObject jsonRequest) {
    OBContext context = OBContext.getOBContext();
    JSONObject jsonExtraInfo = new JSONObject();
    Role role = OBDal.getInstance().get(Role.class, context.getRole().getId());
    try {
      jsonExtraInfo.put("auth", CopilotUtils.getAuthJson(role, context));
      jsonExtraInfo.put("model_config", CopilotUtils.getModelsConfigJSON());
      jsonRequest.put("extra_info", jsonExtraInfo);
    } catch (Exception e) {
      log.error("Error adding auth token to extraInfo", e);
    }
    try {
      WeldUtils.getInstanceFromStaticBeanManager(CopilotQuestionHookManager.class).executeHooks(copilotApp,
          jsonRequest);
    } catch (OBException e) {
      log.error("Error executing hooks", e);
    }
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param f
   * @param fileId
   */
  private static void saveFileTemp(File f, String fileId) {
    CopilotFile fileCop = OBProvider.getInstance().get(CopilotFile.class);
    fileCop.setOpenaiIdFile(fileId);
    fileCop.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
    fileCop.setName(f.getName());
    fileCop.setType(CopilotConstants.KBF_TYPE_ATTACHED);
    fileCop.setTemp(true);
    OBDal.getInstance().save(fileCop);
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   */
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
          .distinct()
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

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param user
   * @param copilotApp
   */
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

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param copilotApp
   * @throws JSONException
   * @throws IOException
   */
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

  /**
   * Constructs a JSON object representing an error event in response to a request.
   * This method is specifically designed to format error messages for front-end display or logging purposes.
   * It encapsulates the error message, the conversation ID from the request, and assigns a role of 'error' to the event.
   *
   * @param request
   *     The HttpServletRequest from which the conversation ID is extracted.
   * @param e
   *     The OBException containing the error message to be included in the response.
   * @return A JSONObject structured to convey error information, including the error message, conversation ID, and a role indicator.
   * @throws JSONException
   *     If an error occurs during the creation of the JSON object.
   */
  public static JSONObject getErrorEventJSON(HttpServletRequest request, OBException e) throws JSONException {
    return new JSONObject().put("answer", new JSONObject()
        .put("response", e.getMessage())
        .put("conversation_id", request.getParameter("conversation_id"))
        .put("role", "error"));
  }

  /**
   * Overloads the {@link #sendEventToFront(PrintWriter, String, boolean)} method to allow sending JSON objects directly.
   * This method converts the provided JSON object to a string and then delegates the task of sending the event to the front-end
   * to the original {@code sendEventToFront} method. It is useful for cases where the data to be sent is already structured as a JSON object.
   *
   * @param writerToFront
   *     The {@link PrintWriter} object used to write the event data to the response stream.
   * @param json
   *     The {@link JSONObject} containing the data to be sent to the front-end.
   * @param addData
   *     A boolean flag indicating whether the "data: " prefix should be added to the event data.
   */
  public static void sendEventToFront(PrintWriter writerToFront, JSONObject json, boolean addData) {
    sendEventToFront(writerToFront, json.toString(), addData);
  }

  /**
   * Sets the response headers to configure the HttpServletResponse object for server-sent events (SSE).
   * This method prepares the response to stream data to a client in a text/event-stream format, which is
   * used for sending real-time updates to the client without requiring the client to repeatedly poll the server.
   *
   * @param response
   *     The HttpServletResponse object to be configured for SSE.
   */
  static void setEventStreamMode(HttpServletResponse response) {
    response.setContentType("text/event-stream");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Connection", "keep-alive");
  }

  /**
   * Sends an event to the front-end client. This method formats the message as a server-sent event (SSE)
   * by optionally prefixing it with "data: " and appending two newline characters. It then writes the message
   * to the PrintWriter associated with the HttpServletResponse, flushing the stream to ensure the message is sent.
   * Additionally, it logs the message to the console.
   *
   * @param writerToFront
   *     The PrintWriter to write the event data to.
   * @param x
   *     The message to be sent to the client.
   * @param addData
   *     A flag indicating whether to prefix the message with "data: " to conform to the SSE protocol.
   */
  private static void sendEventToFront(PrintWriter writerToFront, String x, boolean addData) {
    String line = (addData ? "data: " : "") + x + "\n\n";
    logIfDebug(line);
    writerToFront.println(line);
    writerToFront.flush();
  }
}
