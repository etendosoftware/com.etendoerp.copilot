package com.etendoerp.copilot.engine;

import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.copilot.config.ProviderCredentials;
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
 * Provider, model, and credentials are resolved together from the selected
 * {@code ETCOP_OPENAI_MODEL} record by {@link ProviderCredentials}. A linked AI API Key record
 * takes precedence; legacy Openbravo.properties values remain a deprecated fallback for
 * unlinked existing models. Ollama uses its native client without an API key. Etendo aliases
 * use the OpenAI-compatible Etendo endpoint and are sent without a provider prefix.
 */
public class ChatModelFactory {

  private static final Logger log = LogManager.getLogger(ChatModelFactory.class);

  public static final String PROVIDER_ANTHROPIC = "anthropic";
  public static final String PROVIDER_OLLAMA = "ollama";

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
    String requestedModel = request.optString(RestServiceUtil.PROP_MODEL, null);
    ProviderCredentials.ProviderConfig config = ProviderCredentials.create().resolve(requestedModel);
    String provider = config.provider();
    String model = config.model();
    Double temperature = optionalTemperature(request);
    Duration timeout = Duration.ofSeconds(Long.parseLong(config.timeoutSeconds()));

    Integer maxTokens = maxTokens(request, provider, model);
    if (maxTokens == null) {
      maxTokens = config.maxTokens();
    }

    log.debug("Building Java chat model. provider={} model={} maxTokens={}", provider, model, maxTokens);

    if (StringUtils.equalsIgnoreCase(provider, PROVIDER_OLLAMA)) {
      OllamaChatModel.OllamaChatModelBuilder ollamaBuilder = OllamaChatModel.builder();
      ollamaBuilder.baseUrl(StringUtils.defaultIfBlank(config.baseUrl(), "http://localhost:11434"));
      ollamaBuilder.modelName(model);
      ollamaBuilder.temperature(temperature);
      ollamaBuilder.timeout(timeout);
      return ollamaBuilder.build();
    }

    if (config.gatewayMode() && !StringUtils.equalsIgnoreCase(provider, "etendo")) {
      return OpenAiChatModel.builder()
          .baseUrl(config.baseUrl())
          .apiKey(StringUtils.defaultIfBlank(config.apiKey(), PROXY_PLACEHOLDER_KEY))
          .modelName(provider + "/" + model)
          .temperature(temperature)
          .maxTokens(maxTokens)
          .timeout(timeout)
          .build();
    }

    if (StringUtils.equalsIgnoreCase(provider, PROVIDER_ANTHROPIC)) {
      return AnthropicChatModel.builder()
          .apiKey(config.apiKey())
          .modelName(model)
          .temperature(temperature)
          .maxTokens(maxTokens)
          .timeout(timeout)
          .build();
    }
    if (StringUtils.equalsIgnoreCase(provider, CopilotConstants.PROVIDER_GEMINI)) {
      return GoogleAiGeminiChatModel.builder()
          .apiKey(config.apiKey())
          .modelName(model)
          .temperature(temperature)
          .maxOutputTokens(maxTokens)
          .timeout(timeout)
          .build();
    }
    OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
        .apiKey(StringUtils.defaultIfBlank(config.apiKey(), PROXY_PLACEHOLDER_KEY))
        .modelName(model)
        .temperature(temperature)
        .maxTokens(maxTokens)
        .timeout(timeout);
    if (StringUtils.isNotBlank(config.baseUrl())) {
      builder.baseUrl(config.baseUrl());
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

}
