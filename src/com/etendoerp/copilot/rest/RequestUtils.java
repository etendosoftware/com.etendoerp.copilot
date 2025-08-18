package com.etendoerp.copilot.rest;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.copilot.util.CopilotConstants;

/**
 * Utility class that centralizes request -> JSON extraction helpers.
 * Contains methods to add parameters to a JSONObject, parse request bodies
 * and retrieve a normalized JSON object from an HttpServletRequest.
 */
public final class RequestUtils {

  private RequestUtils() {
  }

  /**
   * Build a JSONObject from well-known request parameters used by Copilot endpoints.
   * Checks for question, app id, conversation id and file parameters (file is treated as an array).
   *
   * @param request the HttpServletRequest containing parameters
   * @return a JSONObject with the collected parameters
   * @throws JSONException when constructing the returned JSONObject fails
   */
  public static JSONObject retrieveParametersAsJson(HttpServletRequest request) throws JSONException {
    JSONObject json = new JSONObject();

    addParameterIfExists(json, request, CopilotConstants.PROP_QUESTION);
    addParameterIfExists(json, request, CopilotConstants.PROP_APP_ID);
    addParameterIfExists(json, request, CopilotConstants.PROP_CONVERSATION_ID);
    addParameterIfExists(json, request, CopilotConstants.PROP_FILE, true);

    return json;
  }

  /**
   * Returns true when the incoming request is a POST and the request reader is available.
   * This is used to decide whether to attempt to read a JSON body from the request.
   *
   * @param request the HttpServletRequest to inspect
   * @return true when method equals POST and reader is not null
   * @throws IOException if obtaining the reader throws an IOException
   */
  public static boolean isPostRequest(HttpServletRequest request) throws IOException {
    return StringUtils.equalsIgnoreCase(request.getMethod(), "POST") && request.getReader() != null;
  }

  /**
   * Reads the request body and parses it as JSON.
   * If reading or parsing fails this method returns an empty JSONObject.
   *
   * @param request the HttpServletRequest whose body will be parsed
   * @return a JSONObject with the parsed body or an empty JSONObject on error
   */
  public static JSONObject parseJsonFromRequest(HttpServletRequest request) {
    try {
      String body = request.getReader().lines().reduce("", String::concat);
      return new JSONObject(body);
    } catch (IOException | JSONException e) {
      return new JSONObject();
    }
  }

  /**
   * Normalizes incoming request data to a JSONObject.
   * If the request is a POST with a readable body this will try to parse it as JSON.
   * When the parsed body is empty, the method falls back to reading parameters via
   * {@link #retrieveParametersAsJson(HttpServletRequest)}.
   *
   * @param request the HttpServletRequest to extract data from
   * @return a JSONObject with the extracted request data
   * @throws IOException when accessing the request reader fails
   * @throws JSONException when constructing the returned JSONObject fails
   */
  public static JSONObject extractRequestBody(HttpServletRequest request) throws IOException, JSONException {
    JSONObject json = new JSONObject();

    if (isPostRequest(request)) {
      json = parseJsonFromRequest(request);
    }
    if (json.length() == 0) {
      json = retrieveParametersAsJson(request);
    }

    return json;
  }

  /**
   * Convenience overload that adds a parameter to a JSONObject when the parameter exists in the request.
   *
   * @param json the JSONObject to modify
   * @param request the HttpServletRequest containing parameters
   * @param paramName the parameter name to add when present
   * @throws JSONException when adding values to the JSONObject fails
   */
  public static void addParameterIfExists(JSONObject json, HttpServletRequest request, String paramName)
      throws JSONException {
    addParameterIfExists(json, request, paramName, false);
  }

  /**
   * Converts a String array into a JSONArray. Returns an empty JSONArray when the input is null.
   *
   * @param paramMultipleValues array of String values
   * @return a JSONArray containing the values or an empty JSONArray when input is null
   */
  public static JSONArray stringArrayToJsonArray(String[] paramMultipleValues) {
    if (paramMultipleValues == null) {
      return new JSONArray();
    }
    JSONArray jsonArray = new JSONArray();
    for (String value : paramMultipleValues) {
      jsonArray.put(value);
    }
    return jsonArray;
  }

  /**
   * Adds a parameter to the provided JSONObject if it exists in the request.
   * When {@code isArray} is false the parameter is read as a single value. When
   * {@code isArray} is true, the method will attempt to read multiple values and
   * add them as a JSONArray.
   *
   * @param json the JSONObject to be modified
   * @param request the HttpServletRequest to read parameters from
   * @param paramName the name of the parameter
   * @param isArray whether the parameter is expected to be multi-valued
   * @throws JSONException when adding values to the JSONObject fails
   */
  public static void addParameterIfExists(JSONObject json, HttpServletRequest request, String paramName,
      boolean isArray) throws JSONException {

    if (!isArray) {
      String paramValue = request.getParameter(paramName);
      if (paramValue != null) {
        json.put(paramName, paramValue);
      }
      return;
    }

    String[] paramMultipleValues = request.getParameterValues(paramName);
    if (paramMultipleValues != null) {
      json.put(paramName, stringArrayToJsonArray(paramMultipleValues));
    }
  }
}
