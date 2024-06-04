package com.etendoerp.copilot;

import java.sql.PreparedStatement;

import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;

public class AddTreeNodes extends ModuleScript {

  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();
      StringBuilder sb = new StringBuilder();
      sb.append("DO $$\n");
      sb.append("DECLARE rec RECORD;\n");
      sb.append(
          "max_seqno INTEGER;\n");
      sb.append("begin\n");
    // get the max seqno
      sb.append("SELECT MAX(seqno) INTO max_seqno\n");
      sb.append(
          "FROM ad_treenode at2\n");
      sb.append("WHERE at2.ad_tree_id = 'D9D1766B86694B849978AF1C99C06DBB';\n");
      sb.append(
          "FOR rec IN\n");
      // Detect Copilot Apps out of the tree\n
      sb.append("SELECT *\n");
      sb.append(
          "FROM etcop_app app\n");
      sb.append("WHERE NOT EXISTS (\n");
      sb.append("        SELECT 1\n");
      sb.append(
          "        FROM ad_treenode at2\n");
      sb.append(
          "        WHERE at2.ad_tree_id = 'D9D1766B86694B849978AF1C99C06DBB'\n");
      sb.append(
          "            AND at2.node_id = app.etcop_app_id\n");
      sb.append("    ) LOOP \n");
      sb.append("    \n");
      sb.append(
          "    RAISE NOTICE 'Adding Tree Node for Etcop App: %', rec.etcop_app_id;\n");
        // Insert the tree node for the apps, to avoid not having them in the tree
          sb.append(
          "INSERT INTO ad_treenode (\n");
      sb.append("        ad_treenode_id,\n");
      sb.append("        ad_tree_id,\n");
      sb.append(
          "        node_id,\n");
      sb.append("        ad_client_id,\n");
      sb.append("        ad_org_id,\n");
      sb.append(
          "        isactive,\n");
      sb.append("        created,\n");
      sb.append("        createdby,\n");
      sb.append(
          "        updated,\n");
      sb.append("        updatedby,\n");
      sb.append("        parent_id,\n");
      sb.append(
          "        seqno\n");
      sb.append("    )\n");
      sb.append("VALUES(\n");
      sb.append("        get_uuid(),\n");
      sb.append(
          "        'D9D1766B86694B849978AF1C99C06DBB',\n");
      sb.append("        rec.etcop_app_id,\n");
      sb.append(
          "        rec.ad_client_id,\n");
      sb.append("        '0',\n");
      sb.append("        'Y',\n");
      sb.append(
          "        now(),\n");
      sb.append("        '100',\n");
      sb.append("        now(),\n");
      sb.append("        '100',\n");
      sb.append(
          "        '0',\n");
      sb.append("        max_seqno + 10\n");
      sb.append("    );\n");
      sb.append(
          "max_seqno := max_seqno + 10;\n");
      sb.append("end loop;\n");
      sb.append("END $$");
      PreparedStatement ps = cp.getPreparedStatement(sb.toString());
      ps.executeUpdate();
    } catch (Exception e) {
      handleError(e);
    }
  }

}
