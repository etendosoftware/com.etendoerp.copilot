package com.etendoerp.copilot.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;


import com.etendoerp.copilot.rest.RestService;
import com.etendoerp.copilot.util.CopilotUtils;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

public class CheckHostsButton extends BaseProcessActionHandler {
    private static final Logger log4j = LogManager.getLogger(RestService.class);
    @Override
    protected JSONObject doExecute(Map<String, Object> parameters, String content) throws JSONException {
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
            log4j.error("Fail obtaining token or a verifying host.", e);
        }
        returnSuccessMsg(result);
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
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            String etendoHostMessage;
            if (responseCode == 200) {
                log4j.info("ETENDO_HOST successfully verified.");
                etendoHostMessage = "ETENDO_HOST successfully verified.";
            } else {
                etendoHostMessage = "Error verifying ETENDO_HOST: Error " + responseCode;
                log4j.error("Error verifying ETENDO_HOST");
            }
            result.put("etendo_host", etendoHostMessage);
        } catch (Exception e) {
            log4j.error("Error verifying ETENDO_HOST: ", e);
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
            pythonConnection.setRequestProperty("Content-Type", "application/json");
            pythonConnection.setRequestProperty("Accept", "application/json");

            int responsePythonCode = pythonConnection.getResponseCode();
            if (responsePythonCode == HttpServletResponse.SC_OK) {
                log4j.info("COPILOT_HOST successfully verified.");
                result.put("copilot_host", "COPILOT_HOST successfully verified.");
                BufferedReader reader;
                    reader = new BufferedReader(new InputStreamReader(pythonConnection.getInputStream()));

                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    String responseBody = responseBuilder.toString();

                    if (responseBody.contains("ETENDO_HOST_DOCKER successfully verified")) {
                        log4j.info("ETENDO_HOST_DOCKER successfully verified.");
                        result.put("etendo_host_docker", "ETENDO_HOST_DOCKER successfully verified.");
                    } else {
                        log4j.error("Error verifying ETENDO_HOST_DOCKER.");
                        result.put("etendo_host_docker", "Error verifying ETENDO_HOST_DOCKER.");
                    }
            } else {
                result.put("copilot_host", "Error verifying COPILOT_HOST: Error " + responsePythonCode);
                result.put("etendo_host_docker", "ETENDO_HOST_DOCKER not verified.");
                log4j.error("Error verifying COPILOT_HOST: Error " + responsePythonCode);
            }
        } catch (Exception e) {
            log4j.error("Error verifying COPILOT_HOST: ", e);
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
        showMsgInProcessView.put("msgText", result.getString("etendo_host") + "\n" +
                                            result.getString("copilot_host") + "\n" +
                                            result.getString("etendo_host_docker"));
        showMsgInProcessView.put("wait", true);
        JSONObject showMsgInProcessViewAction = new JSONObject();
        showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);
        actions.put(showMsgInProcessViewAction);
        result.put("responseActions", actions);
    }
}