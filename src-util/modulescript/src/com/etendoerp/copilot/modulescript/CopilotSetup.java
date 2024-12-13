package com.etendoerp.copilot.modulescript;

import org.openbravo.modulescript.ModuleScript;


import java.sql.PreparedStatement;


import org.openbravo.database.ConnectionProvider;


public class CopilotSetup extends ModuleScript {
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();
      // Mark all assistants as pending sync if they are not already synced
      PreparedStatement ps = cp
          .getPreparedStatement("UPDATE etcop_app SET sync_status = 'PS' WHERE sync_status IS NULL;");
      ps.executeUpdate();
    } catch (Exception e) {
      handleError(e);
    }
  }
}
