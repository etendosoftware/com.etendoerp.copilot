package com.etendoerp.copilot.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.config.ProviderCredentials.ApiKeySettings;
import com.etendoerp.copilot.config.ProviderCredentials.ModelSettings;

public class ProviderCredentialsTest {
  @Test
  public void linkedKeyWinsOverDeprecatedProperty() {
    Properties properties = new Properties();
    properties.setProperty("openai.api.key", "legacy-canary");
    ProviderCredentials resolver = new ProviderCredentials(new Lookup(
        new ModelSettings("gpt-alias", "openai", "key-id", 1000),
        new ApiKeySettings("openai", "db-canary", "https://example.invalid", true)), properties);

    ProviderCredentials.ProviderConfig result = resolver.resolve("model-id");

    assertEquals("db-canary", result.apiKey());
    assertEquals("https://example.invalid", result.baseUrl());
    assertTrue(result.toString().contains("[REDACTED]"));
    assertFalse(result.toString().contains("db-canary"));
  }

  @Test
  public void unlinkedLegacyModelUsesDeprecatedProperty() {
    Properties properties = new Properties();
    properties.setProperty("openai.api.key", "legacy-canary");
    ProviderCredentials resolver = new ProviderCredentials(new Lookup(
        new ModelSettings("gpt-alias", "openai", null, null), null), properties);

    assertEquals("legacy-canary", resolver.resolve("model-id").apiKey());
  }

  @Test
  public void missingEtendoKeyProducesConfigurationGuidance() {
    ProviderCredentials resolver = new ProviderCredentials(new Lookup(
        new ModelSettings("etendo-fast", "etendo", null, null), null), new Properties());

    OBException error = assertThrows(OBException.class, () -> resolver.resolve("model-id"));

    assertTrue(error.getMessage().contains("etendo-fast"));
    assertTrue(error.getMessage().contains("AI API Keys"));
  }

  private static final class Lookup implements ProviderCredentials.ModelLookup {
    private final ModelSettings model;
    private final ApiKeySettings apiKey;

    private Lookup(ModelSettings model, ApiKeySettings apiKey) {
      this.model = model;
      this.apiKey = apiKey;
    }

    @Override
    public ModelSettings findModel(String modelId) {
      return model;
    }

    @Override
    public ModelSettings findDefaultModel(String providerType) {
      return model;
    }
    @Override
    public ApiKeySettings findApiKey(String apiKeyId) {
      return apiKey;
    }

    @Override
    public ApiKeySettings findScopedActiveApiKey(String providerType) {
      return apiKey;
    }
  }
}
