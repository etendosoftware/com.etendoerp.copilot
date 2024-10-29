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
import javax.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TransferQueue;

import static com.etendoerp.copilot.rest.RestServiceUtil.*;
import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import com.etendoerp.copilot.util.CopilotConstants;

public class RestService {
  private static final Logger log4j = LogManager.getLogger(RestService.class);
  static final Map<String, TransferQueue<String>> asyncRequests = new HashMap<>();
  public static final String CACHED_QUESTION = "cachedQuestion";

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
      if ((StringUtils.equalsIgnoreCase(request.getMethod(), "POST") && request.getReader() != null)) {
        BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
        JSONObject jsonBody = new JSONObject(sb.toString());
        question = jsonBody.getString(CopilotConstants.PROP_QUESTION);
        if (StringUtils.isBlank(question)) {
          throw new OBException("Question is required");
        }
      }
      request.getSession().setAttribute(CACHED_QUESTION, question);
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(new JSONObject().put("message", "Question cached").toString());
    } catch (IOException | JSONException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      try {
        response.getWriter().write(new JSONObject().put(CopilotConstants.ERROR, e.getMessage()).toString());
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
  private String readCachedQuestion(HttpServletRequest request) throws JSONException {
    // Read the cached question from the session
    HttpSession session = request.getSession();
    if ( session == null) {
      throw new JSONException("Session is null.");
    }
    String cachedQuestion = (String) session.getAttribute(CACHED_QUESTION);
    if (StringUtils.equals(cachedQuestion, null)) {
      throw new JSONException("Cached question is null.");
    }
    logIfDebug("Reading cached question: " + cachedQuestion);
    request.getSession().removeAttribute(CACHED_QUESTION);
    return cachedQuestion;
  }

  private void sendErrorResponse(HttpServletResponse response, int status, String message)
      throws IOException, JSONException {
    response.setStatus(status);
    JSONObject error = new JSONObject();
    error.put(CopilotConstants.ERROR, message);
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

  /**
   * Checks if the incoming HTTP request is an asynchronous request.
   * The method determines this by comparing the request's path with predefined
   * constants representing asynchronous request types.
   *
   * @param request the {@link HttpServletRequest} object containing client request information
   * @return {@code true} if the request path matches an asynchronous request type,
   *         otherwise {@code false}
   */
  public boolean isAsyncRequest(HttpServletRequest request) {
    String path = request.getPathInfo();
    if (StringUtils.equals(path, AQUESTION)) {
      return true;
    }
    return StringUtils.equals(path, AGRAPH);
  }

  /**
   * Handles the incoming HTTP request for processing a question.
   * This method extracts parameters from the request, validates them,
   * and determines whether to process the request asynchronously or synchronously.
   *
   * @param request  the {@link HttpServletRequest} object containing client request information
   * @param response the {@link HttpServletResponse} object used to return response to the client
   * @throws IOException   if an input or output error occurs during the handling of the request
   * @throws JSONException if there is an error in processing JSON data
   */
  public void handleQuestion(HttpServletRequest request, HttpServletResponse response)
      throws IOException, JSONException {
    // Get the parameters from the JSON or request
    JSONObject json = extractRequestBody(request);

    // Read cached question if necessary
    addCachedQuestionIfPresent(request, json);

    // Validate required parameters
    validateRequiredParams(json);

    // Handle request
    if (isAsyncRequest(request)) {
      processAsyncRequest(request, response, json);
    } else {
      processSyncRequest(response, json);
    }
  }

  private JSONObject extractRequestBody(HttpServletRequest request) throws IOException, JSONException {
    JSONObject json = new JSONObject();

    if (isPostRequest(request)) {
      json = parseJsonFromRequest(request);
    }
    if (json.length() == 0) {
      json = retrieveParametersAsJson(request);
    }

    return json;
  }

  private boolean isPostRequest(HttpServletRequest request) throws IOException {
    return StringUtils.equalsIgnoreCase(request.getMethod(), "POST") && request.getReader() != null;
  }

  private JSONObject parseJsonFromRequest(HttpServletRequest request) {
    try {
      String body = request.getReader().lines().reduce("", String::concat);
      return new JSONObject(body);
    } catch (IOException | JSONException e) {
      return new JSONObject();
    }
  }

  /**
   * Retrieves parameters from the incoming HTTP request and
   * constructs a JSON object containing those parameters.
   * This method checks for the existence of specific parameters
   * defined in {@link CopilotConstants} and adds them to the JSON object.
   *
   * @param request the {@link HttpServletRequest} object containing client request information
   * @return a {@link JSONObject} containing the retrieved parameters
   * @throws JSONException if there is an error in constructing the JSON object
   */
  public JSONObject retrieveParametersAsJson(HttpServletRequest request) throws JSONException {
    JSONObject json = new JSONObject();

    addParameterIfExists(json, request, CopilotConstants.PROP_QUESTION);
    addParameterIfExists(json, request, CopilotConstants.PROP_APP_ID);
    addParameterIfExists(json, request, CopilotConstants.PROP_CONVERSATION_ID);
    addParameterIfExists(json, request, CopilotConstants.PROP_FILE);

    return json;
  }

  private void addParameterIfExists(JSONObject json, HttpServletRequest request,
      String paramName) throws JSONException {
    String paramValue = request.getParameter(paramName);
    if (paramValue != null) {
      json.put(paramName, paramValue);
    }
  }

  /**
   * Adds a cached question to the provided JSON object if the question
   * is not already present in the JSON and if the cached question exists.
   * This method checks for a cached question in the request and populates
   * the JSON object accordingly.
   *
   * @param request the {@link HttpServletRequest} object containing client request information
   * @param json    the {@link JSONObject} to which the cached question may be added
   * @throws JSONException if there is an error in adding the question to the JSON object
   */
  public void addCachedQuestionIfPresent(HttpServletRequest request, JSONObject json) throws JSONException {
    String cachedQuestion = readCachedQuestion(request);
    if (StringUtils.isBlank(json.optString(CopilotConstants.PROP_QUESTION)) && StringUtils.isNotBlank(cachedQuestion)) {
      json.put(CopilotConstants.PROP_QUESTION, cachedQuestion);
    }
  }

  /**
   * Validates that the required parameters are present in the provided JSON object.
   * This method checks for the existence of specific parameters defined in
   * {@link CopilotConstants}. If any required parameter is missing, an {@link OBException}
   * is thrown with a corresponding error message.
   *
   * @param json the {@link JSONObject} containing parameters to be validated
   * @throws OBException if any required parameter is missing in the JSON object
   */
  public void validateRequiredParams(JSONObject json) {
    if (!json.has(CopilotConstants.PROP_QUESTION)) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_MissingParam"), CopilotConstants.PROP_QUESTION));
    }
    if (!json.has(CopilotConstants.PROP_APP_ID)) {
      throw new OBException(
          String.format(OBMessageUtils.messageBD("ETCOP_MissingParam"), CopilotConstants.PROP_APP_ID));
    }
  }

  private void processAsyncRequest(HttpServletRequest request, HttpServletResponse response,
      JSONObject json) throws IOException, JSONException {
    try {
      RestServiceUtil.handleQuestion(true, response, json);
    } catch (OBException e) {
      RestServiceUtil.setEventStreamMode(response);
      JSONObject errorEventJSON = RestServiceUtil.getErrorEventJSON(request, e);
      PrintWriter writerToFront = response.getWriter();
      RestServiceUtil.sendEventToFront(writerToFront, errorEventJSON, true);
    }
  }

  /**
   * Processes a synchronous HTTP request by handling the provided JSON object
   * and writing the response back to the client. This method invokes a REST service
   * to handle the question and manages the response and any potential errors.
   *
   * @param response the {@link HttpServletResponse} object used to return the response to the client
   * @param json     the {@link JSONObject} containing the request parameters to be processed
   * @throws IOException   if an input or output error occurs during the response writing process
   * @throws JSONException if there is an error in processing the JSON data
   */
  public void processSyncRequest(HttpServletResponse response, JSONObject json) throws IOException, JSONException {
    try {
      var responseOriginal = RestServiceUtil.handleQuestion(false, response, json);
      response.setContentType(APPLICATION_JSON_CHARSET_UTF_8);
      response.getWriter().write(responseOriginal.toString());
    } catch (CopilotRestServiceException e) {
      response.getWriter().write(new JSONObject().put(CopilotConstants.ERROR, e.getMessage()).toString());
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
