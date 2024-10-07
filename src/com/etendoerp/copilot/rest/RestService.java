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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
      } else if (StringUtils.equalsIgnoreCase(path, "/cacheQuestion")) {
        handleCacheQuestion(request, response);
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

  /**
   * Handles the caching of a question from the HTTP request.
   * This method attempts to save the cached question and handles any exceptions that may occur.
   *
   * @param request
   *     the HttpServletRequest object that contains the request the client made to the servlet
   * @param response
   *     the HttpServletResponse object that contains the response the servlet returns to the client
   */
  private void handleCacheQuestion(HttpServletRequest request, HttpServletResponse response) {
    try {
      // Attempt to save the cached question
      saveCachedQuestion(request, response);
    } catch (Exception e) {
      // Log the error and send a BAD_REQUEST response
      log4j.error(e);
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      } catch (IOException ioException) {
        // Log the IOException and send an INTERNAL_SERVER_ERROR response
        log4j.error(ioException);
        try {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ioException.getMessage());
        } catch (IOException ioException2) {
          // Log the second IOException
          log4j.error(ioException2);
        }
      }
    }
  }

  /**
   * Saves the cached question from the HTTP request.
   * This method reads the question from the request body and stores it in the session.
   * If an error occurs, it sends an appropriate error response.
   *
   * @param request
   *     the HttpServletRequest object that contains the request the client made to the servlet
   * @param response
   *     the HttpServletResponse object that contains the response the servlet returns to the client
   */
  private void saveCachedQuestion(HttpServletRequest request, HttpServletResponse response) {
    // Read the question sent in the body
    try {
      String question = null;
      if ("POST".equalsIgnoreCase(request.getMethod()) && request.getReader() != null) {
        BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
        JSONObject jsonBody = new JSONObject(sb.toString());
        question = jsonBody.getString("question");
        if (question == null) {
          throw new OBException("Question is required");
        }
      }
      request.getSession().setAttribute("cachedQuestion", question);
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(new JSONObject().put("message", "Question cached").toString());
    } catch (IOException | JSONException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      try {
        response.getWriter().write(new JSONObject().put("error", e.getMessage()).toString());
      } catch (IOException | JSONException ioException) {
        log4j.error(ioException);
      }
    }
  }

  /**
   * Reads the cached question from the session.
   * This method retrieves the cached question from the session, logs it, and then removes it from the session.
   *
   * @param request
   *     the HttpServletRequest object that contains the request the client made to the servlet
   * @return the cached question as a String
   */
  private String readCachedQuestion(HttpServletRequest request) {
    // Read the cached question from the session
    String cachedQuestion = (String) request.getSession().getAttribute("cachedQuestion");
    System.out.println("Reading cached question: " + cachedQuestion);
    request.getSession().removeAttribute("cachedQuestion");
    return cachedQuestion;
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
    response.setStatus(HttpServletResponse.SC_OK);
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
    String cachedQuestion = readCachedQuestion(request);
    if (StringUtils.isBlank(json.optString("question")) && StringUtils.isNotBlank(cachedQuestion)) {
      json.put("question", cachedQuestion);
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
