package com.etendoerp.copilot.config;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SQLQuery;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.utils.FormatUtilities;

import com.etendoerp.copilot.util.CopilotUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openbravo.dal.core.OBContext;
/** Resolves model provider settings and the key record linked to that model. */
public final class ProviderCredentials {
  private static final long CACHE_TTL_MILLIS = 30_000L;
  private static final Map<String, CachedConfig> CACHE = new ConcurrentHashMap<>();
  private static final Logger log = LogManager.getLogger(ProviderCredentials.class);
  private static final String ETENDO_PROVIDER = "etendo";

  private final ModelLookup lookup;
  private final Properties properties;
  private final boolean cacheEnabled;

  public ProviderCredentials(ModelLookup lookup, Properties properties) {
    this(lookup, properties, false);
  }

  private ProviderCredentials(ModelLookup lookup, Properties properties, boolean cacheEnabled) {
    this.lookup = Objects.requireNonNull(lookup);
    this.properties = Objects.requireNonNull(properties);
    this.cacheEnabled = cacheEnabled;
  }

  public static ProviderCredentials create(ModelLookup lookup) {
    return new ProviderCredentials(lookup, OBPropertiesProvider.getInstance().getOpenbravoProperties());
  }

  public static ProviderCredentials create() {
    return new ProviderCredentials(new DatabaseModelLookup(),
        OBPropertiesProvider.getInstance().getOpenbravoProperties(), true);
  }

  /**
   * Resolves credentials for a model, retaining the old property path for unlinked models while
   * installs are migrated. The returned object is deliberately immutable and masks its key in
   * {@link #toString()}.
   */
  public ProviderConfig resolve(String modelId) {
    if (!cacheEnabled) {
      return resolveUncached(modelId);
    }
    String cacheKey = OBContext.getOBContext().getCurrentClient().getId() + ":"
        + OBContext.getOBContext().getCurrentOrganization().getId() + ":" + modelId;
    CachedConfig cached = CACHE.get(cacheKey);
    long now = System.currentTimeMillis();
    if (cached != null && cached.expiresAt > now) {
      return cached.config;
    }
    ProviderConfig resolved = resolveUncached(modelId);
    CACHE.put(cacheKey, new CachedConfig(resolved, now + CACHE_TTL_MILLIS));
    return resolved;
  }

  private ProviderConfig resolveUncached(String modelId) {
    ModelSettings model = lookup.findModel(modelId);
    if (model == null) {
      throw new OBException("No active model configuration was found.");
    }

    ApiKeySettings key = StringUtils.isBlank(model.apiKeyId()) ? null : lookup.findApiKey(model.apiKeyId());
    String provider = StringUtils.defaultIfBlank(key == null ? null : key.providerType(), model.provider());
    String apiKey = key == null ? null : key.decryptedApiKey();
    String baseUrl = key == null ? null : key.baseUrl();
    if (ETENDO_PROVIDER.equalsIgnoreCase(provider) && StringUtils.isBlank(baseUrl)) {
      baseUrl = "https://llm.etendo.software";
    }

    if (key == null) {
      apiKey = propertyKey(provider);
      baseUrl = propertyBaseUrl(provider);
    } else if (!key.active()) {
      throw new OBException("The API key linked to model " + model.searchKey() + " is inactive.");
    }

    if (StringUtils.isBlank(provider)) {
      throw new OBException("The model has no provider type configured.");
    }
    if (ETENDO_PROVIDER.equalsIgnoreCase(provider) && StringUtils.isBlank(apiKey)) {
      throw new OBException("No API key linked to model " + model.searchKey()
          + "; configure it under AI API Keys.");
    }
    if (!"ollama".equalsIgnoreCase(provider) && StringUtils.isBlank(apiKey)) {
      throw new OBException("No API key is configured for provider " + provider
          + "; link a key under AI API Keys or configure the deprecated property.");
    }
    String timeout = CopilotUtils.readPropertyWithLegacyCompatibility(properties,
        "copilot.engine.java.timeout.seconds", "120");
    String legacyGatewayUrl = CopilotUtils.readPropertyWithLegacyCompatibility(
        properties, "copilot.proxy.url", null);
    boolean gatewayMode = ETENDO_PROVIDER.equalsIgnoreCase(provider)
        || StringUtils.equals(baseUrl, legacyGatewayUrl) && StringUtils.isNotBlank(legacyGatewayUrl);
    return new ProviderConfig(provider, model.searchKey(), apiKey, baseUrl, timeout, model.maxTokens(),
        key != null, gatewayMode);
  }
  public ProviderConfig resolveDefault(String provider) {
    ModelSettings model = lookup.findDefaultModel(provider);
    if (model == null) {
      throw new OBException("No active default model is configured for provider " + provider + ".");
    }
    return resolve(model.searchKey());
  }

