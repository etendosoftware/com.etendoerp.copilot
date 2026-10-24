package com.etendoerp.copilot.kb;

import java.time.Duration;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;

import com.etendoerp.copilot.config.ProviderCredentials;
import com.etendoerp.copilot.util.CopilotUtils;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/** Resolves the configured embedding model through the central provider credential service. */
public final class KnowledgeBaseEmbeddingModel {
  private static final String DEFAULT_ETENDO_MODEL = "etendo-embeddings";

  private KnowledgeBaseEmbeddingModel() { }

  public static EmbeddingModel build() {
    ProviderCredentials.ProviderConfig config = resolveConfig();
    String modelName = config.gatewayMode() && !"etendo".equalsIgnoreCase(config.provider())
        ? config.provider() + "/" + config.model() : config.model();
    OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
        .modelName(modelName)
        .apiKey(StringUtils.defaultIfBlank(config.apiKey(), "dummy"))
        .timeout(Duration.ofSeconds(Long.parseLong(config.timeoutSeconds())));
    if (StringUtils.isNotBlank(config.baseUrl())) builder.baseUrl(config.baseUrl());
    return builder.build();
  }

  public static String identity() {
    ProviderCredentials.ProviderConfig config = resolveConfig();
    return config.provider().toLowerCase(java.util.Locale.ROOT) + "/" + config.model();
  }

  private static ProviderCredentials.ProviderConfig resolveConfig() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String selectedModel = embeddingModelPreference();
    ProviderCredentials credentials = ProviderCredentials.create();
    if (StringUtils.isNotBlank(selectedModel)) return credentials.resolve(selectedModel);
    try {
      return credentials.resolve(DEFAULT_ETENDO_MODEL);
    } catch (OBException missingEtendoAlias) {
      String legacyModel = CopilotUtils.readPropertyWithLegacyCompatibility(properties,
          "copilot.embedding.model", "text-embedding-3-small");
      return credentials.resolve(legacyModel);
    }
  }

  private static String embeddingModelPreference() {
    try {
      return Preferences.getPreferenceValue("ETCOP_DefaultEmbeddingModel", true,
          OBContext.getOBContext().getCurrentClient(), OBContext.getOBContext().getCurrentOrganization(),
          OBContext.getOBContext().getUser(), OBContext.getOBContext().getRole(), null);
    } catch (Exception e) {
      return null;
    }
  }

  public static float[] vector(EmbeddingModel model, String text) {
    Embedding embedding = model.embed(text).content();
    return embedding.vector();
  }
}
