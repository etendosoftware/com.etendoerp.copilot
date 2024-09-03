package com.etendoerp.copilot.rest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TransferQueue;

import static com.etendoerp.copilot.rest.RestServiceUtil.*;
import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

public class RestService {
  private static final Logger log4j = LogManager.getLogger(RestService.class);
  static final Map<String, TransferQueue<String>> asyncRequests = new HashMap<>();

  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String path = request.getPathInfo();
    try {
      OBContext.setAdminMode();
      if (StringUtils.equalsIgnoreCase(path, GET_ASSISTANTS)) {
        handleAssistants(response);
        return;
      } else if (StringUtils.equalsIgnoreCase(path, AQUESTION)) {
        try {
          handleQuestion(request, response);
        } catch (OBException e) {
          throw new OBException("Error handling question: " + e.getMessage());
        }
        return;
      } else if (StringUtils.equalsIgnoreCase(path, "/labels")) {
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
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String path = request.getPathInfo();
    try {
      OBContext.setAdminMode();
      if (StringUtils.equalsIgnoreCase(path, QUESTION)) {
        try {
          handleQuestion(request, response);
        } catch (OBException e) {
          throw new OBException("Error handling question: " + e.getMessage());
        }
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
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private void sendErrorResponse(HttpServletResponse response, int status, String message)
      throws IOException, JSONException {
    response.setStatus(status);
    JSONObject error = new JSONObject();
    error.put("error", message);
    response.getWriter().write(error.toString());
  }

  private void handleFile(HttpServletRequest request, HttpServletResponse response)
      throws Exception {
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

  private boolean isAsyncRequest(HttpServletRequest request) {
    String path = request.getPathInfo();
    if (StringUtils.equals(path, AQUESTION)) {
      return true;
    }
    if (StringUtils.equals(path, AGRAPH)) {
      return true;
    }
    return false;
  }

  private void handleQuestion(HttpServletRequest request, HttpServletResponse response)
      throws IOException, JSONException {
    // read the json sent
    JSONObject json = null;
    // get body from request
    if ("POST".equalsIgnoreCase(request.getMethod()) && request.getReader() != null) {
      try {
        json = new JSONObject(request.getReader().lines().reduce("", String::concat));
      } catch (JSONException ignore) {
        // Body is not a valid json, try with params
      }
    }
    if (json == null) {
      json = new JSONObject();
      if (request.getParameter("question") != null) {
        json.put("question", request.getParameter("question"));
      }
      if (request.getParameter("app_id") != null) {
        json.put("app_id", request.getParameter("app_id"));
      }
      if (request.getParameter("conversation_id") != null) {
        json.put("conversation_id", request.getParameter("conversation_id"));
      }
      if (request.getParameter("file") != null) {
        json.put("file", request.getParameter("file"));
      }
    }
    if (!json.has("question")) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MissingParam"), "question"));
    }
    if (!json.has("app_id")) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MissingParam"), "app_id"));
    }
    boolean isAsyncRequest = isAsyncRequest(request);
    if (isAsyncRequest) {
      try {
        RestServiceUtil.handleQuestion(true, response, json);
      } catch (OBException e) {
        RestServiceUtil.setEventStreamMode(response);
        JSONObject errorEventJSON = RestServiceUtil.getErrorEventJSON(request, e);
        PrintWriter writerToFront = response.getWriter();
        RestServiceUtil.sendEventToFront(writerToFront, errorEventJSON, true);
      }
    } else {
      try {
        var responseOriginal = RestServiceUtil.handleQuestion(false, response, json);
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
