package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.process.SyncAssistant.ERROR;
import static com.etendoerp.copilot.util.CopilotUtils.getAssistantPrompt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.hook.CopilotFileHookManager;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;

public class OpenAIUtils {
  private static final Logger log = LogManager.getLogger(OpenAIUtils.class);
  public static final String BASE_URL = "https://api.openai.com/v1";
  public static final String METHOD_DELETE = "DELETE";
  public static final String HEADER_AUTHORIZATION = "Authorization";
  public static final String HEADER_CONTENT_TYPE = "Content-Type";
  public static final String HEADER_OPEN_AI_BETA = "OpenAI-Beta";
  public static final String CONTENT_TYPE_JSON = "application/json";
  public static final String HEADER_BEARER = "Bearer ";
  public static final String HEADER_ASSISTANTS_V_2 = "assistants=v2";
  public static final String OPENAI_API_KEY = "OPENAI_API_KEY";
  public static final String ENDPOINT_FILES = "/files";
  public static final String ENDPOINT_MODELS = "/models";
  public static final String ENDPOINT_ASSISTANTS = "/assistants";
  public static final String ENDPOINT_VECTORDB = "/vector_stores";
  public static final int MILLIES_SOCKET_TIMEOUT = 5 * 60 * 1000;
  public static final String MESSAGE = "message";
  public static final String INSTRUCTIONS = "instructions";
  public static final String CODE_INT_TOO_LONG_ERR = "'tool_resources.code_interpreter.file_ids': array too long";
  private static final String APP_SOURCE_TAB_ID = "A10DD4D68A0945A3B11AA5433DFE49B6";

  private OpenAIUtils() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Synchronizes the assistant for the given CopilotApp instance.
   * <p>
   * This method checks if the assistant exists for the provided CopilotApp instance.
   * If the assistant does not exist, it creates a new one.
   *
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @param app
   *     The CopilotApp instance for which the assistant is to be synchronized.
   * @throws OBException
   *     If an error occurs during the synchronization process.
   */
  public static void syncAssistant(String openaiApiKey, CopilotApp app) throws OBException {
    try {
      upsertAssistant(app, openaiApiKey);
    } catch (JSONException | IOException e) {
      throw new OBException(e.getMessage());
    }
  }

  /**
   * Matches the parameter and code in the response JSON object.
   * <p>
   * This method checks if the response contains an error with the specified parameter and code.
   *
   * @param response
   *     The JSON object containing the response.
   * @param param
   *     The parameter to match.
   * @param code
   *     The code to match.
   * @return true if the parameter and code match, false otherwise.
   * @throws JSONException
   *     If an error occurs while parsing the JSON object.
   */
  private static boolean matchParamAndCode(JSONObject response, String param, String code)
      throws JSONException {
    if (!response.has(ERROR)) {
      return false;
    }
    JSONObject error = response.getJSONObject(ERROR);
    return error.has("param") && error.has("code") && StringUtils.equals(error.getString("param"),
        param) && StringUtils.equals(error.getString("code"), code);
  }

  /**
   * Inserts or updates the assistant for the given CopilotApp instance.
   * <p>
   * This method checks if the assistant exists for the provided CopilotApp instance.
   * If the assistant does not exist, it creates a new one and sets its properties.
   *
   * @param app
   *     The CopilotApp instance for which the assistant is to be inserted or updated.
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @return The JSON object containing the response from the OpenAI API.
   * @throws JSONException
   *     If an error occurs while parsing the JSON object.
   * @throws IOException
   *     If an error occurs while making the request to the OpenAI API.
   */
  private static JSONObject upsertAssistant(CopilotApp app, String openaiApiKey)
      throws JSONException, IOException {
    String openaiIdAssistant = app.getOpenaiIdAssistant();
    if (StringUtils.isNotEmpty(openaiIdAssistant)) {
      if (!existsAssistant(openaiIdAssistant)) {
        openaiIdAssistant = null;
      }
    }
    String endpoint = ENDPOINT_ASSISTANTS + (StringUtils.isNotEmpty(openaiIdAssistant) ?
        "/" + openaiIdAssistant :
        "");
    JSONObject body = new JSONObject();
    body.put(INSTRUCTIONS, getAssistantPrompt(app));
    body.put("name", app.getName());
    body.put("tool_resources", generateToolsResources(app));
    body.put("tools", buildToolsArray(app));
    if (app.getTemperature() != null) {
      body.put("temperature", app.getTemperature());
    }
    String model = CopilotModelUtils.getAppModel(app, CopilotConstants.PROVIDER_OPENAI);
    logIfDebug("Selected model: " + model);
    body.put("model", model);
    JSONObject response = makeRequestToOpenAI(openaiApiKey, endpoint, body, "POST", null, false);
    logIfDebug(response.toString());
    if (response.has(ERROR)) {
      if (matchParamAndCode(response, INSTRUCTIONS, "string_above_max_length")) {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("ETCOP_Error_Sync_Instructions"), app.getName(),
                response.getJSONObject(ERROR).getString(MESSAGE)));
      }
      if (response.optJSONObject(ERROR) != null
          && StringUtils.isNotEmpty(response.getJSONObject(ERROR).optString(MESSAGE))
          && StringUtils.containsIgnoreCase(response.getJSONObject(ERROR).optString(MESSAGE), CODE_INT_TOO_LONG_ERR)
      ) {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("ETCOP_Error_Code_Int_Too_Long"), app.getName(),
                response.getJSONObject(ERROR).getString(MESSAGE)));
      }
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_Error_Syn_Assist"), app.getName(),
              response.getJSONObject(ERROR).getString(MESSAGE)));
    }
    if (!StringUtils.equals(app.getOpenaiIdAssistant(), response.getString("id"))) {
      app.setOpenaiIdAssistant(response.getString("id"));
      OBDal.getInstance().save(app);
      OBDal.getInstance().flush();
    }
    return response;
  }

  /**
   * Generates the tool resources for the given CopilotApp instance.
   * <p>
   * This method creates a JSON object containing the tool resources for the provided CopilotApp instance.
   *
   * @param app
   *     The CopilotApp instance for which the tool resources are to be generated.
   * @return The JSON object containing the tool resources.
   * @throws JSONException
   *     If an error occurs while creating the JSON object.
   */
  private static JSONObject generateToolsResources(CopilotApp app) throws JSONException {
    JSONObject toolsResources = new JSONObject();
    if (app.isCodeInterpreter()) {
      toolsResources.put("code_interpreter", generateCodeInterpreterResources(app));
    }
    if (app.isRetrieval()) {
      toolsResources.put("file_search", generateFileSearchResources(app));
    }
    return toolsResources;
  }

  /**
   * Generates the code interpreter resources for the given CopilotApp instance.
   * <p>
   * This method creates a JSON object containing the code interpreter resources for the provided CopilotApp instance.
   *
   * @param app
   *     The CopilotApp instance for which the code interpreter resources are to be generated.
   * @return The JSON object containing the code interpreter resources.
   * @throws JSONException
   *     If an error occurs while creating the JSON object.
   */
  private static JSONObject generateCodeInterpreterResources(CopilotApp app) throws JSONException {
    JSONArray files = getKbArrayFiles(app);
    JSONObject fileIds = new JSONObject();
    fileIds.put("file_ids", files);
    return fileIds;
  }

  /**
   * Generates the file search resources for the given CopilotApp instance.
   * <p>
   * This method creates a JSON object containing the file search resources for the provided CopilotApp instance.
   *
   * @param app
   *     The CopilotApp instance for which the file search resources are to be generated.
   * @return The JSON object containing the file search resources.
   * @throws JSONException
   *     If an error occurs while creating the JSON object.
   */
  private static JSONObject generateFileSearchResources(CopilotApp app) throws JSONException {
    JSONObject vectordb = new JSONObject();
    JSONArray vectorIds = new JSONArray();
    vectorIds.put(getOrCreateVectorDbId(app));
    vectordb.put("vector_store_ids", vectorIds);
    return vectordb;
  }

  /**
   * Lists all assistants from the OpenAI API.
   * <p>
   * This method makes a GET request to the OpenAI API to retrieve a list of assistants.
   * It then logs the ID, name, and creation date of each assistant.
   *
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @return A JSONArray containing the data of all assistants.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static JSONArray listAssistants(String openaiApiKey) throws JSONException {
    String endpoint = ENDPOINT_ASSISTANTS;
    JSONObject json = makeRequestToOpenAI(openaiApiKey, endpoint, null, "GET",
        "?order=desc&limit=100");
    JSONArray data = json.getJSONArray("data");
    for (int i = 0; i < data.length(); i++) {
      JSONObject assistant = data.getJSONObject(i);
      String created = assistant.getString(
          "created_at"); // convert the date to a timestamp. the created is in The Unix timestamp (in seconds) for when the assistant file was created.
      Date date = new Date(Long.parseLong(created) * 1000);
      logIfDebug(
          String.format("%s - %s - %s", assistant.getString("id"), assistant.getString("name"),
              date));
    }
    return data;
  }

  /**
   * Logs the given text if debug logging is enabled.
   *
   * @param text
   *     The text to log.
   */
  public static void logIfDebug(String text) {
    if (log.isDebugEnabled()) {
      log.debug(text);
    }
  }

  /**
   * Deletes an assistant from the OpenAI API.
   * <p>
   * This method makes a DELETE request to the OpenAI API to delete the specified assistant.
   *
   * @param openaiAssistantId
   *     The ID of the assistant to delete.
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static void deleteAssistant(String openaiAssistantId, String openaiApiKey)
      throws JSONException {
    String endpoint = ENDPOINT_ASSISTANTS + "/" + openaiAssistantId;
    JSONObject json = makeRequestToOpenAI(openaiApiKey, endpoint, null, METHOD_DELETE, null);
    logIfDebug(json.toString());
  }

  /**
   * Wraps the given parameters in a JSON schema.
   * <p>
   * This method creates a JSON object with a "type" of "object" and the given parameters as properties.
   *
   * @param parameters
   *     The parameters to wrap in the JSON schema.
   * @return A JSONObject representing the JSON schema.
   * @throws JSONException
   *     If an error occurs while creating the JSON object.
   */
  public static JSONObject wrappWithJSONSchema(JSONObject parameters) throws JSONException {
    return new JSONObject().put("type", "object").put("properties", parameters);
  }

  /**
   * Retrieves the knowledge base files for the given CopilotApp instance.
   * <p>
   * This method iterates through the app's sources and collects the file IDs of those that are not excluded from the code interpreter.
   *
   * @param app
   *     The CopilotApp instance for which to retrieve the knowledge base files.
   * @return A JSONArray containing the file IDs.
   */
  private static JSONArray getKbArrayFiles(CopilotApp app) {
    JSONArray result = new JSONArray();
    for (CopilotAppSource source : app.getETCOPAppSourceList()) {
      if (!source.isExcludeFromCodeInterpreter() && CopilotConstants.isKbBehaviour(source) &&
          (StringUtils.equalsIgnoreCase(source.getEtcopApp().getAppType(), CopilotConstants.APP_TYPE_OPENAI))) {
        String openaiIdFile;
        if (CopilotConstants.isFileTypeLocalOrRemoteFile(source.getFile())) {
          openaiIdFile = source.getOpenaiIdFile();
        } else {
          openaiIdFile = source.getOpenaiIdFile();
        }
        result.put(openaiIdFile);
      }
    }
    return result;
  }

  /**
   * Makes a request to the OpenAI API to upload a file.
   * <p>
   * This method uploads the given file to the OpenAI API for the specified purpose.
   *
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @param endpoint
   *     The endpoint to which the file should be uploaded.
   * @param purpose
   *     The purpose of the file upload.
   * @param fileToSend
   *     The file to upload.
   * @return A JSONObject containing the response from the OpenAI API.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static JSONObject makeRequestToOpenAIForFiles(String openaiApiKey, String endpoint,
      String purpose, File fileToSend) throws JSONException {
    String mimeType = URLConnection.guessContentTypeFromName(fileToSend.getName());
    kong.unirest.HttpResponse<String> response = Unirest.post(BASE_URL + endpoint)
        .header(HEADER_AUTHORIZATION, String.format("Bearer %s", openaiApiKey))
        .field("purpose", purpose)
        .field("file", fileToSend, mimeType)
        .asString();
    JSONObject jsonResponse = new JSONObject(response.getBody());
    if (!response.isSuccess()) {
      if (jsonResponse.has(ERROR)) {
        throw new OBException(jsonResponse.getJSONObject(ERROR).getString(MESSAGE));
      } else {
        throw new OBException(response.getBody());
      }
    }
    return jsonResponse;
  }

  /**
   * Makes a request to the OpenAI API.
   * <p>
   * This method constructs the URL and makes an HTTP request to the OpenAI API using the specified method.
   * It handles different HTTP methods (GET, POST, PUT, DELETE) and returns the JSON response.
   *
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @param endpoint
   *     The API endpoint to call.
   * @param body
   *     The JSON body to send with the request (for POST and PUT methods).
   * @param method
   *     The HTTP method to use (GET, POST, PUT, DELETE).
   * @param queryParams
   *     The query parameters to include in the URL.
   * @return The JSON response from the OpenAI API.
   * @throws UnirestException
   *     If an error occurs while making the HTTP request.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static JSONObject makeRequestToOpenAI(String openaiApiKey, String endpoint,
      JSONObject body, String method, String queryParams) throws UnirestException, JSONException {
    return makeRequestToOpenAI(openaiApiKey, endpoint, body, method, queryParams, true);
  }

  /**
   * Makes a request to the OpenAI API with an option to catch HTTP errors.
   * <p>
   * This method constructs the URL and makes an HTTP request to the OpenAI API using the specified method.
   * It handles different HTTP methods (GET, POST, PUT, DELETE) and returns the JSON response.
   * If `catchHttpErrors` is true, it throws an OBException for HTTP errors.
   *
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @param endpoint
   *     The API endpoint to call.
   * @param body
   *     The JSON body to send with the request (for POST and PUT methods).
   * @param method
   *     The HTTP method to use (GET, POST, PUT, DELETE).
   * @param queryParams
   *     The query parameters to include in the URL.
   * @param catchHttpErrors
   *     Whether to catch HTTP errors and throw an OBException.
   * @return The JSON response from the OpenAI API.
   * @throws UnirestException
   *     If an error occurs while making the HTTP request.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static JSONObject makeRequestToOpenAI(String openaiApiKey, String endpoint,
      JSONObject body, String method, String queryParams, boolean catchHttpErrors)
      throws UnirestException, JSONException {
    String url = BASE_URL + endpoint + ((queryParams != null) ? queryParams : "");
    HttpResponse<String> response;
    switch (method) {
      case "GET":
        response = Unirest.get(url)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .header(HEADER_AUTHORIZATION, HEADER_BEARER + openaiApiKey)
            .header(HEADER_OPEN_AI_BETA, HEADER_ASSISTANTS_V_2)
            .socketTimeout(MILLIES_SOCKET_TIMEOUT)
            .asString();
        break;
      case "POST":
        response = Unirest.post(url)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .header(HEADER_AUTHORIZATION, HEADER_BEARER + openaiApiKey)
            .header(HEADER_OPEN_AI_BETA, HEADER_ASSISTANTS_V_2)
            .socketTimeout(MILLIES_SOCKET_TIMEOUT)
            .body(body != null ? body.toString() : "")
            .asString();
        break;
      case "PUT":
        response = Unirest.put(url)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .header(HEADER_AUTHORIZATION, HEADER_BEARER + openaiApiKey)
            .header(HEADER_OPEN_AI_BETA, HEADER_ASSISTANTS_V_2)
            .socketTimeout(MILLIES_SOCKET_TIMEOUT)
            .body(body != null ? body.toString() : "")
            .asString();
        break;
      case METHOD_DELETE:
        response = Unirest.delete(url)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .header(HEADER_AUTHORIZATION, HEADER_BEARER + openaiApiKey)
            .header(HEADER_OPEN_AI_BETA, HEADER_ASSISTANTS_V_2)
            .socketTimeout(MILLIES_SOCKET_TIMEOUT)
            .asString();
        break;
      default:
        throw new IllegalArgumentException("Invalid method: " + method);
    }
    JSONObject jsonBody = new JSONObject(response.getBody());
    if (catchHttpErrors && !response.isSuccess()) {
      if (jsonBody.has(ERROR)) {
        throw new OBException(jsonBody.getJSONObject(ERROR).getString(MESSAGE));
      }
    }
    return new JSONObject(response.getBody());
  }

  /**
   * Builds an array of tools for the given CopilotApp instance.
   * <p>
   * This method creates a JSON array containing the tools configured for the provided CopilotApp instance.
   *
   * @param app
   *     The CopilotApp instance for which to build the tools array.
   * @return A JSONArray containing the tools.
   * @throws JSONException
   *     If an error occurs while creating the JSON array.
   */
  private static JSONArray buildToolsArray(CopilotApp app) throws JSONException {
    JSONArray toolSet = ToolsUtil.getToolSet(app);
    JSONObject tool = new JSONObject();
    if (Boolean.TRUE.equals(app.isCodeInterpreter())) {
      tool.put("type", "code_interpreter");
      toolSet.put(tool);
    }
    if (Boolean.TRUE.equals(app.isRetrieval())) {
      tool = new JSONObject();
      tool.put("type", "file_search");
      toolSet.put(tool);
    }
    return toolSet;
  }

  /**
   * Synchronizes the given CopilotAppSource instance with the OpenAI API.
   * <p>
   * This method checks if the file associated with the CopilotAppSource has changed.
   * If the file has changed, it uploads the file to the OpenAI API and updates the CopilotAppSource.
   *
   * @param appSource
   *     The CopilotAppSource instance to synchronize.
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   * @throws IOException
   *     If an error occurs while uploading the file.
   */
  public static void syncAppSource(CopilotAppSource appSource, String openaiApiKey)
      throws JSONException, IOException {
    //first we need to get the file
    //if the file not has an id, we need to create it
    logIfDebug("Syncing file " + appSource.getFile().getName());
    CopilotFile fileToSync = appSource.getFile();
    WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class)
        .executeHooks(fileToSync);
    if (!fileHasChanged(fileToSync)) {
      logIfDebug("File " + fileToSync.getName() + " not has changed, skipping sync");
      return;
    }
    if (StringUtils.isNotEmpty(appSource.getOpenaiIdFile())) {
      //we will delete the file
      logIfDebug("Deleting file " + fileToSync.getName());
      deleteFile(appSource.getOpenaiIdFile(), openaiApiKey);
    }
    if (CopilotConstants.isHQLQueryFile(appSource.getFile())) {
      syncHQLAppSource(appSource, openaiApiKey);
      return;
    }
    logIfDebug("Uploading file " + fileToSync.getName());
    String fileId = OpenAIUtils.downloadAttachmentAndUploadFile(fileToSync, openaiApiKey);
    fileToSync.setOpenaiIdFile(fileId);
    appSource.setOpenaiIdFile(fileId);
    OBDal.getInstance().save(appSource);
    fileToSync.setLastSync(new Date());
    fileToSync.setUpdated(new Date());
    OBDal.getInstance().save(fileToSync);
    OBDal.getInstance().flush();
  }

  /**
   * Synchronizes the HQL app source with the OpenAI API.
   * <p>
   * This method deletes the existing file if it exists, generates a new HQL file,
   * uploads it to the OpenAI API, and updates the app source with the new file ID.
   *
   * @param appSource
   *     The CopilotAppSource instance to synchronize.
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @return The ID of the uploaded file.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static String syncHQLAppSource(CopilotAppSource appSource, String openaiApiKey)
      throws JSONException {
    String openaiFileId = appSource.getOpenaiIdFile();
    if (StringUtils.isNotEmpty(openaiFileId)) {
      logIfDebug("Deleting file " + appSource.getFile().getName());
      deleteFile(appSource.getOpenaiIdFile(), openaiApiKey);
    }
    File file = FileUtils.generateHQLFile(appSource);

    String fileId = uploadFileToOpenAI(openaiApiKey, file);
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);

    aim.upload(new HashMap<>(), APP_SOURCE_TAB_ID, appSource.getId(),
        appSource.getOrganization().getId(), file);

    appSource.setOpenaiIdFile(fileId);
    OBDal.getInstance().save(appSource);
    OBDal.getInstance().flush();
    return fileId;
  }

  /**
   * Checks if a remote file exists on the OpenAI API.
   * <p>
   * This method makes a GET request to the OpenAI API to check if the specified file exists.
   *
   * @param openaiFileId
   *     The ID of the file to check.
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @return true if the file exists, false otherwise.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static boolean existsRemoteFile(String openaiFileId, String openaiApiKey)
      throws JSONException {
    var response = makeRequestToOpenAI(openaiApiKey, ENDPOINT_FILES + "/" + openaiFileId, null,
        "GET", null, false);
    return !response.has(ERROR);
  }

  /**
   * Checks if a file has changed since the last synchronization.
   * <p>
   * This method compares the last synchronization date and the updated date of the file.
   * It also checks if there are any attachments that have been updated since the last synchronization.
   *
   * @param fileToSync
   *     The CopilotFile instance to check.
   * @return true if the file has changed, false otherwise.
   */
  private static boolean fileHasChanged(CopilotFile fileToSync) {
    if (StringUtils.isEmpty(fileToSync.getOpenaiIdFile())) {
      return true;
    }
    Date lastSyncDate = fileToSync.getLastSync();
    if (lastSyncDate == null) {
      return true;
    }
    Date updated = fileToSync.getUpdated();
    // Clean the milliseconds
    lastSyncDate = new Date(lastSyncDate.getTime() / 1000 * 1000);
    updated = new Date(updated.getTime() / 1000 * 1000);

    if ((updated.after(lastSyncDate))) {
      return true;
    }
    // Check Attachments
    OBCriteria<Attachment> attCrit = OBDal.getInstance().createCriteria(Attachment.class);
    attCrit.add(Restrictions.eq(Attachment.PROPERTY_RECORD, fileToSync.getId()));
    Attachment attach = (Attachment) attCrit.setMaxResults(1).uniqueResult();
    if (attach == null) {
      return false;
    }

    Date updatedAtt = attach.getUpdated();
    updatedAtt = new Date(updatedAtt.getTime() / 1000 * 1000);
    return updatedAtt.after(lastSyncDate);
  }

  /**
   * Deletes a file from the OpenAI API.
   * <p>
   * This method makes a DELETE request to the OpenAI API to delete the specified file.
   *
   * @param openaiIdFile
   *     The ID of the file to delete.
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  static void deleteFile(String openaiIdFile, String openaiApiKey) throws JSONException {
    if (existsRemoteFile(openaiIdFile, openaiApiKey)) {
      JSONObject response = makeRequestToOpenAI(openaiApiKey, ENDPOINT_FILES + "/" + openaiIdFile,
          null, METHOD_DELETE, null);
      logIfDebug(response.toString());
    }
  }

  /**
   * Downloads an attachment and uploads it to the OpenAI API.
   * <p>
   * This method retrieves the file from the CopilotFile instance, uploads it to the OpenAI API,
   * and returns the ID of the uploaded file.
   *
   * @param fileToSync
   *     The CopilotFile instance to download and upload.
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @return The ID of the uploaded file.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   * @throws IOException
   *     If an error occurs while handling the file.
   */
  private static String downloadAttachmentAndUploadFile(CopilotFile fileToSync, String openaiApiKey)
      throws JSONException, IOException {
    // Make the request to OpenAI
    File tempFile = getFileFromCopilotFile(fileToSync);
    return uploadFileToOpenAI(openaiApiKey, tempFile);
  }

  /**
   * Retrieves a file from a CopilotFile instance.
   * <p>
   * This method downloads the attachment associated with the CopilotFile instance,
   * saves it to a temporary file, and returns the temporary file.
   *
   * @param fileToSync
   *     The CopilotFile instance to retrieve the file from.
   * @return The temporary file.
   * @throws IOException
   *     If an error occurs while handling the file.
   */
  public static File getFileFromCopilotFile(CopilotFile fileToSync) throws IOException {
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(
        AttachImplementationManager.class);
    OBCriteria<Attachment> attCrit = OBDal.getInstance().createCriteria(Attachment.class);
    attCrit.add(Restrictions.eq(Attachment.PROPERTY_RECORD, fileToSync.getId()));
    Attachment attach = (Attachment) attCrit.setMaxResults(1).uniqueResult();
    if (attach == null) {
      FileUtils.throwMissingAttachException(fileToSync);
    }
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    aim.download(attach.getId(), os);
    // Save os to temp file
    // Create a temp file
    String filename = attach.getName();
    String fileWithoutExtension = filename.substring(0, filename.lastIndexOf("."));
    String extension = filename.substring(filename.lastIndexOf(".") + 1);
    File tempFile = File.createTempFile(fileWithoutExtension, "." + extension);
    boolean setW = tempFile.setWritable(true);
    if (!setW) {
      logIfDebug("The temp file is not writable");
    }
    tempFile.deleteOnExit();
    os.writeTo(new FileOutputStream(tempFile));
    return tempFile;
  }

  /**
   * Uploads a file to the OpenAI API.
   * <p>
   * This method uploads the given file to the OpenAI API for the specified purpose.
   *
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @param fileToSend
   *     The file to upload.
   * @return The ID of the uploaded file.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  public static String uploadFileToOpenAI(String openaiApiKey, File fileToSend)
      throws JSONException {
    JSONObject jsonResponse;
    jsonResponse = makeRequestToOpenAIForFiles(openaiApiKey, ENDPOINT_FILES, "assistants",
        fileToSend);
    if (jsonResponse.has(ERROR)) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_Error_File_upload"), fileToSend.getName(),
              jsonResponse.getJSONObject(ERROR).getString(MESSAGE)));
    }
    return jsonResponse.getString("id");
  }

  /**
   * Retrieves the list of models from the OpenAI API.
   * <p>
   * This method makes a GET request to the OpenAI API to retrieve a list of available models.
   *
   * @param openaiApiKey
   *     The API key for OpenAI.
   * @return A JSONArray containing the list of models.
   * @throws OBException
   *     If an error occurs while parsing the JSON response.
   */
  public static JSONArray getModelList(String openaiApiKey) {
    try {
      JSONObject list = makeRequestToOpenAI(openaiApiKey, ENDPOINT_MODELS, null, "GET", null);
      return new JSONArray(list.getString("data"));
    } catch (JSONException e) {
      throw new OBException(e.getMessage());
    }
  }

  /**
   * Retrieves the OpenAI API key from the Openbravo properties.
   * <p>
   * This method reads the OpenAI API key from the Openbravo properties file.
   *
   * @return The OpenAI API key.
   */
  public static String getOpenaiApiKey() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    return properties.getProperty(OPENAI_API_KEY);
  }

  /**
   * Refreshes the vector database for the given CopilotApp instance.
   * <p>
   * This method updates the vector database with the latest files and removes outdated files.
   *
   * @param app
   *     The CopilotApp instance for which to refresh the vector database.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  public static void refreshVectorDb(CopilotApp app) throws JSONException {
    String openAIVectorDbId = getOrCreateVectorDbId(app);
    JSONObject currentFiles = makeRequestToOpenAI(getOpenaiApiKey(),
        ENDPOINT_VECTORDB + "/" + openAIVectorDbId + ENDPOINT_FILES, null, "GET", null);
    List<String> updatedFiles = updateVectorDbFiles(app, openAIVectorDbId);
    removeOutdatedFiles(updatedFiles, currentFiles, openAIVectorDbId);
  }

  /**
   * Retrieves or creates a vector database ID for the given CopilotApp instance.
   * <p>
   * This method checks if the vector database ID exists for the provided CopilotApp instance.
   * If it does not exist, it creates a new one.
   *
   * @param app
   *     The CopilotApp instance for which to retrieve or create the vector database ID.
   * @return The vector database ID.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static String getOrCreateVectorDbId(CopilotApp app) throws JSONException {
    if (app.getOpenaiVectordbID() != null) {
      if (!existsVectorDb(app.getOpenaiVectordbID())) {
        return createVectorDbId(app);
      }
      return app.getOpenaiVectordbID();
    }
    return createVectorDbId(app);
  }

  /**
   * Checks if a vector database exists on the OpenAI API.
   * <p>
   * This method makes a GET request to the OpenAI API to check if the specified vector database exists.
   *
   * @param openaiIdVectordb
   *     The ID of the vector database to check.
   * @return true if the vector database exists, false otherwise.
   */
  private static boolean existsVectorDb(String openaiIdVectordb) {
    try {
      JSONObject response = makeRequestToOpenAI(getOpenaiApiKey(),
          ENDPOINT_VECTORDB + "/" + openaiIdVectordb, null, "GET", null, false);
      return !response.has("error");
    } catch (JSONException e) {
      return false;
    }
  }

  /**
   * Checks if an assistant exists on the OpenAI API.
   * <p>
   * This method makes a GET request to the OpenAI API to check if the specified assistant exists.
   *
   * @param openaiAssistantId
   *     The ID of the assistant to check.
   * @return true if the assistant exists, false otherwise.
   */
  private static boolean existsAssistant(String openaiAssistantId) {
    try {
      JSONObject response = makeRequestToOpenAI(getOpenaiApiKey(),
          ENDPOINT_ASSISTANTS + "/" + openaiAssistantId, null, "GET", null, false);
      return !response.has("error");
    } catch (JSONException e) {
      return false;
    }
  }

  /**
   * Creates a vector database ID for the given CopilotApp instance.
   * <p>
   * This method creates a new vector database for the provided CopilotApp instance
   * and sets its properties. It then saves the vector database ID in the app instance.
   *
   * @param app
   *     The CopilotApp instance for which the vector database ID is to be created.
   * @return The vector database ID.
   * @throws JSONException
   *     If an error occurs while creating the JSON object.
   */
  private static String createVectorDbId(CopilotApp app) throws JSONException {
    JSONObject vectordb = new JSONObject();
    vectordb.put("name", app.getName());
    JSONObject response = makeRequestToOpenAI(getOpenaiApiKey(), ENDPOINT_VECTORDB, vectordb, "POST", null);
    String openAIVectorDbId = response.getString("id");
    app.setOpenaiVectordbID(openAIVectorDbId);
    OBDal.getInstance().save(app);
    OBDal.getInstance().flush();

    return openAIVectorDbId;
  }

  /**
   * Updates the vector database files for the given CopilotApp instance.
   * <p>
   * This method iterates through the app's sources and updates the vector database
   * with the latest files. It returns a list of updated file IDs.
   *
   * @param app
   *     The CopilotApp instance for which the vector database files are to be updated.
   * @param openAIVectorDbId
   *     The ID of the vector database to update.
   * @return A list of updated file IDs.
   * @throws JSONException
   *     If an error occurs while creating the JSON object.
   */
  private static List<String> updateVectorDbFiles(CopilotApp app, String openAIVectorDbId) throws JSONException {
    List<String> updatedFiles = new ArrayList<>();
    for (CopilotAppSource copilotAppSource : app.getETCOPAppSourceList()) {
      updateVectorDBKBFile(app, openAIVectorDbId, copilotAppSource, updatedFiles);
    }
    return updatedFiles;
  }

  /**
   * Updates a specific knowledge base file in the vector database.
   * <p>
   * This method checks if the given CopilotAppSource should be included in the retrieval process.
   * If it should, it updates the vector database with the file associated with the CopilotAppSource.
   *
   * @param app
   *     The CopilotApp instance for which the vector database is being updated.
   * @param openAIVectorDbId
   *     The ID of the vector database to update.
   * @param copilotAppSource
   *     The CopilotAppSource instance containing the file to update.
   * @param updatedFiles
   *     The list of updated file IDs to which the new file ID will be added.
   * @throws JSONException
   *     If an error occurs while creating the JSON object.
   */
  private static void updateVectorDBKBFile(CopilotApp app, String openAIVectorDbId, CopilotAppSource copilotAppSource,
      List<String> updatedFiles) throws JSONException {
    if (copilotAppSource.isExcludeFromRetrieval()
        || !CopilotConstants.isKbBehaviour(copilotAppSource)
        || copilotAppSource.getFile() == null) {
      return;
    }

    CopilotFile file = copilotAppSource.getFile();
    String openAIFileId = StringUtils.isNotEmpty(
        copilotAppSource.getOpenaiIdFile()) ? copilotAppSource.getOpenaiIdFile() : file.getOpenaiIdFile();
    JSONObject fileSearch = new JSONObject();
    fileSearch.put("file_id", openAIFileId);
    var response = makeRequestToOpenAI(getOpenaiApiKey(),
        ENDPOINT_VECTORDB + "/" + openAIVectorDbId + ENDPOINT_FILES, fileSearch, "POST", null, false);
    if (response.has(ERROR)) {
      if (app.isCodeInterpreter()) {
        log.warn("Error updating file in vector db: " + response);
        return;
      }
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_Error_Updating_VectorDb"), app.getName(),
              response.getJSONObject(ERROR).getString(MESSAGE)));

    }
    updatedFiles.add(openAIFileId);
  }

  /**
   * Removes outdated files from the vector database.
   * <p>
   * This method iterates through the current files in the vector database and removes
   * any files that are not in the list of updated files.
   *
   * @param updatedFiles
   *     A list of updated file IDs.
   * @param currentFiles
   *     The JSON object containing the current files in the vector database.
   * @param openAIVectorDbId
   *     The ID of the vector database to update.
   * @throws JSONException
   *     If an error occurs while parsing the JSON object.
   */
  private static void removeOutdatedFiles(List<String> updatedFiles, JSONObject currentFiles,
      String openAIVectorDbId) throws JSONException {
    if (currentFiles == null || !currentFiles.has("data")) {
      return;
    }
    for (int i = 0; i < currentFiles.getJSONArray("data").length(); i++) {
      JSONObject existingFile = currentFiles.getJSONArray("data").getJSONObject(i);
      if (!updatedFiles.contains(existingFile.getString("id"))) {
        String existingFileId = existingFile.getString("id");
        makeRequestToOpenAI(getOpenaiApiKey(),
            OpenAIUtils.ENDPOINT_VECTORDB + "/" + openAIVectorDbId + ENDPOINT_FILES + "/" + existingFileId, null,
            METHOD_DELETE, null);
      }
    }
  }

  /**
   * Verifies whether the specified {@link CopilotApp} can use attached files
   * configured as a knowledge base. If the list of knowledge base files is not empty
   * and the application does not have the flags "Code Interpreter" or "Retrieval" checked,
   * an {@link OBException} is thrown with a descriptive error message.
   *
   * @param app
   *     The {@link CopilotApp} instance being verified for compatibility with knowledge base files.
   * @param knowledgeBaseFiles
   *     A list of {@link CopilotAppSource} objects representing files configured as a knowledge base.
   * @throws OBException
   *     If the application does not support "Code Interpreter" or "Retrieval" features,
   *     making it incompatible with knowledge base files. The error message will state:
   *     "The app does not have 'Code Interpreter' or 'Retrieval' configured, so files configured as 'Knowledge Base' cannot be attached."
   */
  public static void checkIfAppCanUseAttachedFiles(CopilotApp app, List<CopilotAppSource> knowledgeBaseFiles) {
    if (!knowledgeBaseFiles.isEmpty() && !app.isCodeInterpreter() && !app.isRetrieval()) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_Error_KnowledgeBaseIgnored"), app.getName()));
    }
  }
}
