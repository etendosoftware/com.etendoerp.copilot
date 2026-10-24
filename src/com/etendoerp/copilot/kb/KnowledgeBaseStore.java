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
      String sourceName, String content, String hash, float[] embedding, String modelIdentity) {
    String sql = "INSERT INTO " + TABLE + " (chunk_id,app_id,ad_client_id,namespace,source_id,source_name,content,content_hash,embedding,embedding_model,embedding_dims,purge) "
        + "VALUES (?,?,?,?,?,?,?,?,CAST(? AS vector),?,?,false) ON CONFLICT (chunk_id) DO UPDATE SET "
        + "content=EXCLUDED.content,content_hash=EXCLUDED.content_hash,embedding=EXCLUDED.embedding,"
        + "embedding_model=EXCLUDED.embedding_model,embedding_dims=EXCLUDED.embedding_dims,purge=false,source_name=EXCLUDED.source_name";
    try {
      executor.update(sql, chunkId, appId, clientId, namespace, sourceId, sourceName, content, hash,
          vectorText(embedding), modelIdentity, embedding.length);
    } catch (SQLException e) {
      throw new OBException("Could not write the knowledge-base chunk. Confirm pgvector is enabled.", e);
    }
  }

  public List<Match> search(String appId, String clientId, String namespace, float[] query, int limit,
      String modelIdentity) {
    KnowledgeBaseSql.Scope scope = KnowledgeBaseSql.scope(appId, clientId);
    int dimensions = query.length;
    String sql = KnowledgeBaseSql.searchSql(dimensions);
    try {
      List<Object[]> groups = executor.query("SELECT embedding_model,embedding_dims,count(*) FROM " + TABLE
          + " WHERE app_id=? AND namespace=? AND ad_client_id IN (?,?) AND purge=false "
          + "GROUP BY embedding_model,embedding_dims", appId, namespace,
          scope.clientIds().get(0), scope.clientIds().size() > 1 ? scope.clientIds().get(1) : scope.clientIds().get(0));
      int total = 0;
      int matching = 0;
      StringBuilder counts = new StringBuilder();
      for (Object[] group : groups) {
        String storedModel = (String) group[0];
        int storedDims = ((Number) group[1]).intValue();
        int count = ((Number) group[2]).intValue();
        total += count;
        if (counts.length() > 0) counts.append(", ");
        counts.append(storedModel).append('/').append(storedDims).append('=').append(count);
        if (storedModel.equals(modelIdentity) && storedDims == dimensions) matching += count;
      }
      if (total > 0 && matching == 0) {
        String reindex = namespace.startsWith("MEMORY_")
            ? "Clear and re-add these memories."
            : "Re-index required: run Sync Assistant (the existing knowledge-base re-index process).";
        org.apache.logging.log4j.LogManager.getLogger(KnowledgeBaseStore.class).warn(
            "Knowledge-base search blocked because stored vectors are incompatible. Scope app={}, client-visible={}, namespace={}, model/dimension counts={}",
            appId, scope.clientIds(), namespace, counts);
        throw new KnowledgeBaseIncompatibleException("Knowledge base vectors are incompatible: stored "
            + counts + "; current " + modelIdentity + "/" + dimensions + ". " + reindex);
      }
      if (total > 0 && matching < total) {
        org.apache.logging.log4j.LogManager.getLogger(KnowledgeBaseStore.class).warn(
            "Knowledge-base partial re-index: {} chunks indexed with another model/dimension are ignored until re-indexed. Counts: {}",
            total - matching, counts);
      }
      List<Object[]> rows = executor.query(sql, vectorText(query), appId, namespace,
          scope.clientIds().get(0), scope.clientIds().size() > 1 ? scope.clientIds().get(1) : scope.clientIds().get(0),
          modelIdentity, dimensions, vectorText(query), limit);
      List<Match> matches = new ArrayList<>();
      for (Object[] row : rows) matches.add(new Match((String) row[0], (String) row[1], (String) row[2], ((Number) row[3]).doubleValue()));
      if (total > 0 && matching < total && !matches.isEmpty()) {
        String warning = namespace.startsWith("MEMORY_")
            ? (total - matching) + " memories from an incompatible model/dimension are ignored. Clear and re-add memories."
            : (total - matching) + " chunks indexed with an incompatible model/dimension are ignored until re-indexed.";
        matches.get(0).setWarning(warning);
      }
      return matches;
    } catch (SQLException e) {
      throw new OBException("Could not search the knowledge base. Confirm pgvector is enabled.", e);
    }
  }

  /** Best-effort runtime DDL limited to per-dimension ANN indexes; database user must own the table. */
  public void ensureIndex(int dimensions) {
    if (dimensions > 2000) {
      org.apache.logging.log4j.LogManager.getLogger(KnowledgeBaseStore.class).info(
          "Skipping KB HNSW index for {} dimensions (pgvector vector HNSW limit is 2000).", dimensions);
      return;
    }
    try {
      executor.update("CREATE INDEX IF NOT EXISTS etcop_kb_hnsw_" + dimensions + " ON " + TABLE
          + " USING hnsw ((embedding::vector(" + dimensions + ")) vector_cosine_ops) WHERE vector_dims(embedding) = "
          + dimensions);
    } catch (SQLException e) {
      org.apache.logging.log4j.LogManager.getLogger(KnowledgeBaseStore.class).warn(
          "Could not create KB HNSW index for {} dimensions; indexing continues.", dimensions, e);
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
  public void purgeSource(String appId, String clientId, String namespace, String sourceId) {
    try {
      executor.update("DELETE FROM " + TABLE
          + " WHERE app_id=? AND ad_client_id=? AND namespace=? AND source_id=? AND purge=true",
          appId, clientId, namespace, sourceId);
    } catch (SQLException e) { throw new OBException("Could not purge replaced knowledge-base chunks.", e); }
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
          int columns = result.getMetaData().getColumnCount();
          while (result.next()) {
            Object[] row = new Object[columns];
            for (int i = 0; i < columns; i++) row[i] = result.getObject(i + 1);
            rows.add(row);
          }
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
    private String warning;
    Match(String content, String sourceName, String sourceId, double distance) {
      this.content = content; this.sourceName = sourceName; this.sourceId = sourceId; this.distance = distance;
    }
    void setWarning(String warning) { this.warning = warning; }
    public String warning() { return warning; }
    public String content() { return content; }
    public String sourceName() { return sourceName; }
    public String sourceId() { return sourceId; }
    public double distance() { return distance; }
  }
}
