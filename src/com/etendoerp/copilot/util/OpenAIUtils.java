package com.etendoerp.copilot.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
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
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.hook.OpenAIPromptHookManager;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;

import static com.etendoerp.copilot.process.SyncOpenAIAssistant.ERROR;

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

  private OpenAIUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static void syncAssistant(String openaiApiKey, CopilotApp app) throws OBException {
    //first we need to get the assistant
    //if the app not has an assistant, we need to create it

    if (StringUtils.isEmpty(app.getOpenaiIdAssistant())) {
      String assistantId = OpenAIUtils.createAssistant(app, openaiApiKey);
      app.setOpenaiIdAssistant(assistantId);
      OBDal.getInstance().save(app);
      OBDal.getInstance().flush();
    } else {
      //we will update the assistant
      try {
        JSONObject response = OpenAIUtils.updateAssistant(app, openaiApiKey);
        if (response.has(ERROR)) {
          if (response.has(ERROR) && response.getJSONObject(ERROR)
              .has(MESSAGE) && response.getJSONObject(ERROR)
              .getString(MESSAGE)
              .contains("No assistant found with id")) {
            //the assistant not exists, we need to set the id to null and create it again
            app.setOpenaiIdAssistant(null);
            OBDal.getInstance().save(app);
            OBDal.getInstance().flush();
            String assistantId = OpenAIUtils.createAssistant(app, openaiApiKey);
            app.setOpenaiIdAssistant(assistantId);
            OBDal.getInstance().save(app);
            OBDal.getInstance().flush();
          } else {
            throw new OBException(
                String.format(OBMessageUtils.messageBD("ETCOP_Error_Syn_Assist"), app.getName(),
                    response.getJSONObject(ERROR).getString(MESSAGE)));
          }

        }
      } catch (JSONException e) {
        throw new OBException(e.getMessage());
      }
    }
  }

  private static JSONObject updateAssistant(CopilotApp app, String openaiApiKey)
      throws JSONException {
    //almost the same as createAssistant, but we need to update the assistant

    String endpoint = ENDPOINT_ASSISTANTS + "/" + app.getOpenaiIdAssistant();
    JSONObject body = new JSONObject();
    body.put("instructions", getAssistantPrompt(app));
    body.put("name", app.getName());
    JSONArray files = getKbArrayFiles(app);
    if (files.length() > 0) {
      JSONObject fileIds = new JSONObject();
      fileIds.put("file_ids", files);
      JSONObject toolsResources = new JSONObject();
      toolsResources.put("code_interpreter", fileIds);
      JSONObject vectordb = new JSONObject();
      JSONArray vectorIds = new JSONArray();
      vectorIds.put(app.getOpenaiVectordbID());
      vectordb.put("vector_store_ids", vectorIds);
      toolsResources.put("file_search", vectordb);
      body.put("tool_resources", toolsResources);
    }
    body.put("tools", buildToolsArray(app));
    body.put("model", app.getModel().getSearchkey());
    //make the request to openai
    JSONObject jsonResponse = makeRequestToOpenAI(openaiApiKey, endpoint, body, "POST", null);
    logIfDebug(jsonResponse.toString());
    return jsonResponse;
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

  private static String createAssistant(CopilotApp app, String openaiApiKey) throws OBException {
    //recreate the following curl command
    try {

      String endpoint = ENDPOINT_ASSISTANTS;
      JSONObject body = new JSONObject();
      body.put("instructions", getAssistantPrompt(app));
      body.put("name", app.getName());
      JSONArray files = getKbArrayFiles(app);
      if (files.length() > 0) {
        body.put("file_ids", files);
      }
      body.put("tools", buildToolsArray(app));
      body.put("model", app.getModel().getSearchkey());
      //make the request to openai
      JSONObject jsonResponse = makeRequestToOpenAI(openaiApiKey, endpoint, body, "POST", null);
      if (jsonResponse.has(ERROR)) {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("ETCOP_Error_Syn_Assist"), app.getName(),
                jsonResponse.getJSONObject(ERROR).getString(MESSAGE)));
      }
      return jsonResponse.getString("id");
    } catch (JSONException e) {
      throw new OBException(e.getMessage());
    }

  }

  private static String getAssistantPrompt(CopilotApp app) {
    StringBuilder sb = new StringBuilder();
    sb.append(app.getPrompt());
    sb.append("\n");
    try {
      sb.append(WeldUtils.getInstanceFromStaticBeanManager(OpenAIPromptHookManager.class)
          .executeHooks(app));
    } catch (OBException e) {
      log.error("Error executing hooks", e);
    }
    return sb.toString();
  }

  private static JSONArray getToolSet(CopilotApp app) throws OBException, JSONException {
    // we will read from /copilot the tools if we can
    JSONArray result = new JSONArray();
    OBCriteria<CopilotAppTool> appToolCrit = OBDal.getInstance()
        .createCriteria(CopilotAppTool.class);
    appToolCrit.add(Restrictions.eq(CopilotAppTool.PROPERTY_COPILOTAPP, app));
    List<CopilotAppTool> appToolsList = appToolCrit.list();
    if (appToolsList.isEmpty()) {
      return result;
    }
    //make petition to /copilot
    for (CopilotAppTool appTool : appToolsList) {
      CopilotTool erpTool = appTool.getCopilotTool();
      String toolInfo = erpTool.getJsonStructure();
      if (toolInfo != null) {

        result.put(new JSONObject(toolInfo));
      }
    }

    return result;
  }

  public static JSONObject wrappWithJSONSchema(JSONObject parameters) throws JSONException {
    return new JSONObject().put("type", "object").put("properties", parameters);
  }

  private static JSONArray getKbArrayFiles(CopilotApp app) {
    JSONArray result = new JSONArray();
    for (CopilotAppSource source : app.getETCOPAppSourceList()) {
      if (CopilotConstants.isKbBehaviour(source) && !StringUtils.isEmpty(
          source.getFile().getOpenaiIdFile())) {
        result.put(source.getFile().getOpenaiIdFile());
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
    return new JSONObject(response.getBody());
  }

  private static JSONObject makeRequestToOpenAI(String openaiApiKey, String endpoint,
      JSONObject body, String method, String queryParams) throws UnirestException, JSONException {
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
    return new JSONObject(response.getBody());
  }

  private static JSONArray buildToolsArray(CopilotApp app) throws JSONException {
    JSONArray toolSet = getToolSet(app);
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

  public static void syncFile(CopilotFile fileToSync, String openaiApiKey)
      throws JSONException, IOException {
    //first we need to get the file
    //if the file not has an id, we need to create it
    logIfDebug("Syncing file " + fileToSync.getName());
    WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class)
        .executeHooks(fileToSync);
    if (!fileHasChanged(fileToSync)) {
      logIfDebug("File " + fileToSync.getName() + " not has changed, skipping sync");
      return;
    }
    if (!StringUtils.isEmpty(fileToSync.getOpenaiIdFile())) {
      //we will delete the file
      logIfDebug("Deleting file " + fileToSync.getName());
      deleteFile(fileToSync.getOpenaiIdFile(), openaiApiKey);
    }
    logIfDebug("Uploading file " + fileToSync.getName());
    String fileId = OpenAIUtils.downloadAttachmentAndUploadFile(fileToSync, openaiApiKey);
    fileToSync.setOpenaiIdFile(fileId);
    fileToSync.setLastSync(new Date());
    fileToSync.setUpdated(new Date());
    OBDal.getInstance().save(fileToSync);
    OBDal.getInstance().flush();

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

  private static void deleteFile(String openaiIdFile, String openaiApiKey) throws JSONException {
    JSONObject response = makeRequestToOpenAI(openaiApiKey, ENDPOINT_FILES + "/" + openaiIdFile,
        null, METHOD_DELETE, null);
    logIfDebug(response.toString());
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
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_ErrorMissingAttach"),
          fileToSync.getName()));
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
    jsonResponse = makeRequestToOpenAIForFiles(openaiApiKey, ENDPOINT_FILES, "assistants", fileToSend);
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
    List<String> updatedFiles = updateFiles(app, openAIVectorDbId);
    removeOutdatedFiles(updatedFiles, currentFiles, openAIVectorDbId);
  }

  private static String getOrCreateVectorDbId(CopilotApp app) throws JSONException {
    if (app.getOpenaiVectordbID() != null) {
      if(!existsVectorDb(app.getOpenaiVectordbID())) {
        return createVectorDbId(app);
      }
      return app.getOpenaiVectordbID();
    }
    return createVectorDbId(app);
  }

  private static boolean existsVectorDb(String openaiIdVectordb) {
    try {
      makeRequestToOpenAI(getOpenaiApiKey(), ENDPOINT_VECTORDB + "/" + openaiIdVectordb, null, "GET", null);
      return true;
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

  private static List<String> updateFiles(CopilotApp app, String openAIVectorDbId)
      throws JSONException {
    List<String> updatedFiles = new ArrayList<>();
    for (CopilotAppSource copilotAppSource : app.getETCOPAppSourceList()) {
      if (CopilotConstants.isKbBehaviour(copilotAppSource)) {
        CopilotFile file = copilotAppSource.getFile();
        if (file.getOpenaiIdFile() != null) {
          JSONObject fileSearch = new JSONObject();
          fileSearch.put("file_id", file.getOpenaiIdFile());
          makeRequestToOpenAI(getOpenaiApiKey(),
              ENDPOINT_VECTORDB + "/" + openAIVectorDbId + ENDPOINT_FILES, fileSearch, "POST", null);
          updatedFiles.add(file.getOpenaiIdFile());
        }
      }
    }
    return updatedFiles;
  }

  private static void removeOutdatedFiles(List<String> updatedFiles, JSONObject currentFiles,
      String openAIVectorDbId) throws JSONException {
    for (String existingFileId : updatedFiles) {
      for (int i = 0; i < currentFiles.getJSONArray("data").length(); i++) {
        JSONObject existingFile = currentFiles.getJSONArray("data").getJSONObject(i);
        if (!updatedFiles.contains(existingFile.getString("id"))) {
          makeRequestToOpenAI(getOpenaiApiKey(),
              ENDPOINT_VECTORDB + "/" + openAIVectorDbId + ENDPOINT_FILES + "/" + existingFileId, null,
              METHOD_DELETE, null);
        }
      }
    }
  }
}

