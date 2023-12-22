package com.etendoerp.copilot.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.hook.CopilotFileHookManager;

import kong.unirest.MimeTypes;
import kong.unirest.Unirest;

public class OpenAIUtils {
  private static final Logger log = LogManager.getLogger(OpenAIUtils.class);
  public static final String BASE_URL = "https://api.openai.com/v1";
  public static final String METHOD_DELETE = "DELETE";

  private OpenAIUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static void syncAssistant(String openaiApiKey,
      CopilotApp app) throws JSONException, IOException, InterruptedException {
    //first we need to get the assistant
    //if the app not has a assistant, we need to create it
    if (StringUtils.isEmpty(app.getOpenaiIdAssistant())) {
      String assistantId = OpenAIUtils.createAssistant(app, openaiApiKey);
      app.setOpenaiIdAssistant(assistantId);
      OBDal.getInstance().save(app);
      OBDal.getInstance().flush();
    } else {
      //we will update the assistant
      OpenAIUtils.updateAssistant(app, openaiApiKey);
    }

  }

  private static void updateAssistant(CopilotApp app,
      String openaiApiKey) throws JSONException, IOException, InterruptedException {
    //almost the same as createAssistant, but we need to update the assistant

    String endpoint = "/assistants/" + app.getOpenaiIdAssistant();
    JSONObject body = new JSONObject();
    body.put("instructions", app.getPrompt());
    body.put("name", app.getName());
    JSONArray files = getArrayFiles(app);
    if (files.length() > 0) {
      body.put("file_ids", files);
    }
    body.put("tools", buildToolsArray(app.isCodeInterpreter(), files.length() > 0, new JSONArray()));
    body.put("model", app.getModel());
    //make the request to openai
    JSONObject jsonResponse = makeRequestToOpenAI(openaiApiKey, endpoint, body, "POST", null);
    logIfDebug(jsonResponse.toString());

  }

  private static void listAssistants(String openaiApiKey) throws JSONException, IOException, InterruptedException {
    String endpoint = "/assistants";
    JSONObject json = makeRequestToOpenAI(openaiApiKey, endpoint, null, "GET", "?order=desc&limit=100"
    );
    for (int i = 0; i < json.getJSONArray("data").length(); i++) {
      JSONObject assistant = json.getJSONArray("data").getJSONObject(i);
      String created = assistant.getString(
          "created_at"); // convert the date to a timestamp. the created is in The Unix timestamp (in seconds) for when the assistant file was created.
      Date date = new Date(Long.parseLong(created) * 1000);
      logIfDebug(
          String.format("%s - %s - %s", assistant.getString("id"), assistant.getString("name"), date.toString()));
    }

  }

  private static void logIfDebug(String text) {
    if (log.isDebugEnabled()) {
      log.debug(text);
    }
  }

  private static void deleteAssistant(String openaiAssistantId,
      String openaiApiKey) throws IOException, InterruptedException, JSONException {
    String endpoint = "/assistants/" + openaiAssistantId;
    JSONObject json = makeRequestToOpenAI(openaiApiKey, endpoint, null, METHOD_DELETE, null);
    logIfDebug(json.toString());
  }

  private static String createAssistant(CopilotApp app,
      String openaiApiKey) throws JSONException, IOException, InterruptedException {
    //recreate the following curl commandç

    String endpoint = "/assistants";
    JSONObject body = new JSONObject();
    body.put("instructions", app.getPrompt());
    body.put("name", app.getName());
    JSONArray files = getArrayFiles(app);
    if (files.length() > 0) {
      body.put("file_ids", files);
    }
    body.put("tools", buildToolsArray(app.isCodeInterpreter(), files.length() > 0, new JSONArray()
        //TODO: add the tools of the tools tab
    ));
    body.put("model", app.getModel());
    //make the request to openai
    JSONObject jsonResponse = makeRequestToOpenAI(openaiApiKey, endpoint, body, "POST", null);
    return jsonResponse.getString("id");


  }

