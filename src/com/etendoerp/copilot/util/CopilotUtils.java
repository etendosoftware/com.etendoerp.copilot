package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_GEMINI;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_GEMINI_VALUE;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI_VALUE;
import static com.etendoerp.copilot.util.CopilotConstants.isHQLQueryFile;
import static com.etendoerp.copilot.util.OpenAIUtils.HEADER_CONTENT_TYPE;
import static com.etendoerp.copilot.util.OpenAIUtils.deleteFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
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
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.hook.OpenAIPromptHookManager;
import com.etendoerp.copilot.hook.ProcessHQLAppSource;

public class CopilotUtils {

  public static final HashMap<String, String> PROVIDER_MAP_CODE_NAME = buildProviderCodeMap();
  public static final HashMap<String, String> PROVIDER_MAP_CODE_DEFAULT_PROP = buildProviderCodeDefaulMap();

  private static final Logger log = LogManager.getLogger(OpenAIUtils.class);

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
        throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MissingModel"),
            app.getName()));
      }
      preference = PROVIDER_MAP_CODE_DEFAULT_PROP.get(current_provider);

      return Preferences.getPreferenceValue(preference, true, context.getCurrentClient(),
          context.getCurrentOrganization(), context.getUser(), context.getRole(), null);

    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }


  public static void textToVectorDB(String text, String dbName, String format) throws JSONException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    JSONObject jsonRequestForCopilot = new JSONObject();
    jsonRequestForCopilot.put("text", text);
    jsonRequestForCopilot.put("kb_vectordb_id", dbName);
    jsonRequestForCopilot.put("format", format);
    String requestBody = jsonRequestForCopilot.toString();

    String endpoint = "addToVectorDB";

    HttpResponse<String> responseFromCopilot = getResponseFromCopilot(properties, endpoint, requestBody);
    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200 || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_Error_sync_vectorDB")));
    }

  }

  private static HttpResponse<String> getResponseFromCopilot(Properties properties, String endpoint,
      String requestBody
  ) {

    try {
      HttpClient client = HttpClient.newBuilder().build();
      String copilotPort = properties.getProperty("COPILOT_PORT", "5005");
      String copilotHost = properties.getProperty("COPILOT_HOST", "localhost");
      HttpRequest copilotRequest = HttpRequest.newBuilder()
          .uri(new URI(String.format("http://%s:%s/%s", copilotHost, copilotPort, endpoint)))
          .headers(HEADER_CONTENT_TYPE, "application/json;charset=UTF-8")
          .version(HttpClient.Version.HTTP_1_1)
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build();

      return client.send(copilotRequest,
          HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      throw new OBException(e);
    }

  }

  public static void resetVectorDB(CopilotApp app) throws JSONException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String dbName = "KB_" + app.getId();
    JSONObject jsonRequestForCopilot = new JSONObject();

    jsonRequestForCopilot.put("kb_vectordb_id", dbName);
    String requestBody = jsonRequestForCopilot.toString();
    String endpoint = "ResetVectorDB";
    HttpResponse<String> responseFromCopilot = getResponseFromCopilot(properties, endpoint, requestBody);
    if (responseFromCopilot == null || responseFromCopilot.statusCode() < 200 || responseFromCopilot.statusCode() >= 300) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorResetVectorDB"), app.getName(),
          responseFromCopilot.body()));
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
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(
        AttachImplementationManager.class);
    OBCriteria<Attachment> attCrit = OBDal.getInstance().createCriteria(Attachment.class);
    attCrit.add(Restrictions.eq(Attachment.PROPERTY_RECORD, fileToSync.getId()));
    Attachment attach = (Attachment) attCrit.setMaxResults(1).uniqueResult();
    if (attach == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttach"),
          fileToSync.getName()));
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
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorTempFile"),
          fileToSync.getName()));
    }
    tempFile.deleteOnExit();
    os.writeTo(new FileOutputStream(tempFile));
    return tempFile;
  }

  private static String pdfToBase64(File fileInPDF) {
    try {
      FileInputStream fileInputStream = new FileInputStream(fileInPDF);
      byte[] fileBytes = new byte[(int) fileInPDF.length()];
      fileInputStream.read(fileBytes);
      fileInputStream.close();

      String base64EncodedPDF = Base64.getEncoder().encodeToString(fileBytes);

      return base64EncodedPDF;

    } catch (IOException e) {
      return null;
    }
  }

  private static String readFileToSync(File file)
      throws IOException {

    String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
    StringBuilder text = new StringBuilder(extension + "-");
    try (FileInputStream fis = new FileInputStream(file);
         InputStreamReader isr = new InputStreamReader(fis);
         BufferedReader br = new BufferedReader(isr)) {

      String line;
      while ((line = br.readLine()) != null) {
        text.append(line).append("\n");
      }
    }
    return text.toString();
  }

  public static void syncAppLangchainSource(CopilotAppSource appSource)
      throws IOException, JSONException {

    CopilotFile fileToSync = appSource.getFile();
    WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class)
        .executeHooks(fileToSync);
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
      String text = pdfToBase64(fileFromCopilotFile);
      textToVectorDB(text, dbName, extension);
    } else if (StringUtils.equalsIgnoreCase(extension, "md")) {
      String text = readFileToSync(fileFromCopilotFile);
      textToVectorDB(text, dbName, extension);

    } else if (StringUtils.equalsIgnoreCase(extension, "txt")) {
      String text = readFileToSync(fileFromCopilotFile);
      textToVectorDB(text, dbName, extension);
    } else {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorInvalidFormat"),
          extension));
    }

    fileToSync.setLastSync(new Date());
    fileToSync.setUpdated(new Date());
    OBDal.getInstance().save(fileToSync);
    OBDal.getInstance().flush();
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
      sb.append(WeldUtils.getInstanceFromStaticBeanManager(OpenAIPromptHookManager.class)
          .executeHooks(app));
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


    System.out.println("-------------");
    System.out.println(stringParsed);
    System.out.println("-------------");
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
          throw new OBException(
              String.format(OBMessageUtils.messageBD("ETCOP_Error_MalformedSourceContent"),
                  appSource.getFile().getName(), appSource.getEtcopApp().getName()));
        } catch (IOException e) {
          log.error(e);
          throw new OBException(e);
        }
      }
    }
    return content.toString();
  }

}
