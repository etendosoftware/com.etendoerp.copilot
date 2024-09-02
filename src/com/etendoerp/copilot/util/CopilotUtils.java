package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_GEMINI;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_GEMINI_VALUE;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI_VALUE;
import static com.etendoerp.copilot.util.CopilotConstants.isHQLQueryFile;
import static com.etendoerp.copilot.util.OpenAIUtils.deleteFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
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
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.hook.OpenAIPromptHookManager;
import com.etendoerp.copilot.hook.ProcessHQLAppSource;
import com.etendoerp.sequences.dimensions.SequenceDimension;
import com.smf.securewebservices.utils.SecureWebServicesUtils;


public class CopilotUtils {

  public static final HashMap<String, String> PROVIDER_MAP_CODE_NAME = buildProviderCodeMap();
  public static final HashMap<String, String> PROVIDER_MAP_CODE_DEFAULT_PROP = buildProviderCodeDefaulMap();

  private static final Logger log = LogManager.getLogger(OpenAIUtils.class);
  private static final String BOUNDARY = UUID.randomUUID().toString();

  private static HashMap<String, String> buildProviderCodeMap() {
    HashMap<String, String> map = new HashMap<>();
    map.put(PROVIDER_OPENAI_VALUE, PROVIDER_OPENAI);
    map.put(PROVIDER_GEMINI_VALUE, PROVIDER_GEMINI);
    return map;
  }

  private static HashMap<String, String> buildProviderCodeDefaulMap() {
    HashMap<String, String> map = new HashMap<>();
    map.put(PROVIDER_OPENAI, "ETCOP_DefaultModelOpenAI");
    map.put(PROVIDER_GEMINI, "ETCOP_DefaultModelGemini");
    return map;
  }


