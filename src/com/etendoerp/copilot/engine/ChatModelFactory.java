package com.etendoerp.copilot.engine;

import static com.etendoerp.copilot.util.CopilotUtils.readPropertyWithLegacyCompatibility;

import java.time.Duration;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;

import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Builds a LangChain4j {@link ChatModel} from the request payload produced by
 * {@code RestServiceUtil.buildRequestJson}.
 * <p>
 * The provider and model names are the ones already resolved on the Java side by
 * {@code CopilotModelUtils}, so the Java engine talks to exactly the same model the Python
 * runtime would have used.
 * <p>
 * Provider resolution mirrors the Python runtime ({@code copilot/core/utils/agent.py::get_llm})
 * so both engines reach the same endpoint with the same model name:
 * <ol>
 *   <li>Ollama is handled natively, before anything else.</li>
 *   <li>If {@code copilot.proxy.url} is set, <b>proxy mode</b> applies to every provider: the
 *       OpenAI-compatible client is used against that gateway and the model name is prefixed with
 *       the original provider ({@code openai/gpt-4.1}), which is how the gateway resolves the real
 *       credentials. The API key falls back to {@code "dummy"} because the OpenAI client refuses
 *       to build without one.</li>
 *   <li>Otherwise each provider uses its native client.</li>
 * </ol>
 * Properties read from {@code Openbravo.properties}:
 * <pre>
 *   copilot.proxy.url          = https://llm.etendo.software   (gateway; enables proxy mode)
 *   openai.api.key             = sk-...
 *   anthropic.api.key          = sk-ant-...
 *   gemini.api.key             = ...
 *   ollama.base.url            = http://localhost:11434        (no key)
 *   copilot.openai.base.url    = http://localhost:4000         (direct mode only, overrides the
 *                                                               OpenAI endpoint without prefixing
 *                                                               the model name)
 * </pre>
 */
public class ChatModelFactory {

  private static final Logger log = LogManager.getLogger(ChatModelFactory.class);

  public static final String PROPERTY_TIMEOUT = "copilot.engine.java.timeout.seconds";
  public static final String PROPERTY_PROXY_URL = "copilot.proxy.url";
  public static final String PROPERTY_OPENAI_BASE_URL = "copilot.openai.base.url";
  public static final String PROVIDER_ANTHROPIC = "anthropic";
  public static final String PROVIDER_OLLAMA = "ollama";

  private static final long DEFAULT_TIMEOUT_SECONDS = 120L;
  /** The OpenAI client refuses to build without a key; the gateway resolves the real one. */
  private static final String PROXY_PLACEHOLDER_KEY = "dummy";

  private ChatModelFactory() {
  }

  /**
   * Build the chat model described by the request.
   *
   * @param request
   *     the payload built by {@code RestServiceUtil.buildRequestJson}
   * @return a ready to use {@link ChatModel}
   * @throws OBException
   *     when the provider has no API key configured
   */
  public static ChatModel build(JSONObject request) {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String provider = StringUtils.defaultIfEmpty(request.optString(RestServiceUtil.PROP_PROVIDER, null),
        CopilotConstants.PROVIDER_OPENAI);
    String model = request.optString(RestServiceUtil.PROP_MODEL, null);
    Double temperature = optionalTemperature(request);
    Duration timeout = timeout(properties);

    Integer maxTokens = maxTokens(request, provider, model);

    log.debug("Building Java chat model. provider={} model={} maxTokens={}", provider, model, maxTokens);

    if (StringUtils.equalsIgnoreCase(provider, PROVIDER_OLLAMA)) {
      // Not chained on purpose: the inherited setters return the base builder, whose build()
      // yields an OllamaBaseChatModel, which is not a ChatModel.
      OllamaChatModel.OllamaChatModelBuilder ollamaBuilder = OllamaChatModel.builder();
      ollamaBuilder.baseUrl(
          readPropertyWithLegacyCompatibility(properties, "ollama.base.url", "http://localhost:11434"));
      ollamaBuilder.modelName(model);
      ollamaBuilder.temperature(temperature);
      ollamaBuilder.timeout(timeout);
      return ollamaBuilder.build();
    }

    String proxyUrl = readPropertyWithLegacyCompatibility(properties, PROPERTY_PROXY_URL, null);
    if (StringUtils.isNotBlank(proxyUrl)) {
      // Proxy mode: every provider goes through the gateway, model prefixed with its provider.
      return OpenAiChatModel.builder()
          .baseUrl(proxyUrl)
          .apiKey(StringUtils.defaultIfBlank(
              readPropertyWithLegacyCompatibility(properties, "openai.api.key", null),
              PROXY_PLACEHOLDER_KEY))
          .modelName(provider + "/" + model)
          .temperature(temperature)
          .maxTokens(maxTokens)
          .timeout(timeout)
          .build();
    }

    if (StringUtils.equalsIgnoreCase(provider, PROVIDER_ANTHROPIC)) {
      return AnthropicChatModel.builder()
          .apiKey(requireKey(properties, "anthropic.api.key", provider))
          .modelName(model)
          .temperature(temperature)
          .maxTokens(maxTokens)
          .timeout(timeout)
          .build();
    }
    if (StringUtils.equalsIgnoreCase(provider, CopilotConstants.PROVIDER_GEMINI)) {
      return GoogleAiGeminiChatModel.builder()
          .apiKey(requireKey(properties, "gemini.api.key", provider))
          .modelName(model)
          .temperature(temperature)
          .maxOutputTokens(maxTokens)
          .timeout(timeout)
          .build();
    }
    // OpenAI and any OpenAI-compatible gateway (LiteLLM, vLLM, Azure-compatible proxies...).
    OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
        .apiKey(requireKey(properties, "openai.api.key", provider))
        .modelName(model)
        .temperature(temperature)
        .maxTokens(maxTokens)
        .timeout(timeout);
    String baseUrl = readPropertyWithLegacyCompatibility(properties, PROPERTY_OPENAI_BASE_URL, null);
    if (StringUtils.isNotBlank(baseUrl)) {
      builder.baseUrl(baseUrl);
    }
    return builder.build();
  }

