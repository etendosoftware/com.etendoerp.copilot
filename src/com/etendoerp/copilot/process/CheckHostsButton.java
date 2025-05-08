package com.etendoerp.copilot.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.copilot.util.CopilotUtils;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * Class responsible for verifying the connectivity and configuration of Etendo and Copilot hosts.
 * Provides feedback on the verification results in the form of JSON responses.
 */
public class CheckHostsButton extends BaseProcessActionHandler {
  private static final Logger log4j = LogManager.getLogger(CheckHostsButton.class);
  private static final String ETENDO_HOST = "ETENDO_HOST";
  private static final String COPILOT_HOST = "COPILOT_HOST";
  private static final String ETENDO_HOST_DOCKER = "ETENDO_HOST_DOCKER";
  public static final String CONTENT_TYPE = "application/json";
  public static final String SUCCESS = "success";
  public static final String SUCESSFULLY_VERIFIED = " sucessfully verified. ";
  public static final String VERIFICATION_FAILED = " verification failed. ";
  public static final String ETCOP_HOST_CHECK = "ETCOP_HOST_CHECK";

  /**
   * Executes the process to check the Etendo and Copilot hosts.
   *
   * @param parameters
   *     The parameters received for the process execution.
   * @param content
   *     The content of the request.
   * @return A JSON object containing the results of the host checks and messages.
   */
  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    JSONObject checks;
    try {
      String token = CopilotUtils.generateEtendoToken();
      if (token == null) {
        log4j.error("Token is null. Unable to proceed with host checks.");
        throw new OBException("Error when generating token.");
      }
      checks = checkEtendoHost(token);
      checks = checkCopilotHost(token, checks);
      returnMessage(checks);
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
    return checks;
  }



  /**
   * Checks the Etendo host connectivity and configuration.
   *
   * @param token
   *     The security token used for authentication.
   * @throws IOException
   *     If an error occurs while connecting to the Etendo host.
   */
  private JSONObject checkEtendoHost(String token) throws IOException, JSONException {
    String etendoHost = CopilotUtils.getEtendoHost();
    HttpURLConnection connection = null;
    JSONObject checks = new JSONObject();
    try {
      String url = etendoHost + "/sws/copilot/configcheck";
      connection = createConnection(url, token);
      int responseCode = connection.getResponseCode();
      checks.put(ETENDO_HOST, responseCode == 200);
      return checks;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Checks the Copilot host connectivity and configuration.
   *
   * @param token
   *     The security token used for authentication.
   * @param checks
   *     The JSON object to store the results of the check.
   * @throws IOException
   *     If an error occurs while connecting to the Copilot host.
   * @throws JSONException
   *     If an error occurs while processing JSON responses.
   */
  private JSONObject checkCopilotHost(String token, JSONObject checks) throws IOException, JSONException {
    String copilotHost = CopilotUtils.getCopilotHost();
    String copilotPort = CopilotUtils.getCopilotPort();

    HttpURLConnection pythonConnection = null;
    try {
      String pythonUrl = "http://" + copilotHost + ":" + copilotPort + "/checkCopilotHost";
      pythonConnection = createConnection(pythonUrl, token);

      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(pythonConnection.getInputStream()))) {
        int responsePythonCode = pythonConnection.getResponseCode();
        if (responsePythonCode != HttpServletResponse.SC_OK) {
          checks.put(COPILOT_HOST, false);
          return checks;
        }

        log4j.debug("COPILOT_HOST is working.");
        checks.put(COPILOT_HOST, true);

        StringBuilder responseBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          responseBuilder.append(line);
        }
        String responseBody = responseBuilder.toString();

        boolean status = StringUtils.contains(responseBody, SUCCESS);
        log4j.debug("ETENDO_HOST_DOCKER is " + (status ? "" : "not") + " working.");
        checks.put(ETENDO_HOST_DOCKER, status);
        return checks;
      }
    } catch (Exception e) {
      throw new OBException("Error when checking COPILOT_HOST and ETENDO_HOST_DOCKER: " + e.getMessage());
    } finally {
      if (pythonConnection != null) {
        pythonConnection.disconnect();
      }
    }
  }

  /**
   * Constructs and sends a success message containing the results of the host checks.
   *
   * @param checks
   *     The JSON object containing the results to be included in the success message.
   * @throws JSONException
   *     If an error occurs while constructing the JSON response.
   */
  private void returnMessage(JSONObject checks) throws JSONException {
    String messageType = SUCCESS;
    if (!checks.optBoolean(ETENDO_HOST, false) ||
        !checks.optBoolean(COPILOT_HOST, false) ||
        !checks.optBoolean(ETENDO_HOST_DOCKER, false)) {
      messageType = "error";
    }

    StringBuilder message = new StringBuilder();
    String ETENDO_HOST_STATUS = VERIFICATION_FAILED;
    String COPILOT_HOST_STATUS = VERIFICATION_FAILED;
    String ETENDO_HOST_DOCKER_STATUS = " not verified.";

    if (checks.optBoolean(ETENDO_HOST)) {
      ETENDO_HOST_STATUS = SUCESSFULLY_VERIFIED;
    }
    if (checks.optBoolean(COPILOT_HOST)) {
      COPILOT_HOST_STATUS = SUCESSFULLY_VERIFIED;
    }
    if (checks.has(ETENDO_HOST_DOCKER)) {
      if (checks.optBoolean(ETENDO_HOST_DOCKER)) {
        ETENDO_HOST_DOCKER_STATUS = SUCESSFULLY_VERIFIED;
      } else {
        ETENDO_HOST_DOCKER_STATUS = VERIFICATION_FAILED;
      }
    }

    message.append(String.format(OBMessageUtils.messageBD(ETCOP_HOST_CHECK), ETENDO_HOST, ETENDO_HOST_STATUS));
    message.append(String.format(OBMessageUtils.messageBD(ETCOP_HOST_CHECK), COPILOT_HOST, COPILOT_HOST_STATUS));
    message.append(
        String.format(OBMessageUtils.messageBD(ETCOP_HOST_CHECK), ETENDO_HOST_DOCKER, ETENDO_HOST_DOCKER_STATUS));

    JSONArray actions = new JSONArray();
    JSONObject showMsgInProcessView = new JSONObject();
    showMsgInProcessView.put("msgType", messageType);
    showMsgInProcessView.put("msgText", message);
    showMsgInProcessView.put("wait", true);
    JSONObject showMsgInProcessViewAction = new JSONObject();
    showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
    actions.put(showMsgInProcessViewAction);
    checks.put("responseActions", actions);
  }

  /**
   * Creates and configures an HttpURLConnection for a given URL and token.
   *
   * @param urlString
   *     The URL to connect to.
   * @param token
   *     The security token for authentication.
   * @return Configured HttpURLConnection.
   * @throws IOException
   *     If an error occurs while creating the connection.
   */
  protected HttpURLConnection createConnection(String urlString, String token) throws IOException {
    URL url = new URL(urlString);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Authorization", "Bearer " + token);
    connection.setRequestProperty("Content-Type", CONTENT_TYPE);
    connection.setRequestProperty("Accept", CONTENT_TYPE);
    return connection;
  }
}