  /**
   * This method is used to get the provider of a given CopilotApp instance.
   * It first checks if the CopilotApp instance and its provider are not null. If they are not null, it returns the provider of the CopilotApp instance.
   * If the CopilotApp instance or its provider is null, it retrieves the default provider from the system preferences.
   * The default provider is retrieved using the preference key "ETCOP_DefaultProvider".
   * The method uses the OBContext to get the current client, organization, user, and role for retrieving the preference value.
   * The provider code is then retrieved from the PROVIDER_MAP_CODE_NAME map using the provider code as the key.
   * If an exception occurs while executing any of the above steps, it throws an OBException with the message of the exception.
   *
   * @param app
   *     The CopilotApp instance for which the provider is to be retrieved.
   * @return The provider of the CopilotApp instance, or the default provider if the CopilotApp instance or its provider is null.
   * @throws OBException
   *     If an error occurs while retrieving the provider.
   */
  public static String getProvider(CopilotApp app) {
    try {
      String provCode = null;
      if (app != null && StringUtils.isNotEmpty(app.getProvider())) {
        provCode = app.getProvider();
      } else {
        OBContext context = OBContext.getOBContext();
        provCode = Preferences.getPreferenceValue("ETCOP_DefaultProvider", true, context.getCurrentClient(),
            context.getCurrentOrganization(), context.getUser(), context.getRole(), null);
      }

      return PROVIDER_MAP_CODE_NAME.get(provCode);
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }

  /**
   * This method is used to get the model of a given CopilotApp instance.
   * It calls the overloaded getAppModel method with the CopilotApp instance and its provider as arguments.
   * The provider of the CopilotApp instance is retrieved using the getProvider method.
   * If an exception occurs while getting the model or the provider, it throws an OBException with the message of the exception.
   *
   * @param app
   *     The CopilotApp instance for which the model is to be retrieved.
   * @return The model of the CopilotApp instance.
   * @throws OBException
   *     If an error occurs while retrieving the model or the provider.
   */
  public static String getAppModel(CopilotApp app) {
    try {
      String model = getAppModel(app, getProvider(app));
      logIfDebug("Selected model: " + model);
      return model;
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }


  /**
   * This method is used to get the model of a given CopilotApp instance and a provider.
   * It first checks if the model and its search key of the CopilotApp instance are not null. If they are not null, it returns the search key of the model.
   * If the model or its search key is null, it retrieves the provider of the CopilotApp instance if the provider argument is null.
   * The provider of the CopilotApp instance is retrieved using the getProvider method.
   * It then checks if the provider is in the PROVIDER_MAP_CODE_DEFAULT_PROP map, and sets the preference accordingly.
   * If the provider is not in the map, it throws an OBException with a formatted message.
   * The preference value is then retrieved using the Preferences.getPreferenceValue method with the preference, the current client, organization, user, and role.
   * If an exception occurs while executing any of the above steps, it throws an OBException with the message of the exception.
   *
   * @param app
   *     The CopilotApp instance for which the model is to be retrieved.
   * @param provider
   *     The provider for which the model is to be retrieved.
   * @return The model of the CopilotApp instance, or the preference value if the model or its search key is null.
   * @throws OBException
   *     If an error occurs while retrieving the model, the provider, or the preference value.
   */
  public static String getAppModel(CopilotApp app, String provider) {
    try {
      String current_provider = provider;
      if (app.getModel() != null && app.getModel().getSearchkey() != null) {
        return app.getModel().getSearchkey();
      }
      // if the provider is not indicated we will read the provider of the app ( or the default if not set)
      OBContext context = OBContext.getOBContext();
      if (current_provider == null) {
        current_provider = getProvider(app);
      }
      String preference;
      if (!PROVIDER_MAP_CODE_DEFAULT_PROP.containsKey(current_provider)) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MissingModel"), app.getName()));
      }
      preference = PROVIDER_MAP_CODE_DEFAULT_PROP.get(current_provider);

      return Preferences.getPreferenceValue(preference, true, context.getCurrentClient(),
          context.getCurrentOrganization(), context.getUser(), context.getRole(), null);

    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }


  public static void toVectorDB(String content, File fileToSend, String dbName, String format,
      boolean isBinary) throws JSONException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String endpoint = "addToVectorDB";
    HttpResponse<String> responseFromCopilot;
    JSONObject jsonRequestForCopilot = new JSONObject();
    jsonRequestForCopilot.put("text", content);
    jsonRequestForCopilot.put("kb_vectordb_id", dbName);
    jsonRequestForCopilot.put("extension", format);
    jsonRequestForCopilot.put("overwrite", false);

    responseFromCopilot = getResponseFromCopilot(properties, endpoint, jsonRequestForCopilot,
        isBinary ? fileToSend : null);

    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200 || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_Error_sync_vectorDB")));
    }
  }