  /**
   * Read the {@code max_tokens} the assistant configured for this model. The value travels in
   * {@code extra_info.model_config}, the same structure the Python runtime consults in
   * {@code get_model_config}.
   *
   * @param request
   *     the request payload
   * @param provider
   *     the resolved provider
   * @param model
   *     the resolved model search key
   * @return the configured limit, or {@code null} when the model declares none
   */
  private static Integer maxTokens(JSONObject request, String provider, String model) {
    JSONObject extraInfo = request.optJSONObject("extra_info");
    if (extraInfo == null || model == null) {
      return null;
    }
    JSONObject modelConfig = extraInfo.optJSONObject("model_config");
    if (modelConfig == null) {
      return null;
    }
    JSONObject providerConfig = modelConfig.optJSONObject(provider);
    if (providerConfig == null) {
      return null;
    }
    JSONObject config = providerConfig.optJSONObject(model);
    if (config == null || config.isNull("max_tokens")) {
      return null;
    }
    int maxTokens = config.optInt("max_tokens", 0);
    return maxTokens > 0 ? maxTokens : null;
  }

  /**
   * @param request
   *     the request payload
   * @return the configured temperature, or {@code null} to let the provider default apply
   */
  private static Double optionalTemperature(JSONObject request) {
    if (!request.has(RestServiceUtil.PROP_TEMPERATURE) || request.isNull(RestServiceUtil.PROP_TEMPERATURE)) {
      return null;
    }
    double temperature = request.optDouble(RestServiceUtil.PROP_TEMPERATURE);
    return Double.isNaN(temperature) ? null : temperature;
  }

  /**
   * @param properties
   *     the Openbravo properties
   * @return the request timeout, defaulting to two minutes
   */
  private static Duration timeout(Properties properties) {
    String configured = readPropertyWithLegacyCompatibility(properties, PROPERTY_TIMEOUT, null);
    if (StringUtils.isBlank(configured)) {
      return Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
    }
    try {
      return Duration.ofSeconds(Long.parseLong(configured.trim()));
    } catch (NumberFormatException e) {
      log.warn("Invalid value '{}' for {}, using {}s", configured, PROPERTY_TIMEOUT, DEFAULT_TIMEOUT_SECONDS);
      return Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
    }
  }

  /**
   * @param properties
   *     the Openbravo properties
   * @param propertyName
   *     the property holding the API key
   * @param provider
   *     the provider name, for the error message
   * @return the API key
   * @throws OBException
   *     when the key is missing
   */
  private static String requireKey(Properties properties, String propertyName, String provider) {
    String key = readPropertyWithLegacyCompatibility(properties, propertyName, null);
    if (StringUtils.isBlank(key)) {
      throw new OBException(String.format(
          "The Java Copilot engine needs the property '%s' in Openbravo.properties to use provider '%s'.",
          propertyName, provider));
    }
    return key;
  }
}
