package com.etendoerp.copilot.modulescript;

import org.openbravo.modulescript.ModuleScript;

import java.sql.PreparedStatement;

import org.openbravo.database.ConnectionProvider;

/**
 * Module script for setting up the Copilot module.
 * <p>
 * This script updates the `etcop_file` and `etcop_app` tables to set default values
 * for `skip_splitting` and `sync_status` columns where they are null.
 */
public class CopilotSetup extends ModuleScript {

  /**
   * Executes the module script.
   * <p>
   * This method updates the `etcop_file` table to set `skip_splitting` to 'N' where it is null,
   * and updates the `etcop_app` table to set `sync_status` to 'PS' where it is null.
   */
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();

      // Update the etcop_file table to set skip_splitting to 'N' where it is null
      PreparedStatement ps = cp.getPreparedStatement(
          "update etcop_file set skip_splitting = 'N' where skip_splitting is null;");
      ps.executeUpdate();

      // Update the etcop_app table to set sync_status to 'PS' where it is null
      PreparedStatement ps2 = cp.getPreparedStatement(
          "UPDATE etcop_app SET sync_status = 'PS' WHERE sync_status IS NULL;");
      ps2.executeUpdate();
    } catch (Exception e) {
      handleError(e);
    }
  }
}
