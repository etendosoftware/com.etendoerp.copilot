package com.etendoerp.copilot.modulescript;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;
import org.openbravo.modulescript.ModuleScriptExecutionLimits;
import org.openbravo.utils.FormatUtilities;
import org.openbravo.base.session.OBPropertiesProvider;

/** Imports configured legacy provider properties into encrypted, scoped API-key records. */
public class ImportLegacyProviderCredentials extends ModuleScript {
  private static final Logger LOG = LogManager.getLogger(ImportLegacyProviderCredentials.class);
  private static final String MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";
  private static final String API_KEY_NAME_PREFIX = "Imported legacy ";

  @Override
  protected ModuleScriptExecutionLimits getModuleScriptExecutionLimits() {
    return new ModuleScriptExecutionLimits(MODULE_ID, null, null);
  }

  @Override
  public void execute() {
    try {
      importConfiguredProperties(getConnectionProvider(),
          OBPropertiesProvider.getInstance().getOpenbravoProperties());
    } catch (Exception e) {
      LOG.error("Legacy provider configuration import failed; secret values were not logged.");
      handleError(e);
    }
  }

  public static void importConfiguredProperties(ConnectionProvider cp, Properties properties) throws Exception {
    String proxyUrl = trimToNull(properties.getProperty("copilot.proxy.url"));
    Map<String, LegacyCredential> credentials = new LinkedHashMap<>();
    credentials.put("openai", new LegacyCredential(properties.getProperty("openai.api.key"),
        proxyUrl != null ? proxyUrl : properties.getProperty("copilot.openai.base.url")));
    credentials.put("anthropic", new LegacyCredential(properties.getProperty("anthropic.api.key"), proxyUrl));
    credentials.put("gemini", new LegacyCredential(properties.getProperty("gemini.api.key"), proxyUrl));
    credentials.put("ollama", new LegacyCredential(null, properties.getProperty("ollama.base.url")));

    for (Map.Entry<String, LegacyCredential> entry : credentials.entrySet()) {
      LegacyCredential credential = entry.getValue();
      boolean gateway = proxyUrl != null;
      String apiKey = trimToNull(credential.apiKey());
      if (apiKey == null && gateway) {
        apiKey = "dummy";
      }
      if (apiKey != null || "ollama".equals(entry.getKey()) && trimToNull(credential.baseUrl()) != null) {
        upsertKey(cp, entry.getKey(), apiKey, trimToNull(credential.baseUrl()));
        linkModels(cp, entry.getKey());
      }
    }
    PreparedStatement restrictAccess = cp.getPreparedStatement(
        "delete from ad_window_access where ad_window_id=(select ad_window_id from ad_window "
            + "where name='AI API Keys' and ad_module_id=?) and ad_role_id<>'0'");
    try {
      restrictAccess.setString(1, MODULE_ID);
      restrictAccess.executeUpdate();
    } finally {
      cp.releasePreparedStatement(restrictAccess);
    }
    LOG.info("Legacy provider configuration import completed; secret values were not logged.");
  }

  private static void upsertKey(ConnectionProvider cp, String provider, String clearKey, String baseUrl)
      throws Exception {
    String name = API_KEY_NAME_PREFIX + provider;
    String encrypted = clearKey == null ? null : encrypt(clearKey);
    String existingId = null;
    PreparedStatement find = cp.getPreparedStatement(
        "select etcop_api_key_id from etcop_api_key where ad_client_id='0' and ad_org_id='0' "
            + "and name=? and provider_type=? order by created limit 1");
    try {
      find.setString(1, name);
      find.setString(2, provider);
      try (ResultSet rs = find.executeQuery()) {
        if (rs.next()) {
          existingId = rs.getString(1);
        }
      }
    } finally {
      cp.releasePreparedStatement(find);
    }

    if (existingId == null) {
      PreparedStatement insert = cp.getPreparedStatement(
          "insert into etcop_api_key (etcop_api_key_id, ad_client_id, ad_org_id, isactive, created, createdby, "
              + "updated, updatedby, name, provider_type, api_key, base_url, ad_module_id) "
              + "values (get_uuid(), '0', '0', 'Y', now(), '0', now(), '0', ?, ?, ?, ?, ?) ");
      try {
        insert.setString(1, name);
        insert.setString(2, provider);
        insert.setString(3, encrypted);
        insert.setString(4, baseUrl);
        insert.setString(5, MODULE_ID);
        insert.executeUpdate();
      } finally {
        cp.releasePreparedStatement(insert);
      }
    } else {
      PreparedStatement update = cp.getPreparedStatement(
          "update etcop_api_key set isactive='Y', api_key=?, base_url=?, updated=now(), updatedby='0' "
              + "where etcop_api_key_id=?");
      try {
        update.setString(1, encrypted);
        update.setString(2, baseUrl);
        update.setString(3, existingId);
        update.executeUpdate();
      } finally {
        cp.releasePreparedStatement(update);
      }
    }
  }

  private static void linkModels(ConnectionProvider cp, String provider) throws Exception {
    PreparedStatement statement = cp.getPreparedStatement(
        "update etcop_openai_model set etcop_api_key_id=(select k.etcop_api_key_id from etcop_api_key k "
            + "where k.ad_client_id='0' and k.ad_org_id='0' and k.name=? and k.provider_type=? "
            + "order by k.created limit 1), updated=now(), updatedby='0' "
            + "where lower(provider)=lower(?) and etcop_api_key_id is null");
    try {
      statement.setString(1, API_KEY_NAME_PREFIX + provider);
      statement.setString(2, provider);
      statement.setString(3, provider);
      statement.executeUpdate();
    } finally {
      cp.releasePreparedStatement(statement);
    }
  }

  private static String encrypt(String clearText) throws ServletException {
    return FormatUtilities.encryptDecrypt(clearText, true);
  }

  private static String trimToNull(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }

  private record LegacyCredential(String apiKey, String baseUrl) { }
}
