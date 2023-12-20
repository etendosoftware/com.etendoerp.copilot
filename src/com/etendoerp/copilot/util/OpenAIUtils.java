package com.etendoerp.copilot.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.MultipartRequest;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

import kong.unirest.MimeTypes;
import kong.unirest.Unirest;

public class OpenAIUtils {
  private static final Logger log = LogManager.getLogger(OpenAIUtils.class);
  public static final String BASE_URL = "https://api.openai.com/v1";

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
    body.put("tools", builToolsArray(app, app.isCodeInterpreter(), files.length() > 0, new JSONArray()));
    body.put("model", app.getModel());
    //make the request to openai
    JSONObject jsonResponse = makeRequestToOpenAI(openaiApiKey, endpoint, body, "POST", null);
    log.info(jsonResponse.toString());

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
      log.info(assistant.getString("id") + " - " + assistant.getString("name") + " - " + date.toString());
    }

  }

  private static void deleteAssistant(String openaiAssistantId, String openaiApiKey) {
    try {
      String endpoint = "/assistants/" + openaiAssistantId;
      JSONObject json = makeRequestToOpenAI(openaiApiKey, endpoint, null, "DELETE", null);
      log.info(json.toString());
    } catch (Exception e) {
      log.error("Error deleting assistant", e);
    }
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
    body.put("tools", builToolsArray(app, app.isCodeInterpreter(), files.length() > 0, new JSONArray()
        //TODO: add the tools of the tools tab
    ));
    body.put("model", app.getModel());
    //make the request to openai
    JSONObject jsonResponse = makeRequestToOpenAI(openaiApiKey, endpoint, body, "POST", null);
//system.out.println(jsonResponse.toString());
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

  /*private static JSONObject makeRequestToOpenAIForFiles2(String openaiApiKey, String endpoint, String method,
      String queryParams, String purpose, String filename,
      ByteArrayOutputStream file) throws IOException, InterruptedException, JSONException {

    String boundary = UUID.randomUUID().toString();

    // Construye el cuerpo de la solicitud
    StringBuilder formData = new StringBuilder();
    formData.append("--").append(boundary).append("\r\n");
    formData.append("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n");
    formData.append(purpose).append("\r\n");
    formData.append("--").append(boundary).append("\r\n");
    formData.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"\r\n");
    formData.append("Content-Type: " + getMIMEType(filename) + "\r\n\r\n");
    formData.append(new String(file.toByteArray())).append("\r\n");
    formData.append("--").append(boundary).append("--\r\n");


    HttpClient client = HttpClient.newHttpClient();
    String url = BASE_URL + endpoint + ((queryParams != null) ? queryParams : "");
    // Crea la solicitud
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + openaiApiKey)
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofString(formData.toString()))
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    log.info("rta: codigo:" + response.statusCode());
    JSONObject jsonResponse = new JSONObject(response.body());
    log.info("rta: " + jsonResponse.toString());
    return jsonResponse;
  }
*/
  private static JSONObject makeRequestToOpenAIForFiles(String openaiApiKey, String endpoint, String method,
      String queryParams, String purpose, String filename,
      ByteArrayOutputStream file) throws IOException, InterruptedException, JSONException {

    //save os to temp file
    //create a temp file
    String file_witout_extension = filename.substring(0, filename.lastIndexOf("."));
    String extension = filename.substring(filename.lastIndexOf(".") + 1);
    File tempFile = File.createTempFile(file_witout_extension, "." + extension);
    //write ByteArrayOutputStream to tempFile
    tempFile.setWritable(true);
    tempFile.deleteOnExit();
    //write ByteArrayOutputStream to tempFile
    file.writeTo(new FileOutputStream(tempFile));


    kong.unirest.HttpResponse<String> response = Unirest.post(BASE_URL + endpoint)
        .header("Authorization", "Bearer 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")
        .field("purpose", "assistants")
        .field("file", tempFile, MimeTypes.EXE).asString();

    log.info("codigo" + response.getStatus());
    log.info("rta: " + response.getBody());
    JSONObject jsonResponse = new JSONObject(response.getBody());
    return jsonResponse;
  }

  private static String getMIMEType(String filename) {
    //extract extension from filename
    String extension = filename.substring(filename.lastIndexOf(".") + 1);
    return OpenAIUConstants.getMimeType("." + extension);
  }

  private static JSONObject makeRequestToOpenAI(String openaiApiKey, String endpoint,
      JSONObject body, String method, String queryParams) throws IOException, InterruptedException, JSONException {
    HttpClient client = HttpClient.newHttpClient();
    String url = BASE_URL + endpoint + ((queryParams != null) ? queryParams : "");
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url));

    requestBuilder.header("Content-Type", "application/json");

    requestBuilder.header("Authorization", "Bearer " + openaiApiKey)
        .header("OpenAI-Beta", "assistants=v1");

    //curl https://api.openai.com/v1/files \
    //  -H "Authorization: Bearer $OPENAI_API_KEY" \
    //  -F purpose="fine-tune" \
    //  -F file="@mydata.jsonl"

    switch (method) {
      case "GET":
        requestBuilder.GET();
        break;
      case "POST":
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        break;
      case "PUT":
        requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body.toString()));
        break;
      case "DELETE":
        requestBuilder.DELETE();
        break;
      default:
        break;
    }
    HttpRequest request = requestBuilder.build();

    String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    JSONObject jsonResponse = new JSONObject(response);
    return jsonResponse;
  }

  private static JSONArray builToolsArray(CopilotApp app, boolean codeInterpreter, boolean retrieval,
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
    //TODO: Call hook to update the attached file
    if (!StringUtils.isEmpty(fileToSync.getOpenaiIdFile())) {
      //we will delete the file
      deleteFile(fileToSync.getOpenaiIdFile(), openaiApiKey);
    }

    String fileId = OpenAIUtils.uploadFile(fileToSync, openaiApiKey);
    fileToSync.setOpenaiIdFile(fileId);
    OBDal.getInstance().save(fileToSync);
    OBDal.getInstance().flush();

  }

  private static void deleteFile(String openaiIdFile,
      String openaiApiKey) throws JSONException, IOException, InterruptedException {
    JSONObject response = makeRequestToOpenAI(openaiApiKey, "/files/" + openaiIdFile, null, "DELETE", null);
    log.info(response.toString());

  }

  private static String uploadFile(CopilotFile fileToSync,
      String openaiApiKey) throws JSONException, IOException, InterruptedException {
    //recreate the following curl command with HttpRequests


    String endpoint = "/files";
    JSONObject body = new JSONObject();
    //make the request to openai
    JSONObject jsonResponse = null;
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(
        AttachImplementationManager.class);
    OBCriteria<Attachment> attCrit = OBDal.getInstance().createCriteria(Attachment.class);
    attCrit.add(Restrictions.eq(Attachment.PROPERTY_RECORD, fileToSync.getId()));
    Attachment attach = (Attachment) attCrit.setMaxResults(1).uniqueResult();
    ByteArrayOutputStream os = new ByteArrayOutputStream();

    aim.download(attach.getId(), os);


    jsonResponse = makeRequestToOpenAIForFiles(openaiApiKey, endpoint, "POST", null, "assistants",
        attach.getName(), os);

    return jsonResponse.getString("id");
  }
}
