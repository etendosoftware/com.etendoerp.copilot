package com.etendoerp.copilot.kb;

import java.time.Duration;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;

import com.etendoerp.copilot.util.CopilotUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/** Resolves the embedding endpoint with the same proxy/provider convention as ChatModelFactory. */
public final class KnowledgeBaseEmbeddingModel {
  private KnowledgeBaseEmbeddingModel() { }
  public static EmbeddingModel build() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String proxy = CopilotUtils.readPropertyWithLegacyCompatibility(properties, "copilot.proxy.url", null);
    String key = CopilotUtils.readPropertyWithLegacyCompatibility(properties, "openai.api.key", null);
    String modelName = CopilotUtils.readPropertyWithLegacyCompatibility(properties,
        "copilot.embedding.model", "text-embedding-3-small");
    String routedModelName = StringUtils.isNotBlank(proxy) ? "openai/" + StringUtils.defaultIfBlank(modelName, "text-embedding-3-small")
        : StringUtils.defaultIfBlank(modelName, "text-embedding-3-small");
    OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
        .modelName(routedModelName)
        .apiKey(StringUtils.defaultIfBlank(key, StringUtils.isNotBlank(proxy) ? "dummy" : null))
        .timeout(Duration.ofSeconds(120));
    if (StringUtils.isNotBlank(proxy)) builder.baseUrl(proxy);
    else {
      String baseUrl = CopilotUtils.readPropertyWithLegacyCompatibility(properties, "copilot.openai.base.url", null);
      if (StringUtils.isNotBlank(baseUrl)) builder.baseUrl(baseUrl);
      if (StringUtils.isBlank(key)) throw new OBException("openai.api.key is required for knowledge-base embeddings");
    }
    return builder.build();
  }

  public static float[] vector(EmbeddingModel model, String text) {
    Embedding embedding = model.embed(text).content();
    return embedding.vector();
  }
}