  public String resolveToolApiKey(String providerType) {
    ApiKeySettings key = lookup.findScopedActiveApiKey(providerType);
    if (key != null && StringUtils.isNotBlank(key.decryptedApiKey())) {
      return key.decryptedApiKey();
    }
    return propertyKey(providerType);
  }

  private String propertyKey(String provider) {
    if ("openai".equalsIgnoreCase(provider)) {
      return CopilotUtils.readPropertyWithLegacyCompatibility(properties, "openai.api.key", null);
    }
    if ("anthropic".equalsIgnoreCase(provider)) {
      return CopilotUtils.readPropertyWithLegacyCompatibility(properties, "anthropic.api.key", null);
    }
    if ("gemini".equalsIgnoreCase(provider)) {
      return CopilotUtils.readPropertyWithLegacyCompatibility(properties, "gemini.api.key", null);
    }
    return null;
  }

  private String propertyBaseUrl(String provider) {
    if ("ollama".equalsIgnoreCase(provider)) {
      return CopilotUtils.readPropertyWithLegacyCompatibility(properties, "ollama.base.url", "http://localhost:11434");
    }
    String proxy = CopilotUtils.readPropertyWithLegacyCompatibility(properties, "copilot.proxy.url", null);
    if (StringUtils.isNotBlank(proxy)) {
      return proxy;
    }
    if ("openai".equalsIgnoreCase(provider)) {
      return CopilotUtils.readPropertyWithLegacyCompatibility(properties, "copilot.openai.base.url", null);
    }
    return null;
  }

  public static void invalidateCache() {
    CACHE.clear();
  }

  private record CachedConfig(ProviderConfig config, long expiresAt) { }


  public interface ModelLookup {
    ModelSettings findModel(String modelId);
    ModelSettings findDefaultModel(String providerType);
    ApiKeySettings findApiKey(String apiKeyId);
    ApiKeySettings findScopedActiveApiKey(String providerType);
  }

  private static final class DatabaseModelLookup implements ModelLookup {
    @Override
    public ModelSettings findModel(String modelKey) {
      String clientId = currentClientId();
      String orgId = currentOrgId();
      @SuppressWarnings("deprecation")
      SQLQuery query = OBDal.getInstance().getSession().createSQLQuery(
          "select searchkey, provider, etcop_api_key_id, max_tokens from etcop_openai_model "
              + "where searchkey = :modelKey and isactive = 'Y' "
              + "and ad_client_id in (:clientId, '0') and ad_org_id in (:orgId, '0') "
              + "order by case when ad_org_id = :orgId then 0 when ad_client_id = :clientId then 1 else 2 end "
              + "limit 1");
      query.setParameter("modelKey", modelKey);
      query.setParameter("clientId", clientId);
      query.setParameter("orgId", orgId);
      @SuppressWarnings("unchecked")
      List<Object[]> rows = query.list();
      if (rows.isEmpty()) {
        return null;
      }
      Object[] row = rows.get(0);
      return new ModelSettings((String) row[0], (String) row[1], (String) row[2],
          row[3] == null ? null : ((Number) row[3]).intValue());
    }
    @Override
    public ModelSettings findDefaultModel(String providerType) {
      String clientId = currentClientId();
      String orgId = currentOrgId();
      @SuppressWarnings("deprecation")
      SQLQuery query = OBDal.getInstance().getSession().createSQLQuery(
          "select searchkey, provider, etcop_api_key_id, max_tokens from etcop_openai_model "
              + "where provider = :providerType and isdefault = 'Y' and isactive = 'Y' "
              + "and ad_client_id in (:clientId, '0') and ad_org_id in (:orgId, '0') "
              + "order by case when ad_org_id = :orgId then 0 when ad_client_id = :clientId then 1 else 2 end "
              + "limit 1");
      query.setParameter("providerType", providerType);
      query.setParameter("clientId", clientId);
      query.setParameter("orgId", orgId);
      @SuppressWarnings("unchecked")
      List<Object[]> rows = query.list();
      if (rows.isEmpty()) {
        return null;
      }
      Object[] row = rows.get(0);
      return new ModelSettings((String) row[0], (String) row[1], (String) row[2],
          row[3] == null ? null : ((Number) row[3]).intValue());
    }

