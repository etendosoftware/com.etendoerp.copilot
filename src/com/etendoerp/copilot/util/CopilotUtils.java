package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.rest.RestServiceUtil.replaceAliasInPrompt;
import static com.etendoerp.copilot.util.CopilotConstants.KB_FILE_VALID_EXTENSIONS;
import static com.etendoerp.copilot.util.CopilotConstants.isHQLQueryFile;
import static com.etendoerp.copilot.util.OpenAIUtils.deleteFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotModel;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.hook.OpenAIPromptHookManager;
import com.etendoerp.copilot.hook.ProcessHQLAppSource;
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
    String dbName = "KB_" + app.getId();
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
   * Retrieves a file associated with the given {@link CopilotFile} instance.
   * <p>
   * This method fetches the attachment corresponding to the provided {@link CopilotFile},
   * downloads its content, and creates a temporary file with the same name and extension.
   * If the attachment is missing or the file lacks an extension, appropriate exceptions
   * are thrown. The temporary file is marked for deletion upon JVM exit.
   *
   * @param fileToSync
   *     The {@link CopilotFile} instance for which the associated file is to be retrieved.
   * @return A {@link File} object representing the temporary file created from the attachment.
   * @throws IOException
   *     If an I/O error occurs during file creation or writing.
   * @throws OBException
   *     If the attachment is missing, the file lacks an extension, or the temporary file
   *     cannot be made writable.
   */
  public static File getFileFromCopilotFile(CopilotFile fileToSync) throws IOException {
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
    OBCriteria<Attachment> attCrit = OBDal.getInstance().createCriteria(Attachment.class);
    attCrit.add(Restrictions.eq(Attachment.PROPERTY_RECORD, fileToSync.getId()));
    Attachment attach = (Attachment) attCrit.setMaxResults(1).uniqueResult();
    if (attach == null) {
      throwMissingAttachException(fileToSync);
    }
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    aim.download(attach.getId(), os);
    // create a temp file
    String filename = attach.getName();
    if (filename.lastIndexOf(".") < 0) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingExtension"), filename));
    }

    String fileWithoutExtension = filename.substring(0, filename.lastIndexOf("."));
    String extension = filename.substring(filename.lastIndexOf(".") + 1);

    File tempFile = File.createTempFile(fileWithoutExtension, "." + extension);
    boolean setW = tempFile.setWritable(true);
    if (!setW) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorTempFile"), fileToSync.getName()));
    }
    tempFile.deleteOnExit();
    os.writeTo(new FileOutputStream(tempFile));
    return tempFile;
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

      fileFromCopilotFile = generateHQLFile(appSource);

    } else {
      // Retrieve the file for non-HQL query files
      fileFromCopilotFile = getFileFromCopilotFile(fileToSync);
    }

    // Prepare database and synchronization parameters
    String dbName = "KB_" + appSource.getEtcopApp().getId();
    boolean skipSplitting = appSource.getFile().isSkipSplitting();
    Long maxChunkSize = appSource.getFile().getMaxChunkSize();
    Long chunkOverlap = appSource.getFile().getChunkOverlap();

    // Validate and determine the file extension
    if (StringUtils.isEmpty(extension)) {
      extension = fileFromCopilotFile.getName().substring(fileFromCopilotFile.getName().lastIndexOf(".") + 1);
    }

    // Check if the file extension is valid
    if (isValidExtension(extension)) {
      // Upload the file to the vector database
      binaryFileToVectorDB(fileFromCopilotFile, dbName, extension, skipSplitting, maxChunkSize, chunkOverlap);
    } else {
      // Throw an exception for invalid file formats
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorInvalidFormat"), extension));
    }

    // Update synchronization metadata
    fileToSync.setLastSync(new Date());
    fileToSync.setUpdated(new Date());
    OBDal.getInstance().save(fileToSync);
    OBDal.getInstance().flush();
  }

  /**
   * Checks if the given file extension is valid.
   * This method compares the provided extension against a list of valid
   * extensions
   * defined in the KB\_FILE\_VALID\_EXTENSIONS constant.
   *
   * @param extension
   *     The file extension to be checked.
   * @return true if the extension is valid, false otherwise.
   */
  private static boolean isValidExtension(String extension) {
    return Arrays.stream(KB_FILE_VALID_EXTENSIONS).anyMatch(
        validExt -> StringUtils.equalsIgnoreCase(validExt, extension));
  }

  static void binaryFileToVectorDB(File fileFromCopilotFile, String dbName, String extension, boolean skipSplitting,
      Long maxChunkSize, Long chunkOverlap) throws JSONException {
    toVectorDB(fileFromCopilotFile, dbName, extension, skipSplitting, maxChunkSize, chunkOverlap);
  }

  static File generateHQLFile(CopilotAppSource appSource) {
    String fileNameToCheck = ProcessHQLAppSource.getFileName(appSource);
    if (StringUtils.equalsIgnoreCase("kb", appSource.getBehaviour()) && (StringUtils.isEmpty(
        fileNameToCheck) || StringUtils.endsWithIgnoreCase(fileNameToCheck, ".csv"))) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_Error_Csv_KB"), appSource.getFile().getName()));
    }

    return ProcessHQLAppSource.getInstance().generate(appSource);
  }

  /**
   * Throws an OBException indicating that an attachment is missing.
   * This method checks the type of the CopilotFile and throws an exception with a
   * specific error message
   * based on whether the file type is attached or not.
   *
   * @param fileToSync
   *     The CopilotFile instance for which the attachment is
   *     missing.
   * @throws OBException
   *     Always thrown to indicate the missing attachment.
   */
  public static void throwMissingAttachException(CopilotFile fileToSync) {
    String errMsg;
    String type = fileToSync.getType();
    if (StringUtils.equalsIgnoreCase(type, CopilotConstants.KBF_TYPE_ATTACHED)) {
      errMsg = String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttach"), fileToSync.getName());
    } else {
      errMsg = String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttachSync"), fileToSync.getName());
    }
    throw new OBException(errMsg);
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

    return replaceCopilotPromptVariables(promptBuilder.toString());
  }

  /**
   * Replaces Copilot prompt variables in the given string.
   * <p>
   * This method replaces placeholders in the provided string with their corresponding
   * values. It uses a default empty {@link JSONObject} for variable mappings.
   * If an error occurs during the replacement process, a {@link RuntimeException} is thrown.
   *
   * @param string
   *     The input string containing placeholders to be replaced.
   * @return A {@link String} with the placeholders replaced by their corresponding values.
   * @throws RuntimeException
   *     If a {@link JSONException} occurs during the replacement process.
   */
  public static String replaceCopilotPromptVariables(String string) {
    try {
      return replaceCopilotPromptVariables(string, new JSONObject());
    } catch (JSONException e) {
      throw new OBException(e);
    }
  }

  /**
   * This method is used to replace a specific placeholder in a string with the
   * host name of Etendo.
   * The placeholder is "@ETENDO_HOST@" and it is replaced with the value returned
   * by the getEtendoHost() method.
   *
   * @param string
   *     The string in which the placeholder is to be replaced. It is
   *     expected to contain "@ETENDO_HOST@".
   * @param maps
   *     A JSONObject containing key-value pairs to replace in the
   *     string.
   * @return The string with the placeholder "@ETENDO_HOST@" replaced by the host
   *     name of Etendo.
   * @throws JSONException
   *     If an error occurs while parsing the JSON object.
   */
  public static String replaceCopilotPromptVariables(String string, JSONObject maps) throws JSONException {
    OBContext obContext = OBContext.getOBContext();
    String stringParsed = StringUtils.replace(string, "@ETENDO_HOST@", getEtendoHost());
    stringParsed = StringUtils.replace(stringParsed, "@ETENDO_HOST_DOCKER@", getEtendoHostDocker());
    if (obContext.getCurrentClient() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@AD_CLIENT_ID@", obContext.getCurrentClient().getId());
    }
    if (obContext.getCurrentClient() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@CLIENT_NAME@", obContext.getCurrentClient().getName());
    }
    if (obContext.getCurrentOrganization() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@AD_ORG_ID@", obContext.getCurrentOrganization().getId());
    }
    if (obContext.getCurrentOrganization() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@ORG_NAME@", obContext.getCurrentOrganization().getName());
    }
    if (obContext.getUser() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@AD_USER_ID@", obContext.getUser().getId());
      stringParsed = StringUtils.replace(stringParsed, "@USERNAME@", obContext.getUser().getUsername());
    }
    if (obContext.getRole() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@AD_ROLE_ID@", obContext.getRole().getId());
      stringParsed = StringUtils.replace(stringParsed, "@ROLE_NAME@", obContext.getRole().getName());
    }
    if (obContext.getWarehouse() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@M_WAREHOUSE_ID@", obContext.getWarehouse().getId());
      stringParsed = StringUtils.replace(stringParsed, "@WAREHOUSE_NAME@", obContext.getWarehouse().getName());
    }
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    stringParsed = StringUtils.replace(stringParsed, "@source.path@", getSourcesPath(properties));

    if (maps != null) {
      Map<String, String> replacements = new HashMap<>();
      Iterator<String> keys = maps.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        Object value = maps.get(key);
        if (value instanceof String || value instanceof Boolean) {
          replacements.put(key, value.toString());
        }
      }
      StrSubstitutor sub = new StrSubstitutor(replacements);
      stringParsed = sub.replace(stringParsed);
    }

    stringParsed = stringParsed.replace("{", "{{").replace("}", "}}");

    if (StringUtils.countMatches(stringParsed, "{{") != StringUtils.countMatches(stringParsed, "}}")) {
      throw new OBException(OBMessageUtils.messageBD("ETCOP_BalancedBrackets"));
    }

    return stringParsed;
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
   * Otherwise, the file is retrieved using the {@link #getFileFromCopilotFile(CopilotFile)} method.
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
      tempFile = getFileFromCopilotFile(appSource.getFile());
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
    String dbName = "KB_" + app.getId();
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
   * Retrieves an attachment associated with the given CopilotFile instance.
   * <p>
   * This method creates a criteria query to find an attachment that matches the
   * given CopilotFile instance.
   * It filters the attachments by the record ID and table ID, and excludes the
   * attachment with the same ID as the target instance.
   *
   * @param targetInstance
   *     The CopilotFile instance for which the attachment is to
   *     be retrieved.
   * @return The Attachment associated with the given CopilotFile instance, or
   *     null if no attachment is found.
   */
  public static Attachment getAttachment(CopilotFile targetInstance) {
    OBCriteria<Attachment> attchCriteria = OBDal.getInstance().createCriteria(Attachment.class);
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, targetInstance.getId()));
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE,
        OBDal.getInstance().get(Table.class, CopilotConstants.COPILOT_FILE_AD_TABLE_ID)));
    attchCriteria.add(Restrictions.ne(Attachment.PROPERTY_ID, targetInstance.getId()));
    return (Attachment) attchCriteria.setMaxResults(1).uniqueResult();
  }

  /**
   * Attaches a file to the given CopilotFile instance.
   * <p>
   * This method uploads a file and associates it with the specified CopilotFile
   * instance.
   *
   * @param hookObject
   *     The CopilotFile instance to which the file is to be
   *     attached.
   * @param aim
   *     The AttachImplementationManager used to handle the file
   *     upload.
   * @param file
   *     The file to be attached.
   */
  public static void attachFile(CopilotFile hookObject, AttachImplementationManager aim, File file) {
    aim.upload(new HashMap<>(), CopilotConstants.COPILOT_FILE_TAB_ID, hookObject.getId(),
        hookObject.getOrganization().getId(), file);
  }

  /**
   * Removes the attachment associated with the given CopilotFile instance.
   * <p>
   * This method retrieves the attachment associated with the specified
   * CopilotFile instance and deletes it.
   *
   * @param aim
   *     The AttachImplementationManager used to handle the file
   *     deletion.
   * @param hookObject
   *     The CopilotFile instance whose attachment is to be removed.
   */
  public static void removeAttachment(AttachImplementationManager aim, CopilotFile hookObject) {
    Attachment attachment = CopilotUtils.getAttachment(hookObject);
    if (attachment != null) {
      aim.delete(attachment);
    }
  }

}
