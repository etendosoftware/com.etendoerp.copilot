package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.rest.RestServiceUtil.replaceAliasInPrompt;
import static com.etendoerp.copilot.util.CopilotConstants.LANGCHAIN_MAX_LENGTH_QUESTION;
import static com.etendoerp.copilot.util.CopilotConstants.isHQLQueryFile;
import static com.etendoerp.copilot.util.OpenAIUtils.ENDPOINT_MODELS;
import static com.etendoerp.copilot.util.OpenAIUtils.deleteFile;
import static com.etendoerp.webhookevents.webhook_util.OpenAPISpecUtils.PROP_NAME;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import kong.unirest.UnirestException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotModel;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.hook.OpenAIPromptHookManager;
import com.etendoerp.copilot.hook.ProcessHQLAppSource;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * CopilotUtils is a utility class that provides various methods for interacting with
 * the Copilot application, including sending requests to the Copilot service, handling
 * file uploads, and managing vector databases.
 */
public class CopilotUtils {

  public static final String MAX_CHUNK_SIZE = "max_chunk_size";
  public static final String CHUNK_OVERLAP = "chunk_overlap";

  private CopilotUtils() {
    // Private constructor to prevent instantiation
  }

  private static final Logger log = LogManager.getLogger(CopilotUtils.class);
  private static final String BOUNDARY = UUID.randomUUID().toString();
  public static final String KB_VECTORDB_ID = "kb_vectordb_id";
  public static final String COPILOT_PORT = "COPILOT_PORT";
  public static final String COPILOT_HOST = "COPILOT_HOST";
  private static final String DEFAULT_PROMPT_PREFERENCE_KEY = "ETCOP_DefaultContextPrompt";
  public static final String DEFAULT_MODELS_DATASET_URL = "https://raw.githubusercontent.com/etendosoftware/com.etendoerp.copilot/refs/heads/<BRANCH>/referencedata/standard/AI_Models_Dataset.xml";


  /**
   * Uploads a file to the vector database with specified parameters.
   * <p>
   * This method sends a file to the vector database using the Copilot service.
   * It constructs a JSON request with the file details, database name, format,
   * and additional parameters such as chunk size and overlap. If the response
   * from the Copilot service indicates a failure, an {@link OBException} is thrown.
   *
   * @param fileToSend
   *     The {@link File} to be uploaded to the vector database.
   * @param dbName
   *     The name of the vector database to which the file will be uploaded.
   * @param format
   *     The file format (e.g., "csv", "json").
   * @param skipSplitting
   *     A boolean indicating whether to skip splitting the file into chunks.
   * @param maxChunkSize
   *     The maximum size of each chunk (in bytes), or null if not specified.
   * @param chunkOverlap
   *     The overlap size between chunks (in bytes), or null if not specified.
   * @throws JSONException
   *     If an error occurs while constructing the JSON request.
   * @throws OBException
   *     If the response from the Copilot service indicates a failure.
   */
  public static void toVectorDB(File fileToSend, String dbName, String format, boolean skipSplitting, Long maxChunkSize,
      Long chunkOverlap) throws JSONException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String endpoint = "addToVectorDB";
    HttpResponse<String> responseFromCopilot;
    JSONObject jsonRequestForCopilot = new JSONObject();
    jsonRequestForCopilot.put("filename", fileToSend.getName());
    jsonRequestForCopilot.put(KB_VECTORDB_ID, dbName);
    jsonRequestForCopilot.put("extension", format);
    jsonRequestForCopilot.put("overwrite", false);
    jsonRequestForCopilot.put("skip_splitting", skipSplitting);
    if (maxChunkSize != null) {
      jsonRequestForCopilot.put(MAX_CHUNK_SIZE, maxChunkSize);
    }
    if (chunkOverlap != null) {
      jsonRequestForCopilot.put(CHUNK_OVERLAP, chunkOverlap);
    }

