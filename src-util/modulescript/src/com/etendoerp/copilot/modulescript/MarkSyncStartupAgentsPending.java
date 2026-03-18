/*************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF  ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and  limitations under the License.
 * All portions are Copyright (C) 2021-2025 Futit Services S.L.
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 ************************************************************************/
package com.etendoerp.copilot.modulescript;

import java.sql.PreparedStatement;

import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;
import org.openbravo.modulescript.ModuleScriptExecutionLimits;

/**
 * Module script that marks agents with "Sync on startup" enabled as Pending Synchronization
 * during update.database.
 * <p>
 * This ensures that when Tomcat starts after an update.database, {@code CopilotSyncStartup}
 * will re-sync those agents because their status will be "PS" (Pending Synchronization).
 */
public class MarkSyncStartupAgentsPending extends ModuleScript {

  private static final String MODULE_ID = "0B8480670F614D4CA99921D68BB0DD87";

  @Override
  protected ModuleScriptExecutionLimits getModuleScriptExecutionLimits() {
    return new ModuleScriptExecutionLimits(MODULE_ID, null, null);
  }

  @Override
  public void execute() {
    ConnectionProvider cp = getConnectionProvider();
    PreparedStatement updatePs = null;
    PreparedStatement insertPs = null;
    try {
      // Update existing etcop_app_info records to 'PS' for agents with sync_startup = 'Y'
      updatePs = cp.getPreparedStatement(
          "UPDATE etcop_app_info SET sync_status = 'PS', updated = NOW() "
              + "WHERE etcop_app_id IN ("
              + "  SELECT etcop_app_id FROM etcop_app WHERE sync_startup = 'Y'"
              + ")");
      updatePs.executeUpdate();

      // Insert etcop_app_info records for agents that have sync_startup = 'Y'
      // but no etcop_app_info record yet
      insertPs = cp.getPreparedStatement(
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
    } finally {
      releasePreparedStatement(cp, updatePs);
      releasePreparedStatement(cp, insertPs);
    }
  }

  private void releasePreparedStatement(ConnectionProvider cp, PreparedStatement ps) {
    if (ps != null) {
      try {
        cp.releasePreparedStatement(ps);
      } catch (Exception e) {
        handleError(e);
      }
    }
  }
}