    @Override
    public ApiKeySettings findApiKey(String apiKeyId) {
      return findKey("etcop_api_key_id = :keyId", apiKeyId, null);
    }

    @Override
    public ApiKeySettings findScopedActiveApiKey(String providerType) {
      return findKey("provider_type = :providerType", null, providerType);
    }

    private ApiKeySettings findKey(String filter, String keyId, String providerType) {
      String clientId = currentClientId();
      String orgId = currentOrgId();
      String activeFilter = keyId == null ? "and isactive = 'Y' " : "";
      @SuppressWarnings("deprecation")
      SQLQuery query = OBDal.getInstance().getSession().createSQLQuery(
          "select provider_type, api_key, base_url, isactive from etcop_api_key "
              + "where " + filter + " " + activeFilter
              + "and ad_client_id in (:clientId, '0') and ad_org_id in (:orgId, '0') "
              + "order by case when ad_org_id = :orgId then 0 when ad_client_id = :clientId then 1 else 2 end "
              + "limit 1");
      if (keyId != null) {
        query.setParameter("keyId", keyId);
      } else {
        query.setParameter("providerType", providerType);
      }
      query.setParameter("clientId", clientId);
      query.setParameter("orgId", orgId);
      @SuppressWarnings("unchecked")
      List<Object[]> rows = query.list();
      if (rows.isEmpty()) {
        return null;
      }
      Object[] row = rows.get(0);
      // isactive is char(1): Hibernate returns a Character, so compare as text.
      return new ApiKeySettings((String) row[0], decrypt((String) row[1]), (String) row[2],
          "Y".equals(String.valueOf(row[3])));
    }

    private static String currentClientId() {
      return OBContext.getOBContext().getCurrentClient().getId();
    }

    private static String currentOrgId() {
      return OBContext.getOBContext().getCurrentOrganization().getId();
    }

    private static String decrypt(String encryptedValue) {
      if (StringUtils.isBlank(encryptedValue)) {
        return null;
      }
      try {
        return FormatUtilities.encryptDecrypt(encryptedValue, false);
      } catch (ServletException | RuntimeException e) {
        log.error("Unable to decrypt an AI API key value; check the stored encrypted value.");
        throw new OBException("Unable to read the configured API key.");
      }
    }
  }

  public record ModelSettings(String searchKey, String provider, String apiKeyId, Integer maxTokens) { }

  public record ApiKeySettings(String providerType, String decryptedApiKey, String baseUrl, boolean active) { }

  public static final class ProviderConfig {
    private final String provider;
    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final String timeoutSeconds;
    private final Integer maxTokens;
    private final boolean linkedKey;
    private final boolean gatewayMode;

    private ProviderConfig(String provider, String model, String apiKey, String baseUrl, String timeoutSeconds,
        Integer maxTokens, boolean linkedKey, boolean gatewayMode) {
      this.provider = provider;
      this.model = model;
      this.apiKey = apiKey;
      this.baseUrl = baseUrl;
      this.timeoutSeconds = timeoutSeconds;
      this.maxTokens = maxTokens;
      this.linkedKey = linkedKey;
      this.gatewayMode = gatewayMode;
    }

    public String provider() { return provider; }
    public String model() { return model; }
    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String timeoutSeconds() { return timeoutSeconds; }
    public Integer maxTokens() { return maxTokens; }
    public boolean linkedKey() { return linkedKey; }
    public boolean gatewayMode() { return gatewayMode; }

    @Override
    public String toString() {
      return "ProviderConfig{provider='" + provider + "', model='" + model + "', apiKey='[REDACTED]', baseUrl='"
          + baseUrl + "', linkedKey=" + linkedKey + "}";
    }
  }
}
