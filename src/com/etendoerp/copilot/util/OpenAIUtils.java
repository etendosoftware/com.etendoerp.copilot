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
import java.util.function.Predicate;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotModel;
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

  public static void syncAssistant(String openaiApiKey, CopilotApp app) throws OBException {
    //first we need to get the assistant
    //if the app not has an assistant, we need to create it
    try {
      upsertAssistant(app, openaiApiKey);
    } catch (JSONException | IOException e) {
      throw new OBException(e.getMessage());
    }
  }

  private static boolean matchParamAndCode(JSONObject response, String param, String code)
      throws JSONException {
    if (!response.has(ERROR)) {
      return false;
    }
    JSONObject error = response.getJSONObject(ERROR);
    return error.has("param") && error.has("code") && StringUtils.equals(error.getString("param"),
        param) && StringUtils.equals(error.getString("code"), code);
  }

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
    String model = CopilotUtils.getAppModel(app, CopilotConstants.PROVIDER_OPENAI);
    logIfDebug("Selected model: " + model);
    body.put("model", model);
    //make the request to openai
    JSONObject response = makeRequestToOpenAI(openaiApiKey, endpoint, body, "POST", null, false);
    logIfDebug(response.toString());
    //
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
      //if the assistant has changed, we need to update the assistant id
      app.setOpenaiIdAssistant(response.getString("id"));
      OBDal.getInstance().save(app);
      OBDal.getInstance().flush();
    }
    return response;
  }

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

  private static JSONObject generateCodeInterpreterResources(CopilotApp app) throws JSONException {
    JSONArray files = getKbArrayFiles(app);
    JSONObject fileIds = new JSONObject();
    fileIds.put("file_ids", files);
    return fileIds;
  }

  private static JSONObject generateFileSearchResources(CopilotApp app) throws JSONException {
    JSONObject vectordb = new JSONObject();
    JSONArray vectorIds = new JSONArray();
    vectorIds.put(getOrCreateVectorDbId(app));
    vectordb.put("vector_store_ids", vectorIds);
    return vectordb;
  }

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

  public static void logIfDebug(String text) {
    if (log.isDebugEnabled()) {
      log.debug(text);
    }
  }

  private static void deleteAssistant(String openaiAssistantId, String openaiApiKey)
      throws JSONException {
    String endpoint = ENDPOINT_ASSISTANTS + "/" + openaiAssistantId;
    JSONObject json = makeRequestToOpenAI(openaiApiKey, endpoint, null, METHOD_DELETE, null);
    logIfDebug(json.toString());
  }


  public static JSONObject wrappWithJSONSchema(JSONObject parameters) throws JSONException {
    return new JSONObject().put("type", "object").put("properties", parameters);
  }

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

  private static JSONObject makeRequestToOpenAI(String openaiApiKey, String endpoint,
      JSONObject body, String method, String queryParams) throws UnirestException, JSONException {
    return makeRequestToOpenAI(openaiApiKey, endpoint, body, method, queryParams, true);
  }

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

  private static String syncHQLAppSource(CopilotAppSource appSource, String openaiApiKey)
      throws JSONException {
    String openaiFileId = appSource.getOpenaiIdFile();
    if (StringUtils.isNotEmpty(openaiFileId)) {
      logIfDebug("Deleting file " + appSource.getFile().getName());
      deleteFile(appSource.getOpenaiIdFile(), openaiApiKey);
    }
    File file = CopilotUtils.generateHQLFile(appSource);

    String fileId = uploadFileToOpenAI(openaiApiKey, file);
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);

    aim.upload(new HashMap<>(), APP_SOURCE_TAB_ID, appSource.getId(),
        appSource.getOrganization().getId(), file);


    appSource.setOpenaiIdFile(fileId);
    OBDal.getInstance().save(appSource);
    OBDal.getInstance().flush();
    return fileId;
  }

  private static boolean existsRemoteFile(String openaiFileId, String openaiApiKey)
      throws JSONException {
    var response = makeRequestToOpenAI(openaiApiKey, ENDPOINT_FILES + "/" + openaiFileId, null,
        "GET", null, false);
    return !response.has(ERROR);
  }

  private static boolean fileHasChanged(CopilotFile fileToSync) {
    if (StringUtils.isEmpty(fileToSync.getOpenaiIdFile())) {
      return true;
    }
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

  static void deleteFile(String openaiIdFile, String openaiApiKey) throws JSONException {
    if (existsRemoteFile(openaiIdFile, openaiApiKey)) {
      JSONObject response = makeRequestToOpenAI(openaiApiKey, ENDPOINT_FILES + "/" + openaiIdFile,
          null, METHOD_DELETE, null);
      logIfDebug(response.toString());
    }
  }

  private static String downloadAttachmentAndUploadFile(CopilotFile fileToSync, String openaiApiKey)
      throws JSONException, IOException {
    //make the request to openai
    File tempFile = getFileFromCopilotFile(fileToSync);
    return uploadFileToOpenAI(openaiApiKey, tempFile);
  }

  public static File getFileFromCopilotFile(CopilotFile fileToSync) throws IOException {
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(
        AttachImplementationManager.class);
    OBCriteria<Attachment> attCrit = OBDal.getInstance().createCriteria(Attachment.class);
    attCrit.add(Restrictions.eq(Attachment.PROPERTY_RECORD, fileToSync.getId()));
    Attachment attach = (Attachment) attCrit.setMaxResults(1).uniqueResult();
    if (attach == null) {
      CopilotUtils.throwMissingAttachException(fileToSync);
    }
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    aim.download(attach.getId(), os);
    //save os to temp file
    //create a temp file
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

  public static JSONArray getModelList(String openaiApiKey) {
    try {
      JSONObject list = makeRequestToOpenAI(openaiApiKey, ENDPOINT_MODELS, null, "GET", null);

      return new JSONArray(list.getString("data"));
    } catch (JSONException e) {
      throw new OBException(e.getMessage());
    }
  }

  public static String getOpenaiApiKey() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    return properties.getProperty(OPENAI_API_KEY);
  }

  public static void deleteLocalAssistants(String openaiApiKey) {
    try {
      JSONArray assistants = listAssistants(openaiApiKey);
      for (int i = 0; i < assistants.length(); i++) {
        JSONObject assistant = assistants.getJSONObject(i);
        if (assistant.getString("name").startsWith("Copilot [LOCAL]")) {
          deleteAssistant(assistant.getString("id"), openaiApiKey);
        }
      }
    } catch (JSONException e) {
      throw new OBException(e);
    }
  }

  public static void refreshVectorDb(CopilotApp app) throws JSONException {
    String openAIVectorDbId = getOrCreateVectorDbId(app);
    JSONObject currentFiles = makeRequestToOpenAI(getOpenaiApiKey(),
        ENDPOINT_VECTORDB + "/" + openAIVectorDbId + ENDPOINT_FILES, null, "GET", null);
    List<String> updatedFiles = updateVectorDbFiles(app, openAIVectorDbId);
    removeOutdatedFiles(updatedFiles, currentFiles, openAIVectorDbId);
  }

  private static String getOrCreateVectorDbId(CopilotApp app) throws JSONException {
    if (app.getOpenaiVectordbID() != null) {
      if (!existsVectorDb(app.getOpenaiVectordbID())) {
        return createVectorDbId(app);
      }
      return app.getOpenaiVectordbID();
    }
    return createVectorDbId(app);
  }

  private static boolean existsVectorDb(String openaiIdVectordb) {
    try {
      JSONObject response = makeRequestToOpenAI(getOpenaiApiKey(),
          ENDPOINT_VECTORDB + "/" + openaiIdVectordb, null, "GET", null, false);
      return !response.has("error");
    } catch (JSONException e) {
      return false;
    }
  }

  private static boolean existsAssistant(String openaiAssistantId) {
    try {
      JSONObject response = makeRequestToOpenAI(getOpenaiApiKey(),
          ENDPOINT_ASSISTANTS + "/" + openaiAssistantId, null, "GET", null, false);
      return !response.has("error");
    } catch (JSONException e) {
      return false;
    }
  }

  private static String createVectorDbId(CopilotApp app) throws JSONException {
    JSONObject vectordb = new JSONObject();
    vectordb.put("name", app.getName());
    JSONObject response = makeRequestToOpenAI(getOpenaiApiKey(), ENDPOINT_VECTORDB, vectordb,
        "POST", null);
    String openAIVectorDbId = response.getString("id");
    app.setOpenaiVectordbID(openAIVectorDbId);
    OBDal.getInstance().save(app);
    OBDal.getInstance().flush();

    return openAIVectorDbId;
  }

  private static List<String> updateVectorDbFiles(CopilotApp app, String openAIVectorDbId)
      throws JSONException {
    List<String> updatedFiles = new ArrayList<>();
    for (CopilotAppSource copilotAppSource : app.getETCOPAppSourceList()) {
      if (!copilotAppSource.isExcludeFromRetrieval() && CopilotConstants.isKbBehaviour(copilotAppSource)) {
        if (copilotAppSource.getFile() == null) {
          continue;
        }

        CopilotFile file = copilotAppSource.getFile();
        String openAIFileId = StringUtils.isNotEmpty(
            copilotAppSource.getOpenaiIdFile()) ? copilotAppSource.getOpenaiIdFile() : file.getOpenaiIdFile();
        JSONObject fileSearch = new JSONObject();
        fileSearch.put("file_id", openAIFileId);
        var response = makeRequestToOpenAI(getOpenaiApiKey(),
            ENDPOINT_VECTORDB + "/" + openAIVectorDbId + ENDPOINT_FILES, fileSearch, "POST", null,
            false);
        if (!response.has(ERROR)) {
          updatedFiles.add(openAIFileId);
        } else {
          if (app.isCodeInterpreter()) {
            log.warn("Error updating file in vector db: " + response);
          } else {
            throw new OBException(
                String.format(OBMessageUtils.messageBD("ETCOP_Error_Updating_VectorDb"), app.getName(),
                    response.getJSONObject(ERROR).getString(MESSAGE)));
          }
        }
      }
    }
    return updatedFiles;
  }

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
            OpenAIUtils.ENDPOINT_VECTORDB + "/" + openAIVectorDbId + ENDPOINT_FILES + "/" + existingFileId,
            null, METHOD_DELETE, null);
      }
    }
  }

  /**
   * Synchronizes the list of available OpenAI models by fetching the latest
   * models from OpenAI and updating the database accordingly. This method retrieves
   * the models via OpenAI's API, excludes specific models based on ownership and naming,
   * and then compares the retrieved models with the current models in the database.
   * <p>If a model exists in the database but is no longer available in OpenAI's list,
   * it is marked as inactive. New models that do not exist in the database are added.
   *
   * @param openaiApiKey
   *     The API key used to authenticate requests to OpenAI's API for retrieving model information.
   */
  public static void syncOpenaiModels(String openaiApiKey) {
    //ask to openai for the list of models
    JSONArray modelJSONArray = OpenAIUtils.getModelList(openaiApiKey);
    //transfer ids of json array to a list of strings
    List<JSONObject> modelIds = new ArrayList<>();
    for (int i = 0; i < modelJSONArray.length(); i++) {
      try {
        JSONObject modelObj = modelJSONArray.getJSONObject(i);
        if (!StringUtils.startsWith(modelObj.getString("id"), "ft:") && //exclude the models that start with gpt-4o
            !StringUtils.equals(modelObj.getString("owned_by"), "openai-dev") &&
            !StringUtils.equals(modelObj.getString("owned_by"), "openai-internal")) {
          modelIds.add(modelObj);
        }
      } catch (JSONException e) {
        log.error("Error in syncOpenaiModels", e);
      }
    }
    //now we have a list of ids, we can get the list of models in the database
    List<CopilotModel> modelsInDB = OBDal.getInstance().createCriteria(CopilotModel.class)
        .add(Restrictions.eq(CopilotModel.PROPERTY_PROVIDER, "openai")).list();

    //now we will check the models of the database that are not in the list of models from openai, to mark them as not active
    for (CopilotModel modelInDB : modelsInDB) {
      //check if the model is in the list of models from openai
      if (modelIds.stream().noneMatch(modelInModelList(modelInDB))) {
        modelInDB.setActive(false);
        OBDal.getInstance().save(modelInDB);
        continue;
      }
      modelIds.removeIf(modelInModelList(modelInDB));
    }
    //the models that are not in the database, we will create them,
    saveNewModels(modelIds);
  }

  /**
   * Returns a predicate to check if a specified {@link CopilotModel}
   * instance matches a given OpenAI model (represented as a {@link JSONObject}).
   * This helper is primarily used to determine whether a model fetched from OpenAI
   * is already present in the database by comparing model IDs.
   *
   * @param modelInDB
   *     The {@link CopilotModel} instance to be matched.
   * @return A predicate that checks if the model ID in the database matches the ID of a given
   *     OpenAI model.
   */
  private static Predicate<JSONObject> modelInModelList(CopilotModel modelInDB) {
    return model -> StringUtils.equals(model.optString("id"), modelInDB.getSearchkey());
  }

  /**
   * This method is used to save new models into the database.
   * It iterates over a list of JSONObjects, each representing a model, and creates a new CopilotOpenAIModel instance for each one.
   * The method sets the client, organization, search key, name, and active status for each new model.
   * It also retrieves the creation date of the model from the JSONObject, converts it from a Unix timestamp to a Date object, and sets it for the new model.
   * The new model is then saved into the database.
   * After all models have been saved, the method flushes the session to ensure that all changes are persisted to the database.
   * If an exception occurs during the execution of the method, it logs the error message and continues with the next iteration.
   * The method uses the OBContext to set and restore the admin mode before and after the execution of the method, respectively.
   *
   * @param modelIds
   *     A list of JSONObjects, each representing a model to be saved into the database.
   */
  private static void saveNewModels(List<JSONObject> modelIds) {
    try {
      OBContext.setAdminMode();
      for (JSONObject modelData : modelIds) {
        CopilotModel model = OBProvider.getInstance().get(CopilotModel.class);
        model.setNewOBObject(true);
        model.setClient(OBDal.getInstance().get(Client.class, "0"));
        model.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
        model.setSearchkey(modelData.optString("id"));
        model.setName(modelData.optString("id"));
        model.setProvider("openai");
        model.setActive(true);
        //get the date in The Unix timestamp (in seconds) when the model was created. Convert to date
        long creationDate = modelData.optLong("created"); // Unix timestamp (in seconds) when the model was created
        model.setCreationDate(new java.util.Date(creationDate * 1000L));
        OBDal.getInstance().save(model);
      }
      OBDal.getInstance().flush();
    } catch (Exception e) {
      log.error("Error in saveNewModels", e);
    } finally {
      OBContext.restorePreviousMode();
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