  private static JSONArray getArrayFiles(CopilotApp app) {
    JSONArray result = new JSONArray();
    for (CopilotAppSource source : app.getETCOPAppSourceList()) {
      if (!StringUtils.isEmpty(source.getFile().getOpenaiIdFile())) {
        result.put(source.getFile().getOpenaiIdFile());
      }
    }
    return result;
  }


  private static JSONObject makeRequestToOpenAIForFiles(String openaiApiKey, String endpoint,
      String purpose, String filename,
      ByteArrayOutputStream file) throws IOException, JSONException {
    //save os to temp file
    //create a temp file
    String fileWithoutExtension = filename.substring(0, filename.lastIndexOf("."));
    String extension = filename.substring(filename.lastIndexOf(".") + 1);
    File tempFile = File.createTempFile(fileWithoutExtension, "." + extension);
    //write ByteArrayOutputStream to tempFile
    boolean setW = tempFile.setWritable(true);
    if (!setW) {
      logIfDebug("The temp file is not writable");
    }
    tempFile.deleteOnExit();
    //write ByteArrayOutputStream to tempFile
    file.writeTo(new FileOutputStream(tempFile));
    kong.unirest.HttpResponse<String> response = Unirest.post(BASE_URL + endpoint)
        .header("Authorization", String.format("Bearer %s", openaiApiKey))
        .field("purpose", purpose)
        .field("file", tempFile, MimeTypes.EXE).asString();
    return new JSONObject(response.getBody());
  }


  private static JSONObject makeRequestToOpenAI(String openaiApiKey, String endpoint,
      JSONObject body, String method, String queryParams) throws IOException, JSONException {
    HttpClient client = HttpClient.newHttpClient();
    String url = BASE_URL + endpoint + ((queryParams != null) ? queryParams : "");
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url));
    requestBuilder.header("Content-Type", "application/json");
    requestBuilder.header("Authorization", "Bearer " + openaiApiKey)
        .header("OpenAI-Beta", "assistants=v1");
    switch (method) {
      case "GET":
        requestBuilder.GET();
        break;
      case "POST":
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body.toString() : ""));
        break;
      case "PUT":
        requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body.toString() : ""));
        break;
      case METHOD_DELETE:
        requestBuilder.DELETE();
        break;
      default:
        break;
    }
    HttpRequest request = requestBuilder.build();
    String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    return new JSONObject(response);
  }
  private static JSONArray buildToolsArray(boolean codeInterpreter, boolean retrieval,
      JSONArray toolSet) throws JSONException {
    JSONArray result = (toolSet != null) ? toolSet : new JSONArray();
    JSONObject tool = new JSONObject();
    if (codeInterpreter) {
      tool.put("type", "code_interpreter");
      result.put(tool);
    }
    if (retrieval) {
      tool = new JSONObject();
      tool.put("type", "retrieval");
      result.put(tool);
    }
    return result;
  }

  public static void syncFile(CopilotFile fileToSync,
      String openaiApiKey) throws JSONException, IOException, InterruptedException {
    //first we need to get the file
    //if the file not has a id, we need to create it
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
    String fileId = OpenAIUtils.uploadFile(fileToSync, openaiApiKey);
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

  private static void deleteFile(String openaiIdFile,
      String openaiApiKey) throws JSONException, IOException, InterruptedException {
    JSONObject response = makeRequestToOpenAI(openaiApiKey, "/files/" + openaiIdFile, null, METHOD_DELETE, null);
    logIfDebug(response.toString());
  }

  private static String uploadFile(CopilotFile fileToSync, String openaiApiKey) throws JSONException, IOException {
    //recreate the following curl command with HttpRequests
    String endpoint = "/files";
    //make the request to openai
    JSONObject jsonResponse = null;
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
    OBCriteria<Attachment> attCrit = OBDal.getInstance().createCriteria(Attachment.class);
    attCrit.add(Restrictions.eq(Attachment.PROPERTY_RECORD, fileToSync.getId()));
    Attachment attach = (Attachment) attCrit.setMaxResults(1).uniqueResult();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    aim.download(attach.getId(), os);
    jsonResponse = makeRequestToOpenAIForFiles(openaiApiKey, endpoint, "assistants",
        attach.getName(), os);
    return jsonResponse.getString("id");
  }
}

