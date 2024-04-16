package com.etendoerp.copilot.rest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import static com.etendoerp.copilot.rest.RestServiceUtil.APPLICATION_JSON_CHARSET_UTF_8;
import static com.etendoerp.copilot.rest.RestServiceUtil.FILE;
import static com.etendoerp.copilot.rest.RestServiceUtil.GET_ASSISTANTS;
import static com.etendoerp.copilot.rest.RestServiceUtil.QUESTION;
import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

public class RestService extends HttpSecureAppServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException,
      ServletException {
    String path = request.getPathInfo();
    try {
      if (StringUtils.equalsIgnoreCase(path, GET_ASSISTANTS)) {
        handleAssistants(response);
        return;
      }
      else if (StringUtils.equalsIgnoreCase(path, "/labels")) {
        response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
        response.getWriter().write(RestServiceUtil.getJSONLabels().toString());
        return;
      }
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } catch (Exception e) {
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        log4j.error(ioException);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
      }
    }
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    String path = request.getPathInfo();
    try {
      OBContext.setAdminMode();
      if (StringUtils.equalsIgnoreCase(path, QUESTION)) {
        handleQuestion(request, response);
        return;
      } else if (StringUtils.equalsIgnoreCase(path, FILE)) {
        handleFile(request, response);
        return;
      }
      //if not a valid path, throw a error status
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } catch (Exception e) {
      log4j.error(e);
      try {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (Exception e2) {
        log4j.error(e2);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e2.getMessage());
      }
    }finally {
      OBContext.restorePreviousMode();
    }
  }

  private void sendErrorResponse(HttpServletResponse response, int status,
      String message) throws IOException, JSONException {
    response.setStatus(status);
    JSONObject error = new JSONObject();
    error.put("error", message);
    response.getWriter().write(error.toString());
  }

  private void handleFile(HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    logIfDebug("handleFile");
    // in the request we will receive a form-data with the field file with the file

    boolean isMultipart = ServletFileUpload.isMultipartContent(request);
    logIfDebug(String.format("isMultipart: %s", isMultipart));
    FileItemFactory factory = new DiskFileItemFactory();

    ServletFileUpload upload = new ServletFileUpload(factory);
    List<FileItem> items = upload.parseRequest(request);
    var responseJson = RestServiceUtil.handleFile(items);
    response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
    response.getWriter().write(responseJson.toString());
  }

  private void handleQuestion(HttpServletRequest request,
      HttpServletResponse response) throws IOException, JSONException {
    // read the json sent
    BufferedReader reader = request.getReader();
    StringBuilder sb = new StringBuilder();
    for (String line; (line = reader.readLine()) != null; ) {
      sb.append(line);
    }
    String jsonRequestStr = sb.toString();
    try {
      var responseOriginal = RestServiceUtil.handleQuestion(new JSONObject(jsonRequestStr));
      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(responseOriginal.toString());
    } catch (CopilotRestServiceException e) {
      response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
      if (e.getCode() > -1) {
        response.setStatus(e.getCode());
      } else {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
    }
  }

  private void handleAssistants(HttpServletResponse response) {
    try {
      var assistants = RestServiceUtil.handleAssistants();
      response.getWriter().write(assistants.toString());
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
