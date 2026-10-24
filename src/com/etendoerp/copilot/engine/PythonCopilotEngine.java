package com.etendoerp.copilot.engine;

import static com.etendoerp.copilot.util.CopilotUtils.readPropertyWithLegacyCompatibility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.rest.RestServiceUtil;

/**
 * Default engine: forwards the request to the Python Copilot service over HTTP.
 * <p>
 * This is the historical behaviour of {@code RestServiceUtil.sendRequestToCopilot}, moved here
 * unchanged so that introducing {@link CopilotEngine} carries no behavioural risk.
 */
public class PythonCopilotEngine implements CopilotEngine {

  private static final Logger log = LogManager.getLogger(PythonCopilotEngine.class);
  private static final java.util.concurrent.atomic.AtomicBoolean DEPRECATION_LOGGED =
      new java.util.concurrent.atomic.AtomicBoolean();

  @Override
  public JSONObject execute(boolean asyncRequest, HttpServletResponse queue, JSONObject request,
      CopilotApp app) throws IOException, JSONException {
    if (DEPRECATION_LOGGED.compareAndSet(false, true)) {
      log.warn("The Python Copilot engine (copilot.engine=python) is DEPRECATED and will be removed; "
          + "set copilot.engine=java to run agents in-process. Agents the Java engine cannot serve yet "
          + "keep falling back to Python and are listed by the 'Copilot engine: python ... (falling back' "
          + "log lines.");
    }
    var properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String copilotPort = readPropertyWithLegacyCompatibility(properties, "copilot.port", "5005");
    String copilotHost = readPropertyWithLegacyCompatibility(properties, "copilot.host", "localhost");
    String endpoint = RestServiceUtil.determineEndpoint(asyncRequest, app);

    if (log.isDebugEnabled()) {
      JSONObject logRequest = new JSONObject(request.toString());
      JSONObject extraInfo = logRequest.optJSONObject("extra_info");
      if (extraInfo != null) {
        JSONObject llmCredentials = extraInfo.optJSONObject("llm");
        if (llmCredentials != null && llmCredentials.has("api_key")) {
          llmCredentials.put("api_key", "[REDACTED]");
        }
      }
      log.debug(logRequest.toString(2));
    }

    URL url = new URL(String.format("http://%s:%s%s", copilotHost, copilotPort, endpoint));
    try {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.getOutputStream().write(request.toString().getBytes());

      if (asyncRequest) {
        return RestServiceUtil.serverSideEvents(asyncRequest, queue, connection.getInputStream());
      }
      String responseFromCopilot = new BufferedReader(
          new InputStreamReader(connection.getInputStream())).lines().collect(Collectors.joining("\n"));
      return new JSONObject(responseFromCopilot);
    } catch (Exception e) {
      log.error(e);
      Thread.currentThread().interrupt();
      throw new OBException(OBMessageUtils.messageBD("ETCOP_ConnError"));
    }
  }
}
