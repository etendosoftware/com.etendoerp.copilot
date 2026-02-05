package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.process.SyncAssistant.ERROR;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
    kong.unirest.HttpResponse<String> response = Unirest.post(getBaseUrl() + endpoint)
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
   * Returns the base URL to use for OpenAI API requests.
   * <p>
   * The method first checks Openbravo properties for the key {@code COPILOT_PROXY_URL}.
   * If present, that value is returned allowing a proxy or custom endpoint to override
   * the default OpenAI API base URL. Otherwise the constant {@link #BASE_URL} is returned.
   *
   * @return the base URL to use for OpenAI API requests (proxy override if configured)
   */
  private static String getBaseUrl() {
    var prop = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    if (prop.containsKey("COPILOT_PROXY_URL")) {
      return prop.getProperty("COPILOT_PROXY_URL");
    }
    return BASE_URL;
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
  static JSONObject makeRequestToOpenAI(String openaiApiKey, String endpoint,
      JSONObject body, String method, String queryParams, boolean catchHttpErrors)
      throws UnirestException, JSONException {
    String url = getBaseUrl() + endpoint + ((queryParams != null) ? queryParams : "");
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
    if (catchHttpErrors && !response.isSuccess() && jsonBody.has(ERROR)) {
      throw new OBException(jsonBody.getJSONObject(ERROR).getString(MESSAGE));
    }
    if (catchHttpErrors && !response.isSuccess()) {
      throw new OBException(response.getBody());
    }
    return jsonBody;
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
    File tempFile = FileUtils.createSecureTempFile(fileWithoutExtension, "." + extension).toFile();
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
