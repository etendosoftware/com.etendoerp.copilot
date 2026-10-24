package com.etendoerp.copilot.kb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parameterized SQL fragments for tenant-safe knowledge-base operations. */
public final class KnowledgeBaseSql {
  private KnowledgeBaseSql() {
  }

  public static Scope scope(String appId, String clientId) {
    if (appId == null || appId.isBlank() || clientId == null || clientId.isBlank()) {
      throw new IllegalArgumentException("App and client scope are required");
    }
    List<String> clients = clientId.equals("0") ? List.of("0") : List.of("0", clientId);
    return new Scope(appId, clients);
  }

  public static String searchSql(int dimensions) {
    if (dimensions < 1) throw new IllegalArgumentException("Embedding dimensions must be positive");
    return "SELECT content,source_name,source_id,embedding::vector(" + dimensions + ") <=> CAST(? AS vector("
        + dimensions + ")) distance FROM etcop_kb_chunk "
        + "WHERE app_id=? AND namespace=? AND ad_client_id IN (?,?) AND purge=false "
        + "AND embedding_model=? AND embedding_dims=? AND vector_dims(embedding)=" + dimensions
        + " ORDER BY embedding::vector(" + dimensions + ") <=> CAST(? AS vector(" + dimensions + ")) LIMIT ?";
  }

  public static String deleteByClientSql() {
    return "DELETE FROM etcop_kb_chunk WHERE app_id = ? AND ad_client_id = ? AND namespace = ?";
  }

  public static String deletePurgedSql() {
    return "DELETE FROM etcop_kb_chunk WHERE app_id = ? AND ad_client_id = ? AND namespace = ? AND purge = TRUE";
  }

  public static final class Scope {
    private final String appId;
    private final List<String> clientIds;
    private Scope(String appId, List<String> clientIds) {
      this.appId = appId;
      this.clientIds = Collections.unmodifiableList(new ArrayList<>(clientIds));
    }
    public String appId() { return appId; }
    public List<String> clientIds() { return clientIds; }
  }
}
