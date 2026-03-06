package com.etendoerp.copilot.modulescript;

import java.sql.PreparedStatement;

import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;

/**
 * Module script that marks agents with "Sync on startup" enabled as Pending Synchronization
 * during update.database.
 * <p>
 * This ensures that when Tomcat starts after an update.database, {@code CopilotSyncStartup}
 * will re-sync those agents because their status will be "PS" (Pending Synchronization).
 * <p>
 * This script runs on every update.database (no execution limits).
 */
public class MarkSyncStartupAgentsPending extends ModuleScript {

  @Override
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();

      // Update existing etcop_app_info records to 'PS' for agents with sync_startup = 'Y'
      PreparedStatement updatePs = cp.getPreparedStatement(
          "UPDATE etcop_app_info SET sync_status = 'PS', updated = NOW() "
              + "WHERE etcop_app_id IN ("
              + "  SELECT etcop_app_id FROM etcop_app WHERE sync_startup = 'Y'"
              + ")");
      updatePs.executeUpdate();

      // Insert etcop_app_info records for agents that have sync_startup = 'Y'
      // but no etcop_app_info record yet
      PreparedStatement insertPs = cp.getPreparedStatement(
          "INSERT INTO etcop_app_info "
              + "(etcop_app_info_id, ad_client_id, ad_org_id, isactive, "
              + " created, createdby, updated, updatedby, etcop_app_id, sync_status) "
              + "SELECT get_uuid(), a.ad_client_id, a.ad_org_id, 'Y', "
              + "       NOW(), '0', NOW(), '0', a.etcop_app_id, 'PS' "
              + "FROM etcop_app a "
              + "WHERE a.sync_startup = 'Y' "
              + "  AND NOT EXISTS ("
              + "    SELECT 1 FROM etcop_app_info ai WHERE ai.etcop_app_id = a.etcop_app_id"
              + "  )");
      insertPs.executeUpdate();

    } catch (Exception e) {
      handleError(e);
    }
  }
}
