package com.etendoerp.copilot.rest;

import static com.etendoerp.copilot.util.CopilotUtils.getAppSourceContent;
import static com.etendoerp.copilot.util.TrackingUtil.getLastConversation;
import static com.etendoerp.copilot.util.TrackingUtil.trackNullResponse;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TransferQueue;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Message;
import org.openbravo.model.ad.ui.MessageTrl;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.hook.CopilotQuestionHookManager;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotModelUtils;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.ExtractedResponse;
import com.etendoerp.copilot.util.OpenAIUtils;
import com.etendoerp.copilot.util.TrackingUtil;
import com.etendoerp.copilot.util.WebhookPermissionUtils;

/**
 * Utility class providing REST service operations for Copilot integration.
 * This class handles file uploads, question processing, response extraction,
 * and communication with the Copilot backend services.
 */
public class RestServiceUtil {


  public static final Logger log = LogManager.getLogger(RestServiceUtil.class);

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
  // Constant for response/answer JSON key used across this class
  public static final String PROP_ANSWER = "answer";
  public static final String PROP_ERROR = "error";
  // JSON literal keys used in Copilot error responses
  public static final String JSON_DETAIL = "detail";
  public static final String JSON_MESSAGE = "message";
  public static final String COPILOT_MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";
  public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json;charset=UTF-8";
  public static final String FILE = "/file";
  public static final String PROP_PROVIDER = "provider";
  public static final String PROP_MODEL = "model";
  public static final String PROP_SYSTEM_PROMPT = "system_prompt";
  public static final String PROP_TOOLS = "tools";
  public static final String PROP_KB_VECTORDB_ID = "kb_vectordb_id";
  public static final String PROP_KB_SEARCH_K = "kb_search_k";
  public static final String PROP_AD_USER_ID = "ad_user_id";
  public static final String ETCOP_COPILOT_ERROR = "ETCOP_CopilotError";
  public static final String METADATA = "metadata";

  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private RestServiceUtil() {
  }

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
      String answer = processFileItem(itemDisk, endpoint);
      responseJson.put(item.getFieldName(), answer);
    }

    return responseJson;
  }

  /**
   * Process a single uploaded file item by creating a safe temporary file and
   * delegating the actual upload to the existing file-handling path.
   *
   * <p>The method normalizes a possibly-null original filename, extracts the file
   * extension (when present), ensures the prefix is valid for
   * {@link File#createTempFile}, and then writes (or moves) the uploaded data into
   * the temporary file. The resulting temporary file is validated and then
   * uploaded to the provided endpoint.</p>
   *
   * @param itemDisk
   *     the uploaded {@link DiskFileItem} to process
   * @param endpoint
   *     the Copilot endpoint to which the file will be sent (for example {@link
   *     #FILE})
   * @return the answer string returned by Copilot for the uploaded file
   * @throws Exception
   *     on IO errors, OBException or any failure during temporary file creation or upload
   */
  static String processFileItem(DiskFileItem itemDisk, String endpoint) throws Exception {
    String originalFileName = normalizeOriginalFileName(itemDisk);
    int dotIndex = originalFileName.lastIndexOf('.');
    String extension = dotIndex == -1 ? null : originalFileName.substring(dotIndex);
    String filenameWithoutExt = dotIndex == -1 ? originalFileName : originalFileName.substring(0, dotIndex);

    String prefix = filenameWithoutExt + "_";
    if (prefix.length() < 3) {
      prefix = (prefix + "___").substring(0, 3);
    }

    Path tempPath = createTempFileWithPrefix(prefix, extension);
    File f = tempPath.toFile();
    f.deleteOnExit();

    setOwnerOnlyPermissions(tempPath, f);
    writeDiskItemToFile(itemDisk, f);
    checkSizeFile(f);
    return handleFile(f, endpoint);
  }

  private static String normalizeOriginalFileName(DiskFileItem itemDisk) {
    String originalFileName = itemDisk.getName();
    if (originalFileName == null || originalFileName.isEmpty()) {
      return "default";
    }
    return originalFileName;
  }

  private static Path createTempFileWithPrefix(String prefix, String extension) throws IOException {
    if (extension == null) {
      return Files.createTempFile(prefix, null);
    }
    return Files.createTempFile(prefix, extension);
  }

  private static void setOwnerOnlyPermissions(Path tempPath, File f) {
    try {
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
      Files.setPosixFilePermissions(tempPath, perms);
    } catch (UnsupportedOperationException | IOException e) {
      try {
        boolean ok;
        ok = f.setReadable(false, false);
        if (!ok) log.debug("Failed to set non-owner readable on temp file: {}", f.getAbsolutePath());
        ok = f.setWritable(false, false);
        if (!ok) log.debug("Failed to set non-owner writable on temp file: {}", f.getAbsolutePath());
        ok = f.setReadable(true, true);
        if (!ok) log.debug("Failed to set owner-readable on temp file: {}", f.getAbsolutePath());
        ok = f.setWritable(true, true);
        if (!ok) log.debug("Failed to set owner-writable on temp file: {}", f.getAbsolutePath());
      } catch (Exception ignore) {
        log.debug("Could not set secure permissions on temp file: {}", f.getAbsolutePath());
      }
    }
  }

  private static void writeDiskItemToFile(DiskFileItem itemDisk, File f) throws Exception {
    if (itemDisk.isInMemory()) {
      itemDisk.write(f);
    } else {
      boolean successRename = itemDisk.getStoreLocation().renameTo(f);
      if (!successRename) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorSavingFile"), itemDisk.getName()));
      }
    }
  }


  /**
   * Upload the given temporary file to the configured Copilot endpoint and return
   * the Copilot response 'answer' value.
   *
   * @param f
   *     the temporary file to upload
   * @param endpoint
   *     the Copilot endpoint to use
   * @return the 'answer' value from Copilot's JSON response (may be empty)
   * @throws IOException
   *     on network IO errors
   * @throws JSONException
   *     when the response cannot be parsed as JSON
   */
  public static String handleFile(File f, String endpoint) throws IOException, JSONException {
    java.util.Properties prop = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    java.net.http.HttpResponse<String> response = CopilotUtils.getResponseFromCopilot(prop, endpoint, new JSONObject(),
        f);
    if (response == null) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ErrorSavingFile"));
    }
    String jsonResponseStr = response.body();
    logIfDebug("Response from Copilot: " + jsonResponseStr);
    return new JSONObject(jsonResponseStr).optString(PROP_ANSWER);
  }


  /**
   * Validate that the provided file does not exceed the maximum allowed size
   * (512 MB). Throws an {@link OBException} when the file is too large.
   *
   * @param f
   *     the file to validate
   */
  private static void checkSizeFile(File f) {
    //check the size of the file: must be max 512mb
    long size = f.length();
    if (size > 512 * 1024 * 1024) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_FileTooBig"), f.getName()));
    }
  }

  /**
   * Log a debug-level message when debug logging is enabled.
   *
   * @param msg
   *     the message to log
   */
  private static void logIfDebug(String msg) {
    if (log.isDebugEnabled()) {
      log.debug(msg);
    }
  }


  /**
   * Transfer the provided data item to the given {@link TransferQueue}. If the
   * queue is null the method returns immediately. The thread is re-interrupted
   * when an {@link InterruptedException} occurs.
   *
   * @param queue
   *     the transfer queue to send the data to
   * @param data
   *     the data payload to transfer
   */
  public static void sendData(TransferQueue<String> queue, String data) {
    if (queue == null) {
      return;
    }
    try {
      queue.transfer(data);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while transferring data to queue", e);
    }
  }

  /**
   * Resolve the target Copilot assistant from the provided JSON payload and
   * delegate to the overloaded {@link #handleQuestion(boolean, HttpServletResponse, CopilotApp, String, String, List)}
   * implementation that performs the actual request handling.
   *
   * @param isAsyncRequest
   *     whether the request should be processed asynchronously (SSE)
   * @param queue
   *     the {@link HttpServletResponse} used for streaming SSE events when async
   * @param jsonRequest
   *     the JSON payload containing at least the assistant id and question
   * @return the parsed response {@link JSONObject} or null for async flows
   * @throws JSONException
   *     when request parsing fails
   * @throws IOException
   *     on IO errors
   */
  public static JSONObject handleQuestion(boolean isAsyncRequest, HttpServletResponse queue,
      JSONObject jsonRequest) throws JSONException, IOException {
    String conversationId = jsonRequest.optString(PROP_CONVERSATION_ID, null);
    String appId = jsonRequest.getString(APP_ID);
    String question = jsonRequest.getString(PROP_QUESTION);
    List<String> filesReceived = getFilesReceived(jsonRequest);
    CopilotApp copilotApp = CopilotUtils.getAssistantByIDOrName(appId);
    switch (copilotApp.getAppType()) {
      case CopilotConstants.APP_TYPE_OPENAI:
        if (StringUtils.isEmpty(copilotApp.getOpenaiAssistantID())) {
          throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_OpenAIAppNotSync"), appId));
        }
        CopilotUtils.validateOpenAIKey();
        break;
      case CopilotConstants.APP_TYPE_LANGCHAIN:
        CopilotUtils.validateOpenAIKey();
        break;
      case CopilotConstants.APP_TYPE_MULTIMODEL:
        if (StringUtils.equals(CopilotModelUtils.getProvider(copilotApp), CopilotConstants.PROVIDER_OPENAI)) {
          CopilotUtils.validateOpenAIKey();
        }
        break;
      case CopilotConstants.APP_TYPE_LANGGRAPH:
        break;
      default:
        log.warn("Unsupported app type: {}", copilotApp.getAppType());
    }
    return handleQuestion(isAsyncRequest, queue, copilotApp, conversationId, question, filesReceived);
  }


  /**
   * Extracts a list of file identifiers from a JSON request.
   *
   * @param jsonRequest
   *     the JSON object containing the file identifiers.
   * @return a list of file identifiers. If the "file" field is empty or not a valid JSON array, an empty list is returned.
   */
  public static List<String> getFilesReceived(JSONObject jsonRequest) {
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
   * Processes server-sent events (SSE) coming from the Copilot backend and forwards them to the
   * front-end client via the provided {@link HttpServletResponse}.
   *
   * @param asyncRequest
   *     when true the method streams intermediate SSE 'data:' lines as they arrive to the
   *     client; when false the method collects the final response and returns it.
   * @param response
   *     the {@link HttpServletResponse} used to write SSE messages back to the client. The
   *     response is configured by this method using {@link #setEventStreamMode(HttpServletResponse)}.
   * @param inputStream
   *     the input stream received from the Copilot backend connection; this stream is read
   *     line-by-line and parsed as JSON or SSE lines depending on the request mode.
   * @return a {@link JSONObject} containing the last (or error) response when the method is invoked
   *     in non-async mode or when an error role/answer is detected; otherwise an empty
   *     {@link JSONObject} is returned.
   */
  public static JSONObject serverSideEvents(boolean asyncRequest, HttpServletResponse response,
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
        if (asyncRequest && currentLine.startsWith("data:")) {
          sendEventToFront(writerToFront, currentLine, false);
        }
        lastLine = currentLine;
      }

      if (!asyncRequest) {
        writerToFront.write(lastLine);
      }

      JSONObject jsonLastLine = null;
      if (StringUtils.isNotEmpty(lastLine)) {
        String payload = asyncRequest ? lastLine.substring(5) : lastLine;
        jsonLastLine = new JSONObject(payload);
      }
      if (isAnswerWithNullOrErrorRole(jsonLastLine)) {
        return jsonLastLine.getJSONObject(PROP_ANSWER);
      }
      return new JSONObject();
    } catch (JSONException | IOException e) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_SSEError") + ": " + e.getMessage(), e);
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (Exception e) {
          log.debug("Error closing input stream", e);
        }
      }
    }
  }

  /**
   * Checks whether the provided JSON object represents an answer with a role indicating
   * a non-successful state. Specifically, it returns true when the JSON contains an
   * 'answer' object and that object's 'role' field equals either the string "null"
   * or the configured error role constant {@link #PROP_ERROR} (case-insensitive).
   *
   * @param jsonLastLine
   *     the JSON object to inspect (may be null)
   * @return true when an 'answer.role' of "null" or PROP_ERROR is present; false otherwise
   */
  /**
   * Inspect a JSON line returned by Copilot and determine whether it contains an
   * answer object whose role indicates an error or a null response.
   *
   * @param jsonLastLine
   *     the JSON object to inspect (may be null)
   * @return {@code true} when the JSON contains an {@code answer} object and its
   *     {@code role} equals "null" or equals {@link #PROP_ERROR} (case-insensitive);
   *     {@code false} otherwise
   * @throws JSONException
   *     when the provided JSON is malformed while accessing members
   */
  public static boolean isAnswerWithNullOrErrorRole(JSONObject jsonLastLine) throws JSONException {
    if (jsonLastLine == null || !jsonLastLine.has(PROP_ANSWER)) {
      return false;
    }
    JSONObject answer = jsonLastLine.getJSONObject(PROP_ANSWER);
    if (!answer.has("role")) {
      return false;
    }
    String role = answer.optString("role");
    return StringUtils.equalsIgnoreCase(role, "null") || StringUtils.equalsIgnoreCase(role, PROP_ERROR);
  }

  /**
   * This method is used to save a file in the temp folder of the server. The file is saved with a
   * UUID as name.
   *
   * @param copilotApp
   * @param conversationId
   * @param question
   * @param questionAttachedFileIds
   * @throws IOException
   * @throws JSONException
   */
  public static JSONObject handleQuestion(boolean asyncRequest, HttpServletResponse queue, CopilotApp copilotApp,
      String conversationId, String question, List<String> questionAttachedFileIds) throws IOException, JSONException {
    if (copilotApp == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_AppNotFound")));
    }
    refreshDynamicFiles(copilotApp);

    // Build request JSON
    JSONObject jsonRequestForCopilot = buildRequestJson(copilotApp, conversationId, question, questionAttachedFileIds);

    if (StringUtils.isEmpty(conversationId) && jsonRequestForCopilot.has(PROP_CONVERSATION_ID)) {
      conversationId = jsonRequestForCopilot.getString(PROP_CONVERSATION_ID);
    }
    // Get response from Copilot
    JSONObject finalResponseAsync = sendRequestToCopilot(asyncRequest, queue, jsonRequestForCopilot, copilotApp);

    // Process and return response
    return processResponseAndTrack(finalResponseAsync, conversationId, question, copilotApp);
  }

  /**
   * Build the JSON payload that will be sent to the Copilot backend for a question.
   * The returned object contains the conversation id (if present), the user id, the
   * question (with any appended app-source content) and any local file ids.
   *
   * @param copilotApp
   *     the assistant configuration used to build the request
   * @param conversationId
   *     the conversation id to associate to the request (may be null)
   * @param question
   *     the user question text
   * @param questionAttachedFileIds
   *     optional list of file ids attached to the question
   * @return a {@link JSONObject} representing the request to send to Copilot
   * @throws IOException
   *     if reading dynamic files fails
   * @throws JSONException
   *     if building the JSON payload fails
   */
  public static JSONObject buildRequestJson(CopilotApp copilotApp, String conversationId, String question,
      List<String> questionAttachedFileIds) throws IOException, JSONException {
    JSONObject jsonRequestForCopilot = new JSONObject();
    boolean isGraph = CopilotUtils.checkIfGraphQuestion(copilotApp);
    String appType = copilotApp.getAppType();

    if (isLangchainDerivatedAssistant(appType) && StringUtils.isEmpty(conversationId)) {
      conversationId = UUID.randomUUID().toString();
    }

    generateAssistantStructure(copilotApp, conversationId, appType, isGraph, jsonRequestForCopilot);

    if (StringUtils.isNotEmpty(conversationId)) {
      jsonRequestForCopilot.put(PROP_CONVERSATION_ID, conversationId);
    }

    jsonRequestForCopilot.put(RestServiceUtil.PROP_AD_USER_ID, OBContext.getOBContext().getUser().getId());
    question += getAppSourceContent(copilotApp.getETCOPAppSourceList(), CopilotConstants.FILE_BEHAVIOUR_QUESTION);
    CopilotUtils.checkQuestionPrompt(question);
    jsonRequestForCopilot.put(PROP_QUESTION, question + appendLocalFileIds(questionAttachedFileIds));

    addAppSourceFileIds(copilotApp, questionAttachedFileIds);
    handleFileIds(questionAttachedFileIds, jsonRequestForCopilot);
    addExtraContextWithHooks(copilotApp, jsonRequestForCopilot);

    return jsonRequestForCopilot;
  }

  /**
   * Determine the correct Copilot endpoint for a given assistant and request mode.
   *
   * @param asyncRequest
   *     true when the request should be processed asynchronously (SSE), false for synchronous
   * @param copilotApp
   *     the assistant used to evaluate whether the question is a graph question
   * @return one of the constants {@link #QUESTION}, {@link #AQUESTION}, {@link #GRAPH} or {@link #AGRAPH}
   */
  public static String determineEndpoint(boolean asyncRequest, CopilotApp copilotApp) {
    boolean isGraph = CopilotUtils.checkIfGraphQuestion(copilotApp);
    if (isGraph) {
      return asyncRequest ? AGRAPH : GRAPH;
    } else {
      return asyncRequest ? AQUESTION : QUESTION;
    }
  }

  /**
   * Send the prepared JSON request to the Copilot backend and return its response.
   * This method handles both synchronous (regular HTTP POST) and asynchronous (SSE) flows.
   *
   * @param asyncRequest
   *     whether to use the asynchronous SSE endpoint
   * @param queue
   *     the HttpServletResponse used for streaming SSE events when asyncRequest is true
   * @param jsonRequestForCopilot
   *     the JSON payload to send
   * @param copilotApp
   *     the assistant configuration (used to determine endpoint)
   * @return the parsed {@link JSONObject} response from Copilot; in SSE mode this will be the
   *     result of {@link #serverSideEvents(boolean, HttpServletResponse, InputStream)}
   * @throws IOException
   *     on network / IO errors
   * @throws JSONException
   *     when parsing Copilot's response fails
   */
  public static JSONObject sendRequestToCopilot(boolean asyncRequest, HttpServletResponse queue,
      JSONObject jsonRequestForCopilot, CopilotApp copilotApp) throws IOException, JSONException {
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
    String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
    String endpoint = determineEndpoint(asyncRequest, copilotApp);

    logIfDebug("Request to Copilot:);");
    logIfDebug(new JSONObject(jsonRequestForCopilot.toString()).toString(2));

    URL url = new URL(String.format("http://%s:%s%s", copilotHost, copilotPort, endpoint));
    try {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.getOutputStream().write(jsonRequestForCopilot.toString().getBytes());

      if (asyncRequest) {
        return serverSideEvents(asyncRequest, queue, connection.getInputStream());
      } else {
        String responseFromCopilot = new BufferedReader(
            new InputStreamReader(connection.getInputStream())).lines().collect(Collectors.joining("\n"));
        return new JSONObject(responseFromCopilot);
      }
    } catch (Exception e) {
      log.error(e);
      Thread.currentThread().interrupt();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
    }
  }

  /**
   * Process the response received from Copilot, validate it, add tracking information and
   * produce the final response object to be returned to the client.
   *
   * @param finalResponseAsync
   *     the raw JSON response received from Copilot
   * @param conversationId
   *     the conversation id associated with the request (may be null)
   * @param question
   *     the original question text
   * @param copilotApp
   *     the assistant used to serve the request
   * @return a {@link JSONObject} ready to be returned to the client containing the app id,
   *     conversation id (if present), response text, metadata (if present), and timestamp
   * @throws JSONException
   *     when assembling the resulting JSON fails
   */
  public static JSONObject processResponseAndTrack(JSONObject finalResponseAsync, String conversationId,
      String question, CopilotApp copilotApp) throws JSONException {
    if (finalResponseAsync == null) {
      trackNullResponse(conversationId, question, copilotApp);
      return null;
    }

    JSONObject responseOriginal = new JSONObject();
    responseOriginal.put(APP_ID, copilotApp.getId());

    if (!finalResponseAsync.has(PROP_ANSWER)) {
      handleMissingAnswer(finalResponseAsync);
    }

    ExtractedResponse extractedResponse = extractResponse(finalResponseAsync, responseOriginal, conversationId);
    String response = extractedResponse.getResponse();
    conversationId = extractedResponse.getConversationId();
    JSONObject metadata = extractedResponse.getMetadata();

    if (StringUtils.isEmpty(response)) {
      throw new OBException(String.format(OBMessageUtils.messageBD(ETCOP_COPILOT_ERROR), "Empty response"));
    }

    addTimestampToResponse(responseOriginal);
    TrackingUtil.getInstance().trackQuestion(conversationId, question, copilotApp);
    TrackingUtil.getInstance().trackResponse(conversationId, response, copilotApp, metadata);

    return responseOriginal;
  }


  /**
   * Inspect a Copilot response for a missing {@code answer} field and throw an {@link OBException}
   * with an appropriate message when an error detail is present.
   *
   * @param finalResponseAsync
   *     the raw JSON response from Copilot
   * @throws JSONException
   *     when parsing the JSON fails
   */
  public static void handleMissingAnswer(JSONObject finalResponseAsync) throws JSONException {
    String message = "";
    if (finalResponseAsync.has(JSON_DETAIL)) {
      JSONArray detail = finalResponseAsync.getJSONArray(JSON_DETAIL);
      if (detail.length() > 0) {
        message = ((JSONObject) detail.get(0)).getString(JSON_MESSAGE);
      }
    }
    if (!message.isEmpty()) {
      throw new OBException(String.format(OBMessageUtils.messageBD(ETCOP_COPILOT_ERROR), message));
    }
  }

  /**
   * Extract the textual response, conversation id, and metadata from Copilot's response structure.
   * Supports both the nested {@code answer} object shape and the older flat {@code response}
   * format.
   *
   * @param finalResponseAsync
   *     the JSON response from Copilot
   * @param responseOriginal
   *     the object that will be populated with extracted fields
   * @param conversationId
   *     the conversation id passed by the caller (may be updated from the response)
   * @return an {@link ExtractedResponse} containing the response text, conversation id, and metadata
   * @throws JSONException
   *     when required fields are missing or parsing fails
   */
  public static ExtractedResponse extractResponse(JSONObject finalResponseAsync, JSONObject responseOriginal,
      String conversationId) throws JSONException {
    String response = null;
    JSONObject metadata = new JSONObject();

    if (finalResponseAsync.has(PROP_ANSWER)) {
      JSONObject answer = (JSONObject) finalResponseAsync.get(PROP_ANSWER);
      handleErrorMessagesIfExists(answer);
      conversationId = answer.optString(PROP_CONVERSATION_ID);
      if (StringUtils.isNotEmpty(conversationId)) {
        responseOriginal.put(PROP_CONVERSATION_ID, conversationId);
      }
      responseOriginal.put(PROP_RESPONSE, answer.get(PROP_RESPONSE));
      response = responseOriginal.getString(PROP_RESPONSE);
      metadata = answer.optJSONObject(METADATA);
    } else if (finalResponseAsync.has(PROP_RESPONSE)) {
      response = finalResponseAsync.getString(PROP_RESPONSE);
      if (finalResponseAsync.has(PROP_CONVERSATION_ID)) {
        responseOriginal.put(PROP_CONVERSATION_ID, finalResponseAsync.get(PROP_CONVERSATION_ID));
        conversationId = finalResponseAsync.optString(PROP_CONVERSATION_ID);
      }
      metadata = finalResponseAsync.optJSONObject(METADATA);
    }

    // Add metadata to the response object if it exists
    if (metadata != null && metadata.length() > 0) {
      responseOriginal.put(METADATA, metadata);
    }

    return new ExtractedResponse(response, conversationId, metadata);
  }

  /**
   * Add a timestamp field to the response JSON using the current system time.
   *
   * @param responseOriginal
   *     the response object to which the timestamp will be added
   * @throws JSONException
   *     when modifying the JSON object fails
   */
  public static void addTimestampToResponse(JSONObject responseOriginal) throws JSONException {
    Date date = new Date();
    Timestamp tms = new Timestamp(date.getTime());
    responseOriginal.put("timestamp", tms.toString());
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
  static void generateAssistantStructure(CopilotApp copilotApp, String conversationId, String appType,
      boolean isGraph, JSONObject jsonRequestForCopilot) throws JSONException, IOException {
    if (isLangchainDerivatedAssistant(appType)) {
      if (!isGraph) {
        CopilotUtils.buildLangchainRequestForCopilot(copilotApp, conversationId, jsonRequestForCopilot, appType);
      } else {
        CopilotUtils.buildLangraphRequestForCopilot(copilotApp, conversationId, jsonRequestForCopilot);
      }
    } else if (StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_OPENAI)) {
      buildOpenAIrequestForCopilot(copilotApp, jsonRequestForCopilot);
    } else {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MissingAppType"), appType));
    }
  }

  /**
   * Generates the assistant structure for the given CopilotApp.
   * <p>
   * This method is an overloaded version that does not require a conversation ID.
   * It determines the type of the assistant and builds the appropriate request structure.
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
    generateAssistantStructure(copilotApp, null, copilotApp.getAppType(), CopilotUtils.checkIfGraphQuestion(copilotApp),
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
    return StringUtils.equalsIgnoreCase(appType, CopilotConstants.APP_TYPE_LANGCHAIN) || StringUtils.equalsIgnoreCase(
        appType, CopilotConstants.APP_TYPE_LANGGRAPH) || StringUtils.equalsIgnoreCase(appType,
        CopilotConstants.APP_TYPE_MULTIMODEL);
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
    jsonRequestForCopilot.put(PROP_ASSISTANT_ID, copilotApp.getOpenaiAssistantID());
    jsonRequestForCopilot.put(PROP_NAME, copilotApp.getName());
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

  static String appendLocalFileIds(List<String> questionAttachedFileIds) throws JSONException {
    if (questionAttachedFileIds == null || questionAttachedFileIds.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (String questionAttachedFileId : questionAttachedFileIds) {
      if (StringUtils.isNotEmpty(questionAttachedFileId)) {
        CopilotFile copilotFile = (CopilotFile) OBDal.getInstance().createCriteria(CopilotFile.class).add(
            Restrictions.eq(CopilotFile.PROPERTY_OPENAIIDFILE, questionAttachedFileId)).setMaxResults(1).uniqueResult();

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
    if (answer.has(PROP_ERROR)) {
      JSONObject errorJson = answer.getJSONObject(PROP_ERROR);
      String message = errorJson.getString(JSON_MESSAGE);
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
   * Return a JSON array with the assistants available for the current role.
   * This method runs in admin mode during the query and restores the previous
   * OBContext mode before returning.
   *
   * @return a {@link JSONArray} with assistant id/name objects
   */
  public static JSONArray handleAssistants() {
    try {
      OBContext.setAdminMode();
      //send json of assistants
      JSONArray assistants = new JSONArray();
      OBContext context = OBContext.getOBContext();
      Role role = context.getRole();

      List<CopilotApp> appList = new HashSet<>(OBDal.getInstance().createCriteria(CopilotRoleApp.class).add(
          Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, role)).list()).stream().map(
          CopilotRoleApp::getCopilotApp).distinct().collect(Collectors.toList());

      appList.sort((app1, app2) -> getLastConversation(context.getUser(), app2).compareTo(
          getLastConversation(context.getUser(), app1)));

      for (CopilotApp app : appList) {
        JSONObject assistantJson = new JSONObject();
        assistantJson.put(APP_ID, app.getId());
        assistantJson.put("name", app.getName());
        assistants.put(assistantJson);
      }

      assignWebhookPermissionsSafely(appList, role);

      return assistants;
    } catch (Exception e) {
      throw new OBException(e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Assign missing webhook permissions for every assistant in {@code appList} for
   * the provided {@code role}. Exceptions for individual apps are logged but do
   * not abort the overall operation.
   *
   * @param appList
   *     the list of assistants
   * @param role
   *     the role to assign permissions to
   */
  private static void assignWebhookPermissionsSafely(List<CopilotApp> appList, Role role) {
    for (CopilotApp app : appList) {
      try {
        WebhookPermissionUtils.assignMissingPermissions(role, app);
      } catch (Exception e) {
        log.error("Error assigning webhook permissions for app '{}': {}", app.getName(), e.getMessage(), e);
      }
    }
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
    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
      String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
      JSONObject jsonRequestForCopilot = new JSONObject();
      // appType was unused after refactor; get it inline where needed
      String conversationId = UUID.randomUUID().toString();

      CopilotUtils.buildLangraphRequestForCopilot(copilotApp, conversationId, jsonRequestForCopilot);
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
      HttpRequest copilotRequest = HttpRequest.newBuilder().uri(
          new URI(String.format("http://%s:%s" + GRAPH, copilotHost, copilotPort))).headers("Content-Type",
          APPLICATION_JSON_CHARSET_UTF_8).version(HttpClient.Version.HTTP_1_1).POST(
          HttpRequest.BodyPublishers.ofString(bodyReq)).build();

      responseFromCopilot = client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
    } catch (URISyntaxException | InterruptedException e) {
      log.error(e);
      Thread.currentThread().interrupt();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
    }
    JSONObject responseJsonFromCopilot = new JSONObject(responseFromCopilot.body());
    if (!responseJsonFromCopilot.has(PROP_ANSWER)) {
      String message = "";
      if (responseJsonFromCopilot.has(JSON_DETAIL)) {
        JSONArray detail = responseJsonFromCopilot.getJSONArray(JSON_DETAIL);
        if (detail.length() > 0) {
          message = ((JSONObject) detail.get(0)).getString(JSON_MESSAGE);
        }
      }
      throw new OBException(String.format(OBMessageUtils.messageBD(ETCOP_COPILOT_ERROR), message));
    }
    JSONObject answer = (JSONObject) responseJsonFromCopilot.get(PROP_ANSWER);
    handleErrorMessagesIfExists(answer);
    return answer.getString(PROP_RESPONSE);
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
    return new JSONObject().put(PROP_ANSWER,
        new JSONObject().put(PROP_RESPONSE, e.getMessage()).put(PROP_CONVERSATION_ID,
            request.getParameter(PROP_CONVERSATION_ID)).put("role", PROP_ERROR));
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