    responseFromCopilot = getResponseFromCopilot(properties, endpoint, jsonRequestForCopilot, fileToSend);

    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200 || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_Error_sync_vectorDB")));
    }
  }

  /**
   * Sends an HTTP POST request to the Copilot service and retrieves the response.
   * <p>
   * This method constructs an HTTP request based on the provided parameters, including
   * the endpoint, JSON body, and optional file to send. If a file is provided, the request
   * is sent as a multipart form-data; otherwise, it is sent as a JSON payload. The method
   * handles exceptions and wraps them in an {@link OBException}.
   *
   * @param properties
   *     The {@link Properties} object containing configuration values, such as the Copilot host and port.
   * @param endpoint
   *     The endpoint of the Copilot service to which the request will be sent.
   * @param jsonBody
   *     The {@link JSONObject} containing the JSON payload to include in the request.
   * @param fileToSend
   *     An optional {@link File} to include in the request as a multipart form-data. If null, the request is sent as JSON.
   * @return An {@link HttpResponse} object containing the response from the Copilot service.
   * @throws OBException
   *     If an error occurs during the request or response handling.
   */
  public static HttpResponse<String> getResponseFromCopilot(Properties properties, String endpoint, JSONObject jsonBody,
      File fileToSend) {

    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty(COPILOT_PORT, "5005");
      String copilotHost = properties.getProperty(COPILOT_HOST, "localhost");

      HttpRequest.BodyPublisher requestBodyPublisher;
      String contentType;

      if (fileToSend != null) {
        requestBodyPublisher = createMultipartBody(jsonBody, fileToSend);
        contentType = "multipart/form-data;boundary=" + BOUNDARY;
      } else {
        requestBodyPublisher = HttpRequest.BodyPublishers.ofString(jsonBody.toString());
        contentType = "application/json;charset=UTF-8";
      }

      HttpRequest copilotRequest = HttpRequest.newBuilder().uri(
          new URI(String.format("http://%s:%s/%s", copilotHost, copilotPort, endpoint))).header("Content-Type",
          contentType).version(HttpClient.Version.HTTP_1_1).POST(requestBodyPublisher).build();

      return client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OBException(e);
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  static HttpResponse<String> doGetCopilot(Properties properties, String endpoint) {
    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty(COPILOT_PORT, "5005");
      String copilotHost = properties.getProperty(COPILOT_HOST, "localhost");

      HttpRequest copilotRequest = HttpRequest.newBuilder().uri(
          new URI(String.format("http://%s:%s/%s", copilotHost, copilotPort, endpoint))).GET().build();

      return client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OBException(e);
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  static HttpRequest.BodyPublisher createMultipartBody(JSONObject jsonBody,
      File file) throws IOException, JSONException {
    var byteArrays = new ByteArrayOutputStream();
    var writer = new PrintWriter(new OutputStreamWriter(byteArrays, StandardCharsets.UTF_8), true);

    String kbVectorDBId = jsonBody.optString(KB_VECTORDB_ID);
    String text = jsonBody.optString("text", null);
    String extension = jsonBody.optString("extension");
    boolean overwrite = jsonBody.optBoolean("overwrite", false);
    boolean skipSplitting = jsonBody.optBoolean("skip_splitting", false);
    if (kbVectorDBId != null) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"kb_vectordb_id\"\r\n\r\n");
      writer.append(kbVectorDBId).append("\r\n");
    }
    if (text != null) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"text\"\r\n\r\n");
      writer.append(text).append("\r\n");
    }
    if (extension != null) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"extension\"\r\n\r\n");
      writer.append(extension).append("\r\n");
    }
    if (overwrite) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"overwrite\"\r\n\r\n");
      writer.append(String.valueOf(overwrite)).append("\r\n");
    }
    if (skipSplitting) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"skip_splitting\"\r\n\r\n");
      writer.append(String.valueOf(skipSplitting)).append("\r\n");
    }
    if (jsonBody.has(MAX_CHUNK_SIZE)) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"max_chunk_size\"\r\n\r\n");
      writer.append(String.valueOf(jsonBody.getLong(MAX_CHUNK_SIZE))).append("\r\n");
    }
    if (jsonBody.has(CHUNK_OVERLAP)) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"chunk_overlap\"\r\n\r\n");
      writer.append(String.valueOf(jsonBody.getLong(CHUNK_OVERLAP))).append("\r\n");
    }
    // File part
    if (file != null) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append(
          "\"\r\n");
      writer.append("Content-Type: application/octet-stream\r\n\r\n");
      writer.flush();

      Files.copy(file.toPath(), byteArrays);
      writer.append("\r\n").flush();
    }

    writer.append("--").append(BOUNDARY).append("--\r\n");
    writer.close();

    return HttpRequest.BodyPublishers.ofByteArray(byteArrays.toByteArray());
  }

  /**
   * Resets the vector database for the specified {@link CopilotApp} instance.
   * <p>
   * This method sends a request to the Copilot service to reset the vector database
   * associated with the given application. If the response status code is not in the
   * range of 200-299, an {@link OBException} is thrown with an appropriate error message.
   *
   * @param app
   *     The {@link CopilotApp} instance for which the vector database is to be reset.
   * @throws JSONException
   *     If an error occurs while constructing the JSON request.
   * @throws OBException
   *     If the response from the Copilot service indicates a failure.
   */
  public static void resetVectorDB(CopilotApp app) throws JSONException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String orgId = OBContext.getOBContext().getCurrentOrganization().getId();
    String dbName = "KB_" + app.getId() + "_" + orgId;
    JSONObject jsonRequestForCopilot = new JSONObject();

    jsonRequestForCopilot.put(KB_VECTORDB_ID, dbName);
    String endpoint = "ResetVectorDB";
    HttpResponse<String> responseFromCopilot = getResponseFromCopilot(properties, endpoint, jsonRequestForCopilot,
        null);
    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200 || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB"), app.getName(),
          responseFromCopilot != null ? responseFromCopilot.body() : ""));
    }

  }

  /**
   * Logs a debug message if debug logging is enabled.
   * <p>
   * This method checks if the debug level is enabled for the logger.
   * If it is, the provided message is logged at the debug level.
   *
   * @param text
   *     The message to be logged if debug logging is enabled.
   */
  public static void logIfDebug(String text) {
    if (log.isDebugEnabled()) {
      log.debug(text);
    }
  }

  /**
   * Synchronizes a LangChain source file for a given {@link CopilotAppSource}.
   * <p>
   * This method handles the synchronization of a LangChain source file by determining
   * the file type, processing it accordingly, and uploading it to the vector database.
   * If the file is an HQL query file, it generates the file and deletes any existing
   * OpenAI file associated with it. For other file types, it retrieves the file from
   * the CopilotFile instance. The method also validates the file extension and ensures
   * it is supported before uploading.
   *
   * @param appSource
   *     The {@link CopilotAppSource} instance containing the file to be synchronized.
   * @throws IOException
   *     If an I/O error occurs during file processing.
   * @throws JSONException
   *     If an error occurs while handling JSON data.
   */
  public static void syncAppLangchainSource(CopilotAppSource appSource) throws IOException, JSONException {

    // Retrieve the file to be synchronized
    CopilotFile fileToSync = appSource.getFile();
    WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class).executeHooks(fileToSync);
    logIfDebug("Uploading file " + fileToSync.getName());

    // Extract the file name and determine its extension
    String filename = fileToSync.getFilename();
    String extension = StringUtils.isNotEmpty(filename) ? filename.substring(filename.lastIndexOf(".") + 1) : null;

    File fileFromCopilotFile = null;

    // Handle HQL query files
    if (isHQLQueryFile(fileToSync)) {
      String openaiFileId = appSource.getOpenaiIdFile();
      if (StringUtils.isNotEmpty(openaiFileId)) {
        logIfDebug("Deleting file " + appSource.getFile().getName());
        deleteFile(appSource.getOpenaiIdFile(), OpenAIUtils.getOpenaiApiKey());
      }

      fileFromCopilotFile = FileUtils.generateHQLFile(appSource);

    } else {
      // Retrieve the file for non-HQL query files
      fileFromCopilotFile = FileUtils.getFileFromCopilotFile(fileToSync);
    }

    // Prepare database and synchronization parameters
    String orgId = OBContext.getOBContext().getCurrentOrganization().getId();
    String dbName = "KB_" + appSource.getEtcopApp().getId() + "_" + orgId;
    boolean skipSplitting = appSource.getFile().isSkipSplitting();
    Long maxChunkSize = appSource.getFile().getMaxChunkSize();
    Long chunkOverlap = appSource.getFile().getChunkOverlap();

    // Validate and determine the file extension
    if (StringUtils.isEmpty(extension)) {
      extension = fileFromCopilotFile.getName().substring(fileFromCopilotFile.getName().lastIndexOf(".") + 1);
    }

    // Check if the file extension is valid
    if (FileUtils.isValidExtension(extension)) {
      // Upload the file to the vector database
      FileUtils.binaryFileToVectorDB(fileFromCopilotFile, dbName, extension, skipSplitting, maxChunkSize, chunkOverlap);
    } else {
      // Throw an exception for invalid file formats
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorInvalidFormat"), extension));
    }


    OBDal.getInstance().flush();
  }

  /**
   * Validates the length of the provided prompt.
   * <p>
   * This method checks if the length of the given prompt exceeds the maximum allowed
   * length defined by {@link CopilotConstants#LANGCHAIN_MAX_LENGTH_PROMPT}. If the length
   * exceeds the limit, an {@link OBException} is thrown with an appropriate error message.
   *
   * @param prompt
   *     The {@link StringBuilder} containing the prompt to be validated.
   * @throws OBException
   *     If the prompt length exceeds the maximum allowed limit.
   */
  public static void checkPromptLength(StringBuilder prompt) {
    if (prompt.length() > CopilotConstants.LANGCHAIN_MAX_LENGTH_PROMPT) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MaxLengthPrompt")));
    }
  }

  /**
   * Generates the assistant prompt for a given Copilot application.
   * <p>
   * This method constructs a prompt by appending the application's base prompt,
   * executing hooks, and including additional context such as default preferences
   * and application source content. It also handles alias replacement for specific
   * application sources.
   *
   * @param app
   *     The {@link CopilotApp} instance for which the assistant prompt is generated.
   * @return A {@link String} representing the generated assistant prompt.
   * @throws IOException
   *     If an I/O error occurs while retrieving application source content.
   */
  public static String getAssistantPrompt(CopilotApp app) throws IOException {
    StringBuilder promptBuilder = new StringBuilder();
    promptBuilder.append(app.getPrompt());
    promptBuilder.append("\n");

    try {
      promptBuilder.append(WeldUtils.getInstanceFromStaticBeanManager(OpenAIPromptHookManager.class).executeHooks(app));
    } catch (OBException e) {
      log.error("Error executing hooks", e);
    }

    OBContext context = OBContext.getOBContext();
    String defaultContextPrompt = null;

    try {
      if (context.getCurrentClient() != null && context.getCurrentOrganization() != null && context.getUser() != null && context.getRole() != null) {

        defaultContextPrompt = Preferences.getPreferenceValue(DEFAULT_PROMPT_PREFERENCE_KEY, true,
            context.getCurrentClient().getId(), context.getCurrentOrganization().getId(), context.getUser().getId(),
            context.getRole().getId(), null);
      } else {
        log.warn("OBContext values are incomplete; skipping defaultContextPrompt retrieval.");
      }
    } catch (PropertyException e) {
      log.error("Error retrieving default context prompt", e);
    }

    if (defaultContextPrompt != null) {
      promptBuilder.append(defaultContextPrompt).append("\n");
    } else {
      log.warn("No default context prompt found.");
    }

    List<CopilotAppSource> appSourcesToAppend = app.getETCOPAppSourceList();

    // app sources to replace with an alias
    List<CopilotAppSource> appSourcesWithAlias = appSourcesToAppend.stream().filter(
        appSource -> StringUtils.equalsIgnoreCase(appSource.getBehaviour(),
            CopilotConstants.FILE_BEHAVIOUR_SYSTEM) && StringUtils.isNotEmpty(appSource.getAlias())).collect(
        Collectors.toList());

    // the app sources to append are the ones that are not with an alias
    appSourcesToAppend = appSourcesToAppend.stream().filter(
        appSource -> !appSourcesWithAlias.contains(appSource)).collect(Collectors.toList());

    promptBuilder = replaceAliasInPrompt(promptBuilder, appSourcesWithAlias);

    promptBuilder.append(getAppSourceContent(appSourcesToAppend, CopilotConstants.FILE_BEHAVIOUR_SYSTEM));

    return CopilotVarReplacerUtil.replaceCopilotPromptVariables(promptBuilder.toString());
  }









  /**
   * Retrieves the source path from the provided properties.
   * This method checks if the application is running inside a Docker container.
   * If it is running inside Docker, it returns an empty string.
   * Otherwise, it retrieves the source path from the properties using the key
   * "source.path".
   *
   * @param properties
   *     The properties object containing configuration values.
   * @return The source path if not running inside Docker, otherwise an empty
   *     string.
   * @throws RuntimeException
   *     If an error occurs while checking the running
   *     environment.
   */
  static String getSourcesPath(Properties properties) {
    boolean inDocker;
    try {
      inDocker = isCopilotRunningInDocker(properties);
      if (inDocker) {
        return "";
      }
      return properties.getProperty("source.path");
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  static boolean isCopilotRunningInDocker(Properties properties) {
    boolean inDocker = false;
    try {
      var resp = doGetCopilot(properties, "runningCheck");
      inDocker = StringUtils.contains(resp.body(), "docker");
    } catch (Exception e) {
      log.error(OBMessageUtils.messageBD("ETCOP_ErrorRunningCheck"), e);
    }
    return inDocker;
  }

  /**
   * This method retrieves the host name of Etendo from the system properties.
   * It uses the key "ETENDO_HOST" to fetch the value from the properties.
   * If the key is not found in the properties, it retu rns "ERROR" as a default
   * value.
   *
   * @return The host name of Etendo if found, otherwise "ERROR".
   */
  public static String getEtendoHost() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    return properties.getProperty("ETENDO_HOST", "ETENDO_HOST_NOT_CONFIGURED");
  }

  public static String getEtendoHostDocker() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String hostDocker = properties.getProperty("ETENDO_HOST_DOCKER", "");
    if (StringUtils.isEmpty(hostDocker)) {
      hostDocker = getEtendoHost();
    }
    return hostDocker;
  }

  public static String getCopilotHost() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    return properties.getProperty(COPILOT_HOST, "");
  }

  public static String getCopilotPort() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    return properties.getProperty(COPILOT_PORT, "5005");
  }

  /**
   * Retrieves the content of application source files based on their type.
   * <p>
   * This method iterates through a list of {@link CopilotAppSource} instances,
   * checks if their behavior matches the specified type, and retrieves their content.
   * The content of each file is appended to a string, separated by delimiters.
   * If an error occurs while reading a file, an appropriate exception is thrown.
   *
   * @param appSourceList
   *     A list of {@link CopilotAppSource} instances to process.
   * @param type
   *     The type of behavior to filter the application sources.
   * @return A {@link String} containing the concatenated content of the application source files.
   * @throws OBException
   *     If a file has malformed content or an I/O error occurs.
   */
  public static String getAppSourceContent(List<CopilotAppSource> appSourceList, String type) {
    StringBuilder content = new StringBuilder();
    for (CopilotAppSource appSource : appSourceList) {
      if (StringUtils.equals(appSource.getBehaviour(), type) && appSource.getFile() != null) {
        try {
          String contentString = getAppSourceContent(appSource);
          content.append("\n---\n");
          content.append(appSource.getFile().getName()).append("\n");
          content.append(contentString).append("\n");
          content.append("\n---\n");
        } catch (MalformedInputException e) {
          throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_Error_MalformedSourceContent"),
              appSource.getFile().getName(), appSource.getEtcopApp().getName()));
        } catch (IOException e) {
          log.error(e);
          throw new OBException(e);
        }
      }
    }
    return content.toString();
  }

  /**
   * Retrieves the content of an application source file.
   * <p>
   * This method determines whether the file associated with the given {@link CopilotAppSource}
   * is an HQL query file. If it is, the file is generated using {@link ProcessHQLAppSource}.
   * Otherwise, the file is retrieved using the {@link FileUtils#getFileFromCopilotFile(CopilotFile)} method.
   * The content of the file is then read and returned as a string.
   *
   * @param appSource
   *     The {@link CopilotAppSource} instance containing the file to be processed.
   * @return A {@link String} representing the content of the application source file.
   * @throws IOException
   *     If an I/O error occurs while reading the file.
   */
  public static String getAppSourceContent(CopilotAppSource appSource) throws IOException {
    File tempFile;
    if (isHQLQueryFile(appSource.getFile())) {
      tempFile = ProcessHQLAppSource.getInstance().generate(appSource);
    } else {
      tempFile = FileUtils.getFileFromCopilotFile(appSource.getFile());
    }
    return Files.readString(tempFile.toPath());
  }

  /**
   * Generates a secure token for Etendo web services.
   * <p>
   * This method generates a secure token for Etendo web services by calling
   * {@link #getEtendoSWSToken(OBContext, Role)} with the current {@link OBContext}.
   *
   * @return A {@link String} representing the generated secure token.
   * @throws Exception
   *     If an error occurs while generating the token.
   */
  public static String generateEtendoToken() throws Exception {
    return getEtendoSWSToken(OBContext.getOBContext(), null);
  }

  /**
   * Purges the vector database for the given CopilotApp instance.
   * This method sends a request to the Copilot service to purge the vector
   * database associated with the specified app.
   * If the response status code is not in the range of 200-299, it throws an
   * OBException.
   *
   * @param app
   *     The CopilotApp instance for which the vector database is to be
   *     purged.
   * @throws JSONException
   *     If there is an error constructing the JSON request.
   * @throws OBException
   *     If the response from the Copilot service indicates a
   *     failure.
   */
  public static void purgeVectorDB(CopilotApp app) throws JSONException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String orgId = OBContext.getOBContext().getCurrentOrganization().getId();
    String dbName = "KB_" + app.getId() + "_" + orgId;
    JSONObject jsonRequestForCopilot = new JSONObject();

    jsonRequestForCopilot.put(KB_VECTORDB_ID, dbName);
    String endpoint = "purgeVectorDB";
    HttpResponse<String> responseFromCopilot = getResponseFromCopilot(properties, endpoint, jsonRequestForCopilot,
        null);
    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200 || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB"), app.getName(),
          responseFromCopilot != null ? responseFromCopilot.body() : ""));
    }

  }

  /**
   * Retrieves the configuration for all models in the system.
   * <p>
   * This method creates a JSON object containing the configuration details for
   * each model,
   * organized by provider and model name. The configuration includes the maximum
   * number of tokens
   * allowed for each model.
   *
   * @return A JSONObject representing the configuration of all models, organized
   *     by provider and model name.
   * @throws JSONException
   *     If an error occurs while creating the JSON object.
   */
  public static JSONObject getModelsConfigJSON() throws JSONException {
    JSONObject modelsConfig = new JSONObject();
    var models = OBDal.getInstance().createCriteria(CopilotModel.class).list();
    for (CopilotModel model : models) {
      String provider = model.getProvider() != null ? model.getProvider() : "null";
      String modelName = model.getSearchkey();
      Integer maxTokens = model.getMaxTokens() != null ? Math.toIntExact(model.getMaxTokens()) : null;
      if (!modelsConfig.has(provider)) {
        modelsConfig.put(provider, new JSONObject());
      }
      var providerConfig = modelsConfig.getJSONObject(provider);
      if (!providerConfig.has(modelName)) {
        providerConfig.put(modelName, new JSONObject());
      }
      var modelConfig = providerConfig.getJSONObject(modelName);
      modelConfig.put("max_tokens", maxTokens);
    }
    return modelsConfig;
  }

  /**
   * Generates a JSON object containing authentication information.
   * <p>
   * This method creates a JSON object and adds an authentication token to it if
   * the role has web service enabled.
   *
   * @param role
   *     The role of the user.
   * @param context
   *     The OBContext containing the current session information.
   * @return A JSON object containing the authentication token.
   * @throws Exception
   *     If an error occurs while generating the token.
   */
  public static JSONObject getAuthJson(Role role, OBContext context) throws Exception {
    JSONObject authJson = new JSONObject();
    // Adding auth token to interact with the Etendo web services
    if (role.isWebServiceEnabled().booleanValue()) {
      authJson.put("ETENDO_TOKEN", getEtendoSWSToken(context, role));
    }
    return authJson;
  }

  /**
   * Generates a secure token for Etendo web services.
   * <p>
   * This method retrieves the user, current organization, and warehouse from the
   * OBContext,
   * and then generates a secure token using these details.
   *
   * @param context
   *     The OBContext containing the current session information.
   * @param role
   *     The role of the user for which the token is being generated.
   *     If null, the role is retrieved from the context.
   * @return A secure token for Etendo web services.
   * @throws Exception
   *     If an error occurs while generating the token.
   */
  static String getEtendoSWSToken(OBContext context, Role role) throws Exception {
    if (role == null) {
      role = OBDal.getInstance().get(Role.class, context.getRole().getId());
    }
    // Refresh to avoid LazyInitializationException
    User user = OBDal.getInstance().get(User.class, context.getUser().getId());
    Organization currentOrganization = OBDal.getInstance().get(Organization.class,
        context.getCurrentOrganization().getId());
    Warehouse warehouse = context.getWarehouse() != null ? OBDal.getInstance().get(Warehouse.class,
        context.getWarehouse().getId()) : null;
    return SecureWebServicesUtils.generateToken(user, role, currentOrganization, warehouse);
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

  /**
   * Validates the OpenAI API key by sending a test request to the OpenAI models endpoint.
   * <p>
   * This method retrieves the OpenAI API key from the configuration, constructs an HTTP GET request
   * to the OpenAI models endpoint, and checks the response status code. If the response indicates
   * an error (non-200 status code), an {@link OBException} is thrown with an appropriate error message.
   * <p>
   * If any exception occurs during the process, it is caught and rethrown as an {@link OBException}.
   *
   * @throws OBException
   *     If the OpenAI API key is invalid or an error occurs during the validation process.
   */
  public static void validateOpenAIKey() {
    try {
      final String openaiApiKey = OpenAIUtils.getOpenaiApiKey();
      // Use OpenAIUtils helper to call the models endpoint. The helper returns a JSONObject when successful.
      JSONObject resp = OpenAIUtils.makeRequestToOpenAI(openaiApiKey, ENDPOINT_MODELS, null, "GET", null, true);
      if (resp == null) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_OpenAIKeyNotValid"));
      }
    } catch (UnirestException | JSONException e) {
      throw new OBException(e.getMessage());
    }
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
  public static void checkQuestionPrompt(String question) {
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
   * @throws JSONException
   *     If there is an error while constructing the JSON request.
   */
  public static void buildLangraphRequestForCopilot(CopilotApp copilotApp, String conversationId,
      JSONObject jsonRequestForCopilot) throws JSONException {
    HashMap<String, ArrayList<String>> stagesAssistants = new HashMap<>();
    loadStagesAssistants(copilotApp, jsonRequestForCopilot, conversationId, stagesAssistants);
    setStages(jsonRequestForCopilot, stagesAssistants);
    //add data for the supervisor
    jsonRequestForCopilot.put(RestServiceUtil.PROP_TEMPERATURE, copilotApp.getTemperature());
    jsonRequestForCopilot.put(RestServiceUtil.PROP_ASSISTANT_ID, copilotApp.getId());
    jsonRequestForCopilot.put(PROP_NAME, copilotApp.getName());
    jsonRequestForCopilot.put(RestServiceUtil.PROP_SYSTEM_PROMPT, copilotApp.getPrompt());
    jsonRequestForCopilot.put(RestServiceUtil.PROP_TOOLS, ToolsUtil.getToolSet(copilotApp));
    jsonRequestForCopilot.put(PROP_NAME, copilotApp.getName());
    jsonRequestForCopilot.put(RestServiceUtil.PROP_MODEL, CopilotModelUtils.getAppModel(copilotApp));
  }

  /**
   * This method is used to load the stages and their associated assistants for a given CopilotApp instance.
   * It iterates over the team members of the CopilotApp instance and creates a JSON object for each one.
   * Each team member JSON object contains the name of the team member and the type of the assistant.
   * The team member JSON objects are added to a JSON array, which is then added to the request JSON object under
   * the key "assistants".
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
          String assistantId = teamMember.getOpenaiAssistantID();
          if (StringUtils.isEmpty(assistantId)) {
            throw new OBException(
                String.format(OBMessageUtils.messageBD("ETCOP_ErrTeamMembNotSync"), teamMember.getName()));
          }
          memberData.put(RestServiceUtil.PROP_ASSISTANT_ID, assistantId);
        } else if (StringUtils.equalsIgnoreCase(teamMember.getAppType(), CopilotConstants.APP_TYPE_LANGCHAIN)
            || StringUtils.equalsIgnoreCase(teamMember.getAppType(), CopilotConstants.APP_TYPE_MULTIMODEL)) {
          buildLangchainRequestForCopilot(teamMember, null, memberData, teamMember.getAppType());
        }

        assistantsArray.put(memberData);

      } catch (JSONException | IOException e) {
        RestServiceUtil.log.error(e);
      }
    }
    stagesAssistants.put("stage1", teamMembersIdentifier);
    jsonRequestForCopilot.put("assistants", assistantsArray);
    if (StringUtils.isNotEmpty(conversationId)) {
      jsonRequestForCopilot.put(RestServiceUtil.PROP_HISTORY, TrackingUtil.getHistory(conversationId));
    }
    //prompt of the graph supervisor
    if (StringUtils.isNotEmpty(copilotApp.getPrompt())) {
      jsonRequestForCopilot.put(RestServiceUtil.PROP_SYSTEM_PROMPT, copilotApp.getPrompt());
    }
    //temperature of the graph supervisor
    jsonRequestForCopilot.put(RestServiceUtil.PROP_TEMPERATURE, copilotApp.getTemperature());
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
      for (Map.Entry<String, ArrayList<String>> entry : stagesAssistants.entrySet()) {
        JSONObject stageJson = new JSONObject();
        stageJson.put("name", entry.getKey());
        JSONArray assistants = new JSONArray();
        for (String assistant : entry.getValue()) {
          assistants.put(assistant);
        }
        stageJson.put("assistants", assistants);
        stages.put(stageJson);
      }
      JSONObject graph = new JSONObject();
      graph.put("stages", stages);
      jsonRequestForCopilot.put("graph", graph);
    } catch (JSONException e) {
      RestServiceUtil.log.error(e);
    }
  }

  /**
   * This method is used to build a request for the Langchain assistant.
   * It sets the assistant ID to the ID of the CopilotApp instance and the type of the assistant to "Langchain".
   * If a conversation ID is provided, it adds the conversation history to the request.
   * It also adds the toolset of the CopilotApp instance to the request.
   * Depending on the provider of the CopilotApp instance, it sets the provider and model in the request.
   * If the provider is "OPENAI", it sets the provider to "openai" and the model to the name of the model of the
   * CopilotApp instance.
   * If the provider is "GEMINI", it sets the provider to "gemini" and the model to "gemini-1.5-pro-latest".
   * If the provider is neither "OPENAI" nor "GEMINI", it throws an exception.
   * If the CopilotApp instance has a prompt, it adds the prompt and the content of the app source file with the
   * behaviour "system" to the request.
   *
   * @param copilotApp
   *     The CopilotApp instance for which the request is to be built.
   * @param conversationId
   *     The conversation ID to be used in the request. If it is not empty, the conversation history will be added to
   *     the request.
   * @param jsonRequestForCopilot
   *     The JSONObject to which the request parameters are to be added.
   * @param appType
   *     The type of the assistant, which can be "Langchain" or "Multimodal".
   * @throws JSONException
   *     If an error occurs while processing the JSON data.
   * @throws IOException
   *     If an error occurs while reading the content of the app source file.
   */

  public static void buildLangchainRequestForCopilot(CopilotApp copilotApp, String conversationId,
      JSONObject jsonRequestForCopilot, String appType) throws JSONException, IOException {
    StringBuilder prompt = new StringBuilder();
    prompt.append(copilotApp.getPrompt());
    jsonRequestForCopilot.put(RestServiceUtil.PROP_ASSISTANT_ID, copilotApp.getId());
    jsonRequestForCopilot.put(PROP_NAME, copilotApp.getName());
    jsonRequestForCopilot.put(RestServiceUtil.PROP_TYPE, appType);
    if (StringUtils.isNotEmpty(conversationId)) {
      jsonRequestForCopilot.put(RestServiceUtil.PROP_HISTORY, TrackingUtil.getHistory(conversationId));
    }
    jsonRequestForCopilot.put(RestServiceUtil.PROP_TEMPERATURE, copilotApp.getTemperature());
    jsonRequestForCopilot.put(RestServiceUtil.PROP_TOOLS, ToolsUtil.getToolSet(copilotApp));
    jsonRequestForCopilot.put(RestServiceUtil.PROP_PROVIDER, CopilotModelUtils.getProvider(copilotApp));
    jsonRequestForCopilot.put(RestServiceUtil.PROP_MODEL, CopilotModelUtils.getAppModel(copilotApp));
    jsonRequestForCopilot.put(RestServiceUtil.PROP_CODE_EXECUTION, copilotApp.isCodeInterpreter());
    String orgId = OBContext.getOBContext().getCurrentOrganization().getId();
    jsonRequestForCopilot.put(RestServiceUtil.PROP_KB_VECTORDB_ID, "KB_" + copilotApp.getId() + "_" + orgId);
    jsonRequestForCopilot.put(RestServiceUtil.PROP_KB_SEARCH_K,
        copilotApp.getSearchResultQty() != null ? copilotApp.getSearchResultQty().intValue() : 4);
    String promptApp = getAssistantPrompt(copilotApp);
    if (StringUtils.isNotEmpty(prompt.toString())) {
      checkPromptLength(prompt);
      jsonRequestForCopilot.put(RestServiceUtil.PROP_SYSTEM_PROMPT, promptApp);
    }
    if (StringUtils.isNotEmpty(copilotApp.getDescription())) {
      jsonRequestForCopilot.put(RestServiceUtil.PROP_DESCRIPTION, copilotApp.getDescription());
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

    // Add MCP configurations
    JSONArray mcpConfigurations = MCPUtils.getMCPConfigurations(copilotApp);
    if (mcpConfigurations.length() > 0) {
      jsonRequestForCopilot.put("mcp_servers", mcpConfigurations);
    }
  }

  /**
   * This method checks if the given CopilotApp instance is of type "LANGCHAIN" and has associated team members.
   * It returns true if both conditions are met, otherwise it returns false.
   *
   * @param copilotApp
   *     The CopilotApp instance to be checked.
   * @return A boolean value indicating whether the CopilotApp instance is of type "LANGCHAIN" and has associated team members.
   */
  public static boolean checkIfGraphQuestion(CopilotApp copilotApp) {
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
        Collectors.toList());
  }
}
