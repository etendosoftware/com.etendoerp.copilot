package com.etendoerp.copilot.modulescript;

import org.openbravo.modulescript.ModuleScript;



import java.sql.PreparedStatement;


import org.openbravo.database.ConnectionProvider;


public class CopilotSetup extends ModuleScript {
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();
      PreparedStatement ps = cp
          .getPreparedStatement("update etcop_file set skip_splitting = 'N' where skip_splitting is null;");
      ps.executeUpdate();
    } catch (Exception e) {
      handleError(e);
    }
  }
}
