package com.etendoerp.copilot.startup;

import java.sql.PreparedStatement;

import javax.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.client.kernel.ApplicationInitializer;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.service.db.DalConnectionProvider;

/** Keeps the AI API Keys window restricted to the System Administrator role. */
@ApplicationScoped
@ComponentProvider.Qualifier("providerApiKeyWindowSecurity")
public class ProviderApiKeyWindowSecurity implements ApplicationInitializer {
  private static final Logger LOG = LogManager.getLogger(ProviderApiKeyWindowSecurity.class);
  private static final String MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";

  @Override
  public void initialize() {
    ConnectionProvider cp = new DalConnectionProvider(false);
    PreparedStatement statement = null;
    try {
      statement = cp.getPreparedStatement(
          "delete from ad_window_access where ad_window_id=(select ad_window_id from ad_window "
              + "where name='AI API Keys' and ad_module_id=?) and ad_role_id<>'0'");
      statement.setString(1, MODULE_ID);
      int removed = statement.executeUpdate();
      if (removed > 0) {
        LOG.info("Restricted the AI API Keys window to the System Administrator role.");
      }
    } catch (Exception e) {
      LOG.error("Could not enforce System Administrator-only access to the AI API Keys window.");
    } finally {
      if (statement != null) {
        try {
          cp.releasePreparedStatement(statement);
        } catch (Exception e) {
          LOG.warn("Could not release the AI API Keys access statement.");
        }
      }
    }
  }
}