  private static HttpResponse<String> getResponseFromCopilot(Properties properties, String endpoint,
      JSONObject jsonBody, File fileToSend) {

    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
      String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");

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
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  private static HttpRequest.BodyPublisher createMultipartBody(JSONObject jsonBody, File file) throws Exception {
    var byteArrays = new ByteArrayOutputStream();
    var writer = new PrintWriter(new OutputStreamWriter(byteArrays, StandardCharsets.UTF_8), true);

    String kb_vectordb_id = jsonBody.getString("kb_vectordb_id");
    String text = jsonBody.optString("text", null);
    String extension = jsonBody.getString("extension");
    boolean overwrite = jsonBody.optBoolean("overwrite", false);

    writer.append("--").append(BOUNDARY).append("\r\n");
    writer.append("Content-Disposition: form-data; name=\"kb_vectordb_id\"\r\n\r\n");
    writer.append(kb_vectordb_id).append("\r\n");

    if (text != null) {
      writer.append("--").append(BOUNDARY).append("\r\n");
      writer.append("Content-Disposition: form-data; name=\"text\"\r\n\r\n");
      writer.append(text).append("\r\n");
    }

    writer.append("--").append(BOUNDARY).append("\r\n");
    writer.append("Content-Disposition: form-data; name=\"extension\"\r\n\r\n");
    writer.append(extension).append("\r\n");

    writer.append("--").append(BOUNDARY).append("\r\n");
    writer.append("Content-Disposition: form-data; name=\"overwrite\"\r\n\r\n");
    writer.append(String.valueOf(overwrite)).append("\r\n");
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

    jsonRequestForCopilot.put("kb_vectordb_id", dbName);
    String endpoint = "ResetVectorDB";
    HttpResponse<String> responseFromCopilot = getResponseFromCopilot(properties, endpoint, jsonRequestForCopilot,
        null);
    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200 || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB"), app.getName(),
          responseFromCopilot != null ? responseFromCopilot.body() : ""));
    }

  }


  private static boolean fileHasChanged(CopilotFile fileToSync) {

    Date lastSyncDate = fileToSync.getLastSync();
    if (lastSyncDate == null) {
      return true;
    }
    Date updated = fileToSync.getUpdated();
    //clean the milliseconds
    lastSyncDate = new Date(lastSyncDate.getTime() / 1000 * 1000);
    updated = new Date(updated.getTime() / 1000 * 1000);

    if ((updated.after(lastSyncDate))) {
      return true;
    }
    //check Attachments
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
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttach"), fileToSync.getName()));
    }
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    aim.download(attach.getId(), os);
    //create a temp file
    String filename = attach.getName();
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

  private static String fileToBase64(File fileInPDF) {
    try {
      FileInputStream fileInputStream = new FileInputStream(fileInPDF);
      byte[] fileBytes = new byte[(int) fileInPDF.length()];
      fileInputStream.read(fileBytes);
      fileInputStream.close();

      String base64EncodedFile = Base64.getEncoder().encodeToString(fileBytes);

      return base64EncodedFile;

    } catch (IOException e) {
      return null;
    }
  }

  private static String readFileToSync(File file) throws IOException {

    String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
    StringBuilder text = new StringBuilder(extension + "-");
    try (FileInputStream fis = new FileInputStream(file); InputStreamReader isr = new InputStreamReader(
        fis); BufferedReader br = new BufferedReader(isr)) {

      String line;
      while ((line = br.readLine()) != null) {
        text.append(line).append("\n");
      }
    }
    return text.toString();
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

    if (StringUtils.isEmpty(extension)) {
      extension = fileFromCopilotFile.getName().substring(fileFromCopilotFile.getName().lastIndexOf(".") + 1);
    }

    if (StringUtils.equalsIgnoreCase(extension, "pdf")) {
      binaryFileToVectorDB(fileFromCopilotFile, dbName, extension);

    } else if (StringUtils.equalsIgnoreCase(extension, "md") || (StringUtils.equalsIgnoreCase(extension, "markdown"))) {
      notEncodedFileToVectorDB(fileFromCopilotFile, dbName, extension);

    } else if (StringUtils.equalsIgnoreCase(extension, "txt")) {
      notEncodedFileToVectorDB(fileFromCopilotFile, dbName, extension);

    } else if (StringUtils.equalsIgnoreCase(extension, "zip")) {
      binaryFileToVectorDB(fileFromCopilotFile, dbName, extension);

    } else if (StringUtils.equalsIgnoreCase(extension, "java")) {
      notEncodedFileToVectorDB(fileFromCopilotFile, dbName, extension);

    } else if (StringUtils.equalsIgnoreCase(extension, "py")) {
      notEncodedFileToVectorDB(fileFromCopilotFile, dbName, extension);

    } else if (StringUtils.equalsIgnoreCase(extension, "js")) {
      notEncodedFileToVectorDB(fileFromCopilotFile, dbName, extension);

    } else if (StringUtils.equalsIgnoreCase(extension, "xml")) {
      notEncodedFileToVectorDB(fileFromCopilotFile, dbName, extension);

    } else {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorInvalidFormat"), extension));
    }

    fileToSync.setLastSync(new Date());
    fileToSync.setUpdated(new Date());
    OBDal.getInstance().save(fileToSync);
    OBDal.getInstance().flush();
  }

  private static void notEncodedFileToVectorDB(File fileFromCopilotFile, String dbName,
      String extension) throws IOException, JSONException {
    String text = readFileToSync(fileFromCopilotFile);
    toVectorDB(text, fileFromCopilotFile, dbName, extension, false);
  }

  private static void binaryFileToVectorDB(File fileFromCopilotFile, String dbName,
      String extension) throws JSONException {
    toVectorDB(null, fileFromCopilotFile, dbName, extension, true);
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

  public static void checkPromptLength(StringBuilder prompt) {
    if (prompt.length() > CopilotConstants.LANGCHAIN_MAX_LENGTH_PROMPT) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MaxLengthPrompt")));
    }
  }

  public static String getAssistantPrompt(CopilotApp app) {
    StringBuilder sb = new StringBuilder();
    sb.append(app.getPrompt());
    sb.append("\n");
    try {
      sb.append(WeldUtils.getInstanceFromStaticBeanManager(OpenAIPromptHookManager.class).executeHooks(app));
    } catch (OBException e) {
      log.error("Error executing hooks", e);
    }
    //
    sb.append(getAppSourceContent(app, CopilotConstants.FILE_BEHAVIOUR_SYSTEM));

    return replaceCopilotPromptVariables(sb.toString());
  }

  /**
   * This method is used to replace a specific placeholder in a string with the host name of Etendo.
   * The placeholder is "@ETENDO_HOST@" and it is replaced with the value returned by the getEtendoHost() method.
   *
   * @param string
   *     The string in which the placeholder is to be replaced. It is expected to contain "@ETENDO_HOST@".
   * @return The string with the placeholder "@ETENDO_HOST@" replaced by the host name of Etendo.
   */
  public static String replaceCopilotPromptVariables(String string) {
    String stringParsed = StringUtils.replace(string, "@ETENDO_HOST@", getEtendoHost());
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    stringParsed = StringUtils.replace(stringParsed, "@sources.path@", properties.getProperty("source.path"));

    //check the If exists something like {SOMETHING} and replace it with {{SOMETHING}}, preserving the content inside
    // replace { with {{
    Pattern pattern = Pattern.compile("\\{");
    Matcher matcher = pattern.matcher(stringParsed);
    stringParsed = matcher.replaceAll("\\{\\{");
    // replace } with }}
    pattern = Pattern.compile("\\}");
    matcher = pattern.matcher(stringParsed);
    stringParsed = matcher.replaceAll("\\}\\}");
    // check that the result is correctly balanced
    if (StringUtils.countMatches(stringParsed, "{{") != StringUtils.countMatches(stringParsed, "}}")) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_BalancedBrackets")));
    }

    return stringParsed;
  }

  /**
   * This method retrieves the host name of Etendo from the system properties.
   * It uses the key "ETENDO_HOST" to fetch the value from the properties.
   * If the key is not found in the properties, it retu rns "ERROR" as a default value.
   *
   * @return The host name of Etendo if found, otherwise "ERROR".
   */
  private static String getEtendoHost() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    return properties.getProperty("ETENDO_HOST", "ETENDO_HOST_NOT_CONFIGURED");
  }

  public static String getAppSourceContent(CopilotApp copilotApp, String type) {
    StringBuilder content = new StringBuilder();
    for (CopilotAppSource appSource : copilotApp.getETCOPAppSourceList()) {
      if (StringUtils.equals(appSource.getBehaviour(), type) && appSource.getFile() != null) {
        try {
          File tempFile;
          if (CopilotConstants.isFileTypeLocalOrRemoteFile(appSource.getFile())) {
            tempFile = getFileFromCopilotFile(appSource.getFile());
          } else {
            tempFile = ProcessHQLAppSource.getInstance().generate(appSource);
          }
          content.append("\n---\n");
          content.append(appSource.getFile().getName()).append("\n");
          content.append(Files.readString(tempFile.toPath())).append("\n");
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

  public static String generateEtendoToken() throws Exception {

    OBContext context = OBContext.getOBContext();
    Organization currentOrganization = OBDal.getInstance().get(Organization.class,
        context.getCurrentOrganization().getId());
    Role role = OBDal.getInstance().get(Role.class, context.getRole().getId());
    User user = OBDal.getInstance().get(User.class, context.getUser().getId());
    Warehouse warehouse = OBDal.getInstance().get(Warehouse.class, context.getWarehouse().getId());
    return SecureWebServicesUtils.generateToken(user, role, currentOrganization,
        warehouse);
  }
}
