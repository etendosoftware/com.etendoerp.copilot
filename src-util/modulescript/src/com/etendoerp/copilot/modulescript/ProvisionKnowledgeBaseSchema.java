package com.etendoerp.copilot.modulescript;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;

/** Creates Copilot's optional KB storage when a Java KB can use it. */
public class ProvisionKnowledgeBaseSchema extends ModuleScript {
  private static final Logger LOG = LogManager.getLogger(ProvisionKnowledgeBaseSchema.class);

  @Override
  public void execute() {
    ConnectionProvider cp = getConnectionProvider();
    try {
      if (!needsKnowledgeBase(cp)) {
        LOG.info("Copilot KB storage is not needed; update.database continues.");
        return;
      }
      if (!isInstalled(cp)) {
        if (!isAvailable(cp)) {
          LOG.info("Copilot KB storage was not provisioned: PostgreSQL pgvector is unavailable. update.database continues.");
          return;
        }
        if (!hasDbExtended(cp)) {
          // DBSM cannot read some pgvector functions (unnamed parameters) and update.database then fails
          // with a NullPointerException. com.etendoerp.db.extended excludes them from the model, so
          // enabling the extension without it would break every later update.database.
          LOG.warn("pgvector is available but Copilot will not enable it: install the module "
              + "com.etendoerp.db.extended first (it keeps pgvector objects out of DBSM), or enable the "
              + "extension yourself afterwards. update.database continues.");
          return;
        }
        createExtension(cp);
      }
      boolean provisioned = KnowledgeBaseSchemaProvisioning.provision(new KnowledgeBaseSchemaProvisioning.Executor() {
        @Override public boolean hasPgVector() throws Exception {
          return isInstalled(cp);
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
    } catch (Exception e) {
      LOG.warn("Copilot KB storage could not be provisioned. update.database continues; verify pgvector availability and database DDL permissions, then rerun update.database. Reason: {}", e.getMessage(), e);
    }
  }

  private static boolean needsKnowledgeBase(ConnectionProvider cp) throws Exception {
    Properties properties = org.openbravo.base.session.OBPropertiesProvider.getInstance().getOpenbravoProperties();
    if ("java".equalsIgnoreCase(properties.getProperty("copilot.engine", "python"))) return true;
    try (PreparedStatement statement = cp.getPreparedStatement(
        "SELECT EXISTS (SELECT 1 FROM etcop_app_source)")) {
      try (ResultSet result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      } finally {
        cp.releasePreparedStatement(statement);
      }
    }
  }

  private static boolean isInstalled(ConnectionProvider cp) throws Exception {
    try (PreparedStatement statement = cp.getPreparedStatement(
        "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname='vector')")) {
      try (ResultSet result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      } finally {
        cp.releasePreparedStatement(statement);
      }
    }
  }

  private static boolean hasDbExtended(ConnectionProvider cp) throws Exception {
    try (PreparedStatement statement = cp.getPreparedStatement(
        "SELECT EXISTS (SELECT 1 FROM ad_module WHERE javapackage='com.etendoerp.db.extended')")) {
      try (ResultSet result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      } finally {
        cp.releasePreparedStatement(statement);
      }
    }
  }

  private static boolean isAvailable(ConnectionProvider cp) throws Exception {
    try (PreparedStatement statement = cp.getPreparedStatement(
        "SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name='vector')")) {
      try (ResultSet result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      } finally {
        cp.releasePreparedStatement(statement);
      }
    }
  }

  private static void createExtension(ConnectionProvider cp) throws Exception {
    Properties properties = org.openbravo.base.session.OBPropertiesProvider.getInstance().getOpenbravoProperties();
    Exception systemFailure = null;
    String systemUser = properties.getProperty("bbdd.systemUser");
    String systemPassword = properties.getProperty("bbdd.systemPassword");
    if (systemUser != null && !systemUser.trim().isEmpty()
        && systemPassword != null && !systemPassword.trim().isEmpty()) {
      try (Connection connection = connection(properties, systemUser, systemPassword);
           Statement statement = connection.createStatement()) {
        statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
        LOG.info("Created the pgvector extension as the database system user.");
        return;
      } catch (Exception e) {
        systemFailure = e;
      }
    }
    try {
      try (Connection connection = connection(properties, properties.getProperty("bbdd.user"),
          properties.getProperty("bbdd.password"));
           Statement statement = connection.createStatement()) {
        statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
        LOG.info("Created the pgvector extension as the application user.");
      }
    } catch (Exception applicationFailure) {
      if (systemFailure != null) applicationFailure.addSuppressed(systemFailure);
      throw new Exception("CREATE EXTENSION vector failed as system and application users: "
          + applicationFailure.getMessage(), applicationFailure);
    }
  }

  private static Connection connection(Properties properties, String user, String password) throws Exception {
    String url = properties.getProperty("bbdd.url");
    String sid = properties.getProperty("bbdd.sid");
    if (url == null || sid == null || user == null || password == null) {
      throw new IllegalStateException("Database connection properties are incomplete");
    }
    Connection connection = DriverManager.getConnection(url + "/" + sid, user, password);
    connection.setAutoCommit(true);
    return connection;
  }
}
