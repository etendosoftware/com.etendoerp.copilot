package com.etendoerp.copilot.kb;

import java.util.List;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import dev.langchain4j.model.embedding.EmbeddingModel;

/** User-scoped memory operations stored separately from an agent's knowledge-base namespace. */
@ApplicationScoped
@Named("AgentMemoryService")
public class AgentMemoryService {
  static final int DEFAULT_RESULTS = 3;
  private final KnowledgeBaseStore store;
  private final EmbeddingModel model;
  public AgentMemoryService() { this(new KnowledgeBaseStore(), KnowledgeBaseEmbeddingModel.build()); }
  AgentMemoryService(KnowledgeBaseStore store, EmbeddingModel model) { this.store = store; this.model = model; }

  public String add(String agentId, String userId, String memory) {
    if (memory == null || memory.isBlank()) throw new IllegalArgumentException("'memory' is required");
    String id = UUID.randomUUID().toString();
    persist(agentId, userId, id, memory);
    return id;
  }
  public boolean update(String agentId, String userId, String id, String memory) {
    if (id == null || id.isBlank() || memory == null || memory.isBlank()) throw new IllegalArgumentException("'memory_id' and 'memory' are required");
    store.ensureReady();
    if (!exists(agentId, userId, id)) return false;
    store.deleteSource(agentId, userId, namespace(agentId), id);
    persist(agentId, userId, id, memory);
    return true;
  }
  public boolean delete(String agentId, String userId, String id) {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("'memory_id' is required");
    store.ensureReady();
    if (!exists(agentId, userId, id)) return false;
    store.deleteSource(agentId, userId, namespace(agentId), id);
    return true;
  }
  public List<KnowledgeBaseStore.Match> search(String agentId, String userId, String query, int k) {
    if (query == null || query.isBlank()) throw new IllegalArgumentException("'query' is required");
    store.ensureReady();
    return store.search(agentId, userId, namespace(agentId), KnowledgeBaseEmbeddingModel.vector(model, query),
        Math.max(1, Math.min(20, k)), KnowledgeBaseEmbeddingModel.identity());
  }
  private boolean exists(String agentId, String userId, String id) {
    return store.hasSource(agentId, userId, namespace(agentId), id);
  }
  private void persist(String agentId, String userId, String id, String memory) {
    store.ensureReady();
    String hash = Integer.toHexString(memory.hashCode());
    store.upsert(id, agentId, userId, namespace(agentId), id, "memory", memory, hash,
        KnowledgeBaseEmbeddingModel.vector(model, memory), KnowledgeBaseEmbeddingModel.identity());
  }
  private static String namespace(String agentId) { return "MEMORY_" + agentId; }
}
