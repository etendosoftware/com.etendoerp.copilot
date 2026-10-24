package com.etendoerp.copilot.kb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;

/** pgvector-backed document store. All SQL is parameterized and scope is applied in SQL. */
public final class KnowledgeBaseStore {
  private static final String TABLE = "etcop_kb_chunk";
  private final SqlExecutor executor;

  public KnowledgeBaseStore() {
    this(new DalSqlExecutor());
  }

  public KnowledgeBaseStore(SqlExecutor executor) {
    this.executor = executor;
  }

  public void ensureReady() {
    try {
      if (!executor.hasPgVector() || !executor.hasTable()) {
        throw new OBException("pgvector not available / KB table not provisioned, run update.database.");
      }
    } catch (SQLException e) {
      throw new OBException("pgvector not available / KB table not provisioned, run update.database.", e);
    }
  }

  public void upsert(String chunkId, String appId, String clientId, String namespace, String sourceId,
      String sourceName, String content, String hash, float[] embedding) {
    String sql = "INSERT INTO " + TABLE + " (chunk_id,app_id,ad_client_id,namespace,source_id,source_name,content,content_hash,embedding,purge) "
        + "VALUES (?,?,?,?,?,?,?,?,CAST(? AS vector),false) ON CONFLICT (chunk_id) DO UPDATE SET "
        + "content=EXCLUDED.content,content_hash=EXCLUDED.content_hash,embedding=EXCLUDED.embedding,purge=false,source_name=EXCLUDED.source_name";
    try {
      executor.update(sql, chunkId, appId, clientId, namespace, sourceId, sourceName, content, hash, vectorText(embedding));
    } catch (SQLException e) {
      throw new OBException("Could not write the knowledge-base chunk. Confirm pgvector is enabled and the model dimensions are consistent.", e);
    }
  }

  public List<Match> search(String appId, String clientId, String namespace, float[] query, int limit) {
    KnowledgeBaseSql.Scope scope = KnowledgeBaseSql.scope(appId, clientId);
    String sql = KnowledgeBaseSql.searchSql();
    try {
      List<Object[]> rows = executor.query(sql, vectorText(query), appId, namespace,
          scope.clientIds().get(0), scope.clientIds().size() > 1 ? scope.clientIds().get(1) : scope.clientIds().get(0),
          vectorText(query), limit);
      List<Match> matches = new ArrayList<>();
      for (Object[] row : rows) matches.add(new Match((String) row[0], (String) row[1], (String) row[2], ((Number) row[3]).doubleValue()));
      return matches;
    } catch (SQLException e) {
      throw new OBException("Could not search the knowledge base. Confirm pgvector is enabled and the embedding model matches the index.", e);
    }
  }

  public boolean hasSource(String appId, String clientId, String namespace, String sourceId) {
    try {
      return !executor.query("SELECT content,source_name,source_id,0 FROM " + TABLE
          + " WHERE app_id=? AND ad_client_id=? AND namespace=? AND source_id=? LIMIT 1",
          appId, clientId, namespace, sourceId).isEmpty();
    } catch (SQLException e) { throw new OBException("Could not look up the stored knowledge-base source.", e); }
  }
  public void deleteSource(String appId, String clientId, String namespace, String sourceId) {
    try {
      executor.update("DELETE FROM " + TABLE + " WHERE app_id=? AND ad_client_id=? AND namespace=? AND source_id=?",
          appId, clientId, namespace, sourceId);
    } catch (SQLException e) { throw new OBException("Could not delete the stored knowledge-base source.", e); }
  }
  public void markSourcePurge(String appId, String clientId, String namespace, String sourceId) {
    try {
      executor.update("UPDATE " + TABLE + " SET purge=true WHERE app_id=? AND ad_client_id=? AND namespace=? AND source_id=?",
          appId, clientId, namespace, sourceId);
    } catch (SQLException e) { throw new OBException("Could not mark stale knowledge-base chunks.", e); }
  }

  public void reset(String appId, String clientId, String namespace) {
    try { executor.update("UPDATE " + TABLE + " SET purge=true WHERE app_id=? AND ad_client_id=? AND namespace=?",
        appId, clientId, namespace); }
    catch (SQLException e) { throw new OBException("Could not reset the knowledge base.", e); }
  }

  public void purge(String appId, String clientId, String namespace) {
    try { executor.update("DELETE FROM " + TABLE + " WHERE app_id=? AND ad_client_id=? AND namespace=? AND purge=true",
        appId, clientId, namespace); }
    catch (SQLException e) { throw new OBException("Could not purge stale knowledge-base chunks.", e); }
  }

  private static String vectorText(float[] values) {
    StringBuilder result = new StringBuilder("[");
    for (int i = 0; i < values.length; i++) {
      if (i > 0) result.append(',');
      result.append(Float.toString(values[i]));
    }
    return result.append(']').toString();
  }

  public interface SqlExecutor {
    boolean hasPgVector() throws SQLException;
    boolean hasTable() throws SQLException;
    int update(String sql, Object... values) throws SQLException;
    List<Object[]> query(String sql, Object... values) throws SQLException;
  }

  private static final class DalSqlExecutor implements SqlExecutor {
    private Connection connection() { return OBDal.getInstance().getConnection(); }
    public boolean hasPgVector() throws SQLException {
      try (PreparedStatement statement = connection().prepareStatement("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname='vector')");
           ResultSet result = statement.executeQuery()) { return result.next() && result.getBoolean(1); }
    }
    public boolean hasTable() throws SQLException {
      try (PreparedStatement statement = connection().prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
        statement.setString(1, TABLE);
        try (ResultSet result = statement.executeQuery()) { return result.next() && result.getBoolean(1); }
      }
    }
    public int update(String sql, Object... values) throws SQLException {
      try (PreparedStatement statement = connection().prepareStatement(sql)) {
        bind(statement, values); return statement.executeUpdate();
      }
    }
    public List<Object[]> query(String sql, Object... values) throws SQLException {
      try (PreparedStatement statement = connection().prepareStatement(sql)) {
        bind(statement, values);
        try (ResultSet result = statement.executeQuery()) {
          List<Object[]> rows = new ArrayList<>();
          while (result.next()) rows.add(new Object[] {result.getString(1), result.getString(2), result.getString(3), result.getObject(4)});
          return rows;
        }
      }
    }
    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
      for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
    }
  }

  public static final class Match {
    private final String content, sourceName, sourceId;
    private final double distance;
    Match(String content, String sourceName, String sourceId, double distance) {
      this.content = content; this.sourceName = sourceName; this.sourceId = sourceId; this.distance = distance;
    }
    public String content() { return content; }
    public String sourceName() { return sourceName; }
    public String sourceId() { return sourceId; }
    public double distance() { return distance; }
  }
}
