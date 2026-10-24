package com.etendoerp.copilot.modulescript;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;

/** Creates KB storage only when pgvector is already enabled; its absence never blocks update.database. */
public class ProvisionKnowledgeBaseSchema extends ModuleScript {
  private static final Logger LOG = LogManager.getLogger(ProvisionKnowledgeBaseSchema.class);

  @Override
  public void execute() {
    ConnectionProvider cp = getConnectionProvider();
    try {
      boolean provisioned = KnowledgeBaseSchemaProvisioning.provision(new KnowledgeBaseSchemaProvisioning.Executor() {
        @Override public boolean hasPgVector() throws Exception {
          PreparedStatement statement = cp.getPreparedStatement(
              "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname='vector')");
          try (ResultSet result = statement.executeQuery()) {
            return result.next() && result.getBoolean(1);
          } finally {
            cp.releasePreparedStatement(statement);
          }
        }
        @Override public void execute(String sql) throws Exception {
          PreparedStatement statement = cp.getPreparedStatement(sql);
          try {
            statement.execute();
          } finally {
            cp.releasePreparedStatement(statement);
          }
        }
      });
      if (provisioned) LOG.info("Copilot knowledge-base pgvector table and indexes are provisioned.");
      else LOG.info("Copilot KB storage was not provisioned: PostgreSQL pgvector is not installed. update.database continues; enable pgvector and rerun update.database to use Java knowledge bases.");
    } catch (Exception e) {
      LOG.warn("Copilot KB storage could not be provisioned. update.database continues; verify pgvector availability and database DDL permissions, then rerun update.database.", e);
    }
  }
}
