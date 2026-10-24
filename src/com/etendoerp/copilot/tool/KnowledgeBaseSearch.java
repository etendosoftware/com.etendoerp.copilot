package com.etendoerp.copilot.tool;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import org.codehaus.jettison.json.JSONObject;
import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.kb.KnowledgeBaseEmbeddingModel;
import com.etendoerp.copilot.kb.KnowledgeBaseStore;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
/** Searches the current agent's indexed knowledge base under the captured client scope. */
@ApplicationScoped
@Named("KnowledgeBaseSearch")
public class KnowledgeBaseSearch implements ServerTool {
  private final KnowledgeBaseStore store;
  public KnowledgeBaseSearch() { this(new KnowledgeBaseStore()); }
  KnowledgeBaseSearch(KnowledgeBaseStore store) { this.store = store; }
  @Override public String name() { return "KnowledgeBaseSearch"; }
  @Override public ToolSpecification specification() {
    return ToolSpecification.builder().name(name())
        .description("Searches the agent knowledge base and returns relevant passages with source citations.")
        .parameters(JsonObjectSchema.builder().addProperty("query", JsonRawSchema.from("{\"type\":\"string\",\"description\":\"The question to search in the agent's knowledge base.\"}"))
            .required("query").build()).build();
  }
  @Override public String execute(JSONObject arguments, ToolExecutionContext context) throws Exception {
    String query = arguments.optString("query", "");
    if (query.isBlank()) throw new IllegalArgumentException("'query' is required");
    int k = Math.max(1, Math.min(20, context.attributes().get("kb_search_k") instanceof Number
        ? ((Number) context.attributes().get("kb_search_k")).intValue() : 4));
    store.ensureReady();
    java.util.List<KnowledgeBaseStore.Match> matches;
    try {
      matches = store.search(context.agentId(), context.clientId(), "KB_" + context.agentId(),
          KnowledgeBaseEmbeddingModel.vector(KnowledgeBaseEmbeddingModel.build(), query), k,
          KnowledgeBaseEmbeddingModel.identity());
    } catch (com.etendoerp.copilot.kb.KnowledgeBaseIncompatibleException incompatible) {
      return new JSONObject().put("error", incompatible.getMessage()).toString();
    }
    JSONObject result = new JSONObject();
    org.codehaus.jettison.json.JSONArray passages = new org.codehaus.jettison.json.JSONArray();
    for (KnowledgeBaseStore.Match match : matches) {
      org.codehaus.jettison.json.JSONObject passage = new JSONObject().put("text", match.content())
          .put("source", match.sourceName()).put("source_id", match.sourceId()).put("distance", match.distance());
      if (match.warning() != null) result.put("warning", match.warning());
      passages.put(passage);
    }
    result.put("results", passages);
    return result.toString();
  }
}
