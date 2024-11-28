package com.etendoerp.copilot.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
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
    public static final String ERROR_COPILOT_HOST = "Error verifying COPILOT_HOST:";
    public static final String ERROR_ETENDO_HOST_DOCKER = "Error verifying ETENDO_HOST_DOCKER.";
    public static final String ETENDO_HOST_DOCKER_NOT_VERIFIED = "ETENDO_HOST_DOCKER not verified.";
    public static final String ERROR = " Error ";
    public static final String COPILOT_HOST_SUCCESS = "COPILOT_HOST successfully verified.";
    public static final String ETENDO_HOST_DOCKER_SUCCESS = "ETENDO_HOST_DOCKER successfully verified.";
    public static final String ERROR_ETENDO_HOST = "Error verifying ETENDO_HOST";
    public static final String ETENDO_HOST_SUCCESS = "ETENDO_HOST successfully verified.";
    public static final String SUCCESS = "success";
    public static final Integer ERROR_NUMBER_VALUE = 0;
    public static final String ERROR_NUMBER_KEY = "ERROR_NUMBER";

    /**
     * Executes the process to check the Etendo and Copilot hosts.
     *
     * @param parameters The parameters received for the process execution.
     * @param content    The content of the request.
     * @return A JSON object containing the results of the host checks and messages.
     */
    @Override
    protected JSONObject doExecute(Map<String, Object> parameters, String content) {
        JSONObject result = new JSONObject();
        try {
            result.put(ERROR_NUMBER_KEY, ERROR_NUMBER_VALUE);
            String token = getSecurityToken();
            if (token == null) {
                log4j.error("Token is null. Unable to proceed with host checks.");
                result.put(ERROR_NUMBER_KEY, ERROR_NUMBER_VALUE + 1);
                result.put("message", "Token is null. Unable to proceed with host checks.");
            } else {
                checkEtendoHost(token, result);
                checkCopilotHost(token, result);
            }
        } catch (Exception e) {
            log4j.error("Fail obtaining token or verifying host.", e);
        }
        try {
            returnSuccessMsg(result);
        } catch (JSONException e) {
            log4j.error("Error constructing success message: ", e);
        }
        return result;
    }

    /**
     * Generates a security token based on the current user's role and credentials.
     *
     * @return A string representing the generated token.
     * @throws Exception If there is an error generating the token.
     */
    private static String getSecurityToken() throws Exception {
        OBContext context = OBContext.getOBContext();
        Role role = OBDal.getInstance().get(Role.class, context.getRole().getId());
        User user = OBDal.getInstance().get(User.class, context.getUser().getId());

        return SecureWebServicesUtils.generateToken(user, role);
    }

    /**
     * Checks the Etendo host connectivity and configuration.
     *
     * @param token  The security token used for authentication.
     * @param result The JSON object to store the results of the check.
     * @throws IOException If an error occurs while connecting to the Etendo host.
     */
    private void checkEtendoHost(String token, JSONObject result) throws IOException {
        String etendoHost = CopilotUtils.getEtendoHost();
        HttpURLConnection connection = null;
        try {
            String url = etendoHost + "/sws/copilot/configcheck";
            connection = createConnection(url, token);
            int responseCode = connection.getResponseCode();
            String etendoHostMessage;
            if (responseCode == 200) {
                log4j.info(ETENDO_HOST_SUCCESS);
                etendoHostMessage = ETENDO_HOST_SUCCESS;
            } else {
                etendoHostMessage = ERROR_ETENDO_HOST + ": " + ERROR + responseCode;
                log4j.error(ERROR_ETENDO_HOST);
                result.put(ERROR_NUMBER_KEY, ERROR_NUMBER_VALUE + 1);
            }
            result.put(ETENDO_HOST, etendoHostMessage);
        } catch (Exception e) {
            log4j.error(ERROR_ETENDO_HOST + ": ", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Checks the Copilot host connectivity and configuration.
     *
     * @param token  The security token used for authentication.
     * @param result The JSON object to store the results of the check.
     * @throws IOException   If an error occurs while connecting to the Copilot host.
     * @throws JSONException If an error occurs while processing JSON responses.
     */
    private void checkCopilotHost(String token, JSONObject result) throws IOException, JSONException {
        String copilotHost = CopilotUtils.getCopilotHost();
        String copilotPort = CopilotUtils.getCopilotPort();

        HttpURLConnection pythonConnection = null;
        try {
            String pythonUrl = "http://" + copilotHost + ":" + copilotPort + "/checkCopilotHost";
            pythonConnection = createConnection(pythonUrl, token);

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(pythonConnection.getInputStream()))) {
                int responsePythonCode = pythonConnection.getResponseCode();
                if (responsePythonCode == HttpServletResponse.SC_OK) {
                    log4j.info(COPILOT_HOST_SUCCESS);
                    result.put(COPILOT_HOST, COPILOT_HOST_SUCCESS);

                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    String responseBody = responseBuilder.toString();

                    if (StringUtils.contains(responseBody, ETENDO_HOST_DOCKER_SUCCESS)) {
                        log4j.info(ETENDO_HOST_DOCKER_SUCCESS);
                        result.put(ETENDO_HOST_DOCKER, ETENDO_HOST_DOCKER_SUCCESS);
                    } else {
                        result.put(ETENDO_HOST_DOCKER, ERROR_ETENDO_HOST_DOCKER);
                        result.put(ERROR_NUMBER_KEY, ERROR_NUMBER_VALUE + 1);
                    }

                } else {
                    result.put(COPILOT_HOST, ERROR_COPILOT_HOST + ERROR + responsePythonCode);
                    result.put(ETENDO_HOST_DOCKER, ETENDO_HOST_DOCKER_NOT_VERIFIED);
                    result.put(ERROR_NUMBER_KEY, ERROR_NUMBER_VALUE + 1);
                }
            }

        } catch (Exception e) {
            if (StringUtils.contains(e.getMessage(), "Connection refused")) {
                String message = "Connection refused. Is Copilot up?";
                log4j.error(ERROR_COPILOT_HOST + " {}", message);
                result.put(COPILOT_HOST, ERROR_COPILOT_HOST + ERROR + message);
            } else {
                result.put(COPILOT_HOST, ERROR_COPILOT_HOST + ERROR + e);
                log4j.error(ERROR_COPILOT_HOST + " ", e);
            }
            result.put(ETENDO_HOST_DOCKER, ETENDO_HOST_DOCKER_NOT_VERIFIED);
            log4j.error(ETENDO_HOST_DOCKER_NOT_VERIFIED);
            result.put(ERROR_NUMBER_KEY, ERROR_NUMBER_VALUE + 1);
        } finally {
            if (pythonConnection != null) {
                pythonConnection.disconnect();
            }
        }
    }

    /**
     * Constructs and sends a success message containing the results of the host checks.
     *
     * @param result The JSON object containing the results to be included in the success message.
     * @throws JSONException If an error occurs while constructing the JSON response.
     */
    private void returnSuccessMsg(JSONObject result) throws JSONException {
        String messageType = SUCCESS;
        if (result.getInt(ERROR_NUMBER_KEY) > 0) {
            messageType = "error";
        }
        JSONArray actions = new JSONArray();
        JSONObject showMsgInProcessView = new JSONObject();
        showMsgInProcessView.put("msgType", messageType);
        showMsgInProcessView.put("msgText", result.getString(ETENDO_HOST) + "\n" +
                                            result.getString(COPILOT_HOST) + "\n" +
                                            result.getString(ETENDO_HOST_DOCKER));
        showMsgInProcessView.put("wait", true);
        JSONObject showMsgInProcessViewAction = new JSONObject();
        showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
        actions.put(showMsgInProcessViewAction);
        result.put("responseActions", actions);
    }

    /**
     * Creates and configures an HttpURLConnection for a given URL and token.
     *
     * @param urlString The URL to connect to.
     * @param token     The security token for authentication.
     * @return Configured HttpURLConnection.
     * @throws IOException If an error occurs while creating the connection.
     */
    private HttpURLConnection createConnection(String urlString, String token) throws IOException {
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