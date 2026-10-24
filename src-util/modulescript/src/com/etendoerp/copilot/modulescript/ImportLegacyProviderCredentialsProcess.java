package com.etendoerp.copilot.process;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.copilot.modulescript.ImportLegacyProviderCredentials;

/** Manually imports legacy properties into encrypted AI API Key records. */
public class ImportLegacyProviderCredentialsProcess extends BaseProcessActionHandler {
  private static final Logger LOG = LogManager.getLogger(ImportLegacyProviderCredentialsProcess.class);

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    JSONObject result = new JSONObject();
    try {
      ImportLegacyProviderCredentials.importConfiguredProperties(new DalConnectionProvider(false),
          OBPropertiesProvider.getInstance().getOpenbravoProperties());
      result.put("message", "Legacy provider configuration imported. Secret values were not logged.");
    } catch (Exception e) {
      LOG.error("Legacy provider configuration import failed; secret values were not logged.");
      try {
        result.put("error", "Legacy provider configuration could not be imported.");
      } catch (org.codehaus.jettison.json.JSONException ignored) {
        // JSONObject is newly created; no secret material is included in this fallback.
      }
    }
    return result;
  }
}
