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

public class CheckHostsButton extends BaseProcessActionHandler {
    private static final Logger log4j = LogManager.getLogger(CheckHostsButton.class);
    private static final String ETENDO_HOST = "ETENDO_HOST";
    private static final String COPILOT_HOST = "COPILOT_HOST";
    private static final String ETENDO_HOST_DOCKER = "ETENDO_HOST_DOCKER";
    public static final String CONTENT_TYPE = "application/json";
    public static final String ERROR_COPILOT_HOST = "Error verifying COPILOT_HOST:";
    public static final String ERROR_ETENDO_HOST_DOCKER = "ETENDO_HOST_DOCKER not verified.";
    public static final String ERROR = " Error ";
    public static final String COPILOT_HOST_SUCCESS = "COPILOT_HOST successfully verified.";
    public static final String ETENDO_HOST_DOCKER_SUCCESS = "ETENDO_HOST_DOCKER successfully verified.";
    public static final String ERROR_ETENDO_HOST = "Error verifying ETENDO_HOST";
    public static final String ETENDO_HOST_SUCCESS = "ETENDO_HOST successfully verified.";

    @Override
    protected JSONObject doExecute(Map<String, Object> parameters, String content) {
        JSONObject result = new JSONObject();
        try {
            String token = getSecurityToken();
            if (token == null) {
                log4j.error("Token is null. Unable to proceed with host checks.");
                result.put("success", false);
                result.put("message", "Token is null. Unable to proceed with host checks.");
            } else {
                checkEtendoHost(token, result);
                checkCopilotHost(token, result);
                result.put("success", true);
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

    private static String getSecurityToken() throws Exception {
        OBContext context = OBContext.getOBContext();
        Role role = OBDal.getInstance().get(Role.class, context.getRole().getId());
        User user = OBDal.getInstance().get(User.class, context.getUser().getId());

        return SecureWebServicesUtils.generateToken(user, role);
    }

    private void checkEtendoHost(String token, JSONObject result) throws IOException {
        String etendoHost = CopilotUtils.getEtendoHost();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(etendoHost + "/sws/copilot/configcheck");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", CONTENT_TYPE);
            connection.setRequestProperty("Accept", CONTENT_TYPE);

            int responseCode = connection.getResponseCode();
            String etendoHostMessage;
            if (responseCode == 200) {
                log4j.info(ETENDO_HOST_SUCCESS);
                etendoHostMessage = ETENDO_HOST_SUCCESS;
            } else {
                etendoHostMessage = ERROR_ETENDO_HOST + ": Error " + responseCode;
                log4j.error(ERROR_ETENDO_HOST);
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

    private void checkCopilotHost(String token, JSONObject result) throws IOException, JSONException {
        String copilotHost = CopilotUtils.getCopilotHost();
        String copilotPort = CopilotUtils.getCopilotPort();

        HttpURLConnection pythonConnection = null;
        try {
            URL pythonUrl = new URL("http://" + copilotHost + ":" + copilotPort + "/checkCopilotHost");
            pythonConnection = (HttpURLConnection) pythonUrl.openConnection();
            pythonConnection.setRequestMethod("POST");
            pythonConnection.setDoOutput(true);
            pythonConnection.setRequestProperty("Authorization", "Bearer " + token);
            pythonConnection.setRequestProperty("Content-Type", CONTENT_TYPE);
            pythonConnection.setRequestProperty("Accept", CONTENT_TYPE);

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
                        log4j.error(CheckHostsButton.ERROR_ETENDO_HOST_DOCKER);
                        result.put(ETENDO_HOST_DOCKER, CheckHostsButton.ERROR_ETENDO_HOST_DOCKER);
                    }
                } else {
                    result.put(COPILOT_HOST, ERROR_COPILOT_HOST + ERROR + responsePythonCode);
                    result.put(ETENDO_HOST_DOCKER, ERROR_ETENDO_HOST_DOCKER);
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
            result.put(ETENDO_HOST_DOCKER, ERROR_ETENDO_HOST_DOCKER);
            log4j.error(ERROR_ETENDO_HOST_DOCKER);
        } finally {
            if (pythonConnection != null) {
                pythonConnection.disconnect();
            }
        }
    }

    private void returnSuccessMsg(JSONObject result) throws JSONException {
        // Message in tab from where the process is executed
        JSONArray actions = new JSONArray();
        JSONObject showMsgInProcessView = new JSONObject();
        showMsgInProcessView.put("msgType", "info");
        showMsgInProcessView.put("msgText", result.getString(ETENDO_HOST) + "\n" +
                                            result.getString(COPILOT_HOST) + "\n" +
                                            result.getString(ETENDO_HOST_DOCKER));
        showMsgInProcessView.put("wait", true);
        JSONObject showMsgInProcessViewAction = new JSONObject();
        showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
        actions.put(showMsgInProcessViewAction);
        result.put("responseActions", actions);
    }
}