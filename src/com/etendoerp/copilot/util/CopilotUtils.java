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

public class CopilotUtils {


  private static final Logger log = LogManager.getLogger(CopilotUtils.class);
  private static final String BOUNDARY = UUID.randomUUID().toString();
  public static final String KB_VECTORDB_ID = "kb_vectordb_id";
  public static final String COPILOT_PORT = "COPILOT_PORT";
  public static final String COPILOT_HOST = "COPILOT_HOST";
  private static final String DEFAULT_PROMPT_PREFERENCE_KEY = "ETCOP_DefaultContextPrompt";
  public static final String DEFAULT_MODELS_DATASET_URL = "https://raw.githubusercontent.com/etendosoftware/com.etendoerp.copilot/refs/heads/<BRANCH>/referencedata/standard/AI_Models_Dataset.xml";


  public static void toVectorDB(String content, File fileToSend, String dbName, String format,
      boolean isBinary, boolean skipSplitting) throws JSONException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String endpoint = "addToVectorDB";
    HttpResponse<String> responseFromCopilot;
    JSONObject jsonRequestForCopilot = new JSONObject();
    jsonRequestForCopilot.put("filename", fileToSend.getName());
    jsonRequestForCopilot.put(KB_VECTORDB_ID, dbName);
    jsonRequestForCopilot.put("extension", format);
    jsonRequestForCopilot.put("overwrite", false);
    jsonRequestForCopilot.put("skip_splitting", skipSplitting);

    responseFromCopilot = getResponseFromCopilot(properties, endpoint, jsonRequestForCopilot, fileToSend);

    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200
        || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_Error_sync_vectorDB")));
    }
  }

  public static HttpResponse<String> getResponseFromCopilot(Properties properties, String endpoint,
      JSONObject jsonBody, File fileToSend) {

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

      HttpRequest copilotRequest = HttpRequest.newBuilder()
          .uri(new URI(String.format("http://%s:%s/%s", copilotHost, copilotPort, endpoint)))
          .header("Content-Type", contentType)
          .version(HttpClient.Version.HTTP_1_1)
          .POST(requestBodyPublisher)
          .build();

      return client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OBException(e);
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  private static HttpResponse<String> doGetCopilot(Properties properties, String endpoint) {
    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty(COPILOT_PORT, "5005");
      String copilotHost = properties.getProperty(COPILOT_HOST, "localhost");

      HttpRequest copilotRequest = HttpRequest.newBuilder()
          .uri(new URI(String.format("http://%s:%s/%s", copilotHost, copilotPort, endpoint)))
          .GET()
          .build();

      return client.send(copilotRequest, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OBException(e);
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  private static HttpRequest.BodyPublisher createMultipartBody(JSONObject jsonBody, File file) throws Exception {
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

  public static void resetVectorDB(CopilotApp app) throws JSONException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String dbName = "KB_" + app.getId();
    JSONObject jsonRequestForCopilot = new JSONObject();

    jsonRequestForCopilot.put(KB_VECTORDB_ID, dbName);
    String endpoint = "ResetVectorDB";
    HttpResponse<String> responseFromCopilot = getResponseFromCopilot(properties, endpoint, jsonRequestForCopilot,
        null);
    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200
        || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB"), app.getName(),
          responseFromCopilot != null ? responseFromCopilot.body() : ""));
    }

  }

  public static void logIfDebug(String text) {
    if (log.isDebugEnabled()) {
      log.debug(text);
    }
  }

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

  public static void syncAppLangchainSource(CopilotAppSource appSource) throws IOException, JSONException {

    CopilotFile fileToSync = appSource.getFile();
    WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class).executeHooks(fileToSync);
    logIfDebug("Uploading file " + fileToSync.getName());

    String filename = fileToSync.getFilename();

    String extension = StringUtils.isNotEmpty(filename) ? filename.substring(filename.lastIndexOf(".") + 1) : null;

    File fileFromCopilotFile = null;
    if (isHQLQueryFile(fileToSync)) {
      String openaiFileId = appSource.getOpenaiIdFile();
      if (StringUtils.isNotEmpty(openaiFileId)) {
        logIfDebug("Deleting file " + appSource.getFile().getName());
        deleteFile(appSource.getOpenaiIdFile(), OpenAIUtils.getOpenaiApiKey());
      }

      fileFromCopilotFile = generateHQLFile(appSource);

    } else {
      fileFromCopilotFile = getFileFromCopilotFile(fileToSync);
    }

    String dbName = "KB_" + appSource.getEtcopApp().getId();
    boolean skipSplitting = appSource.getFile().isSkipSplitting();

    if (StringUtils.isEmpty(extension)) {
      extension = fileFromCopilotFile.getName().substring(fileFromCopilotFile.getName().lastIndexOf(".") + 1);
    }

    if (isValidExtension(extension)) {
      binaryFileToVectorDB(fileFromCopilotFile, dbName, extension, skipSplitting);
    } else {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorInvalidFormat"), extension));
    }

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
    return Arrays.stream(KB_FILE_VALID_EXTENSIONS)
        .anyMatch(validExt -> StringUtils.equalsIgnoreCase(validExt, extension));
  }

  private static void binaryFileToVectorDB(File fileFromCopilotFile, String dbName,
      String extension, boolean skipSplitting) throws JSONException {
    toVectorDB(null, fileFromCopilotFile, dbName, extension, true, skipSplitting);
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
      errMsg = String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttach"),
          fileToSync.getName());
    } else {
      errMsg = String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttachSync"),
          fileToSync.getName());
    }
    throw new OBException(errMsg);
  }

  public static void checkPromptLength(StringBuilder prompt) {
    if (prompt.length() > CopilotConstants.LANGCHAIN_MAX_LENGTH_PROMPT) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MaxLengthPrompt")));
    }
  }

  public static String getAssistantPrompt(CopilotApp app) throws IOException {
    StringBuilder promptBuilder = new StringBuilder();
    promptBuilder.append(app.getPrompt());
    promptBuilder.append("\n");

    try {
      promptBuilder.append(
          WeldUtils.getInstanceFromStaticBeanManager(OpenAIPromptHookManager.class)
              .executeHooks(app));
    } catch (OBException e) {
      log.error("Error executing hooks", e);
    }

    OBContext context = OBContext.getOBContext();
    String defaultContextPrompt = null;

    try {
      if (context.getCurrentClient() != null
          && context.getCurrentOrganization() != null
          && context.getUser() != null
          && context.getRole() != null) {

        defaultContextPrompt = Preferences.getPreferenceValue(
            DEFAULT_PROMPT_PREFERENCE_KEY,
            true,
            context.getCurrentClient().getId(),
            context.getCurrentOrganization().getId(),
            context.getUser().getId(),
            context.getRole().getId(),
            null);
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
    List<CopilotAppSource> appSourcesWithAlias = appSourcesToAppend.stream()
        .filter(appSource -> StringUtils.equalsIgnoreCase(
            appSource.getBehaviour(), CopilotConstants.FILE_BEHAVIOUR_SYSTEM)
            && StringUtils.isNotEmpty(appSource.getAlias()))
        .collect(Collectors.toList());

    // the app sources to append are the ones that are not with an alias
    appSourcesToAppend = appSourcesToAppend.stream()
        .filter(appSource -> !appSourcesWithAlias.contains(appSource))
        .collect(Collectors.toList());

    promptBuilder = replaceAliasInPrompt(promptBuilder, appSourcesWithAlias);

    promptBuilder.append(
        getAppSourceContent(appSourcesToAppend, CopilotConstants.FILE_BEHAVIOUR_SYSTEM));

    return replaceCopilotPromptVariables(promptBuilder.toString());
  }

  public static String replaceCopilotPromptVariables(String string) {
    try {
      return replaceCopilotPromptVariables(string, new JSONObject());
    } catch (JSONException e) {
      throw new RuntimeException(e);
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
   */
  public static String replaceCopilotPromptVariables(String string, JSONObject maps) throws JSONException {
    OBContext obContext = OBContext.getOBContext();
    String stringParsed = StringUtils.replace(string, "@ETENDO_HOST@", getEtendoHost());
    stringParsed = StringUtils.replace(stringParsed, "@ETENDO_HOST_DOCKER@", getEtendoHostDocker());
    stringParsed = StringUtils.replace(stringParsed, "@AD_CLIENT_ID@", obContext.getCurrentClient().getId());
    stringParsed = StringUtils.replace(stringParsed, "@CLIENT_NAME@", obContext.getCurrentClient().getName());
    stringParsed = StringUtils.replace(stringParsed, "@AD_ORG_ID@", obContext.getCurrentOrganization().getId());
    stringParsed = StringUtils.replace(stringParsed, "@ORG_NAME@", obContext.getCurrentOrganization().getName());
    stringParsed = StringUtils.replace(stringParsed, "@AD_USER_ID@", obContext.getUser().getId());
    stringParsed = StringUtils.replace(stringParsed, "@USERNAME@", obContext.getUser().getUsername());
    stringParsed = StringUtils.replace(stringParsed, "@AD_ROLE_ID@", obContext.getRole().getId());
    stringParsed = StringUtils.replace(stringParsed, "@ROLE_NAME@", obContext.getRole().getName());
    stringParsed = StringUtils.replace(stringParsed, "@M_WAREHOUSE_ID@", obContext.getWarehouse().getId());
    stringParsed = StringUtils.replace(stringParsed, "@WAREHOUSE_NAME@", obContext.getWarehouse().getName());

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
  private static String getSourcesPath(Properties properties) {
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

  private static boolean isCopilotRunningInDocker(Properties properties) {
    boolean inDocker = false;
    try {
      var resp = doGetCopilot(properties, "runningCheck");
      inDocker = StringUtils.contains(resp.body(), "docker");
    } catch (Exception e) {
      log.error(OBMessageUtils.messageBD("ETCOP_ErrorRunningCheck"),
          e);// TODO: message like "Error checking if running in Docker, assuming not"
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

  public static String getAppSourceContent(CopilotAppSource appSource) throws IOException {
    File tempFile;
    if (isHQLQueryFile(appSource.getFile())) {
      tempFile = ProcessHQLAppSource.getInstance().generate(appSource);
    } else {
      tempFile = getFileFromCopilotFile(appSource.getFile());
    }
    return Files.readString(tempFile.toPath());
  }

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
    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200
        || responseFromCopilot.statusCode() >= 300) {
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
      Integer max_tokens = model.getMaxTokens() != null ? Math.toIntExact(model.getMaxTokens()) : null;
      if (!modelsConfig.has(provider)) {
        modelsConfig.put(provider, new JSONObject());
      }
      var providerConfig = modelsConfig.getJSONObject(provider);
      if (!providerConfig.has(modelName)) {
        providerConfig.put(modelName, new JSONObject());
      }
      var modelConfig = providerConfig.getJSONObject(modelName);
      modelConfig.put("max_tokens", max_tokens);
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
  private static String getEtendoSWSToken(OBContext context, Role role) throws Exception {
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
