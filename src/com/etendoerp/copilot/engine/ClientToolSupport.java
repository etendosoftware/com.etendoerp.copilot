package com.etendoerp.copilot.engine;

import static com.etendoerp.copilot.util.CopilotUtils.readPropertyWithLegacyCompatibility;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;

import com.etendoerp.copilot.rest.RestServiceUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;

/**
 * Client-executed tools: the model decides, the browser executes.
 * <p>
 * This is the mechanism behind WebMCP support. The chat client discovers the tools registered in
 * the page ({@code document.modelContext.getTools()}) and sends their descriptors with the
 * question. The Java engine offers them to the model but never runs them: when the model asks for
 * one, the engine answers with a {@code tool_call} envelope and the turn ends. The client executes
 * the tool in its own realm — with the user's session and the user's permissions — and re-invokes
 * the endpoint with the result, at which point the conversation continues.
 * <p>
 * Two consequences worth keeping in mind:
 * <ul>
 *   <li>The engine needs <b>no tool implementations at all</b>. Adding a tool in the browser adds
 *       it to the agent with no server change.</li>
 *   <li>The exchange is stateless. The client echoes back every round of the exchange, and the
 *       engine rebuilds the message list from the conversation history plus those rounds. Nothing
 *       is kept between requests. The client already controls the tool results by construction, so
 *       echoing the calls back adds no trust boundary that did not already exist.</li>
 * </ul>
 * Tool round trips are deliberately <b>not</b> written to {@code ETCOP_MESSAGE}: one user turn
 * produces one stored question and one stored answer, whatever happens in between.
 * <p>
 * This path requires the synchronous POST endpoint. The streaming GET whitelists four parameters
 * ({@code RequestUtils.retrieveParametersAsJson}), so tool descriptors and results silently do not
 * survive it, and the turn degrades to a plain chat.
 */
public class ClientToolSupport {

  private static final Logger log = LogManager.getLogger(ClientToolSupport.class);

  /** Tool descriptors offered by the client, as sent by {@code document.modelContext.getTools()}. */
  public static final String PROP_CLIENT_TOOLS = "client_tools";
  /** Ordered rounds of {calls, results} already exchanged for this question. */
  public static final String PROP_TOOL_EXCHANGE = "tool_exchange";
  /** Mixed server/client turns attach results for already-executed server calls here. */
  public static final String PROP_SERVER_RESULTS = "server_results";
  /** Tool-call answer property for calls already executed by the server. */
  public static final String PROP_TOOL_CALLS = "tool_calls";
  /** Role of an answer that carries tool calls instead of text. */
  public static final String ROLE_TOOL_CALL = "tool_call";

  public static final String PROP_CALLS = "calls";
  public static final String PROP_RESULTS = "results";
  public static final String PROP_ID = "id";
  public static final String PROP_NAME = "name";
  public static final String PROP_ARGUMENTS = "arguments";
  public static final String PROP_RESULT = "result";
  public static final String PROP_INPUT_SCHEMA = "inputSchema";
  public static final String PROP_DESCRIPTION = "description";

  /** Property overriding {@link #DEFAULT_MAX_TOOL_ROUNDS}. */
  public static final String PROPERTY_MAX_TOOL_ROUNDS = "copilot.engine.java.max.tool.rounds";

  /**
   * Rounds accepted for a single question before the exchange is cut short.
   * <p>
   * This is a backstop against a model that never stops calling tools, not a flow-control knob, so
   * it has to sit well above what real work needs. A single "create a record" request already
   * spends most of a dozen rounds: read the screen, find the window, open it, start a new record,
   * list the fields, write them, save, read the screen back to check the save actually took — and
   * more if a validation error sends it round again.
   */
  public static final int DEFAULT_MAX_TOOL_ROUNDS = 25;

  private ClientToolSupport() {
  }

  /**
   * Copy the client-tool fields from the incoming HTTP payload into the agent request, leaving the
   * rest of {@code buildRequestJson}'s output untouched.
   *
   * @param clientRequest
   *     the JSON body received from the chat client; may be {@code null}
   * @param agentRequest
   *     the payload being assembled for the engine
   * @throws JSONException
   *     when the fields cannot be copied
   */
  public static void copyClientToolFields(JSONObject clientRequest, JSONObject agentRequest)
      throws JSONException {
    if (clientRequest == null) {
      return;
    }
    if (clientRequest.has(PROP_CLIENT_TOOLS)) {
      agentRequest.put(PROP_CLIENT_TOOLS, clientRequest.get(PROP_CLIENT_TOOLS));
    }
    if (clientRequest.has(PROP_TOOL_EXCHANGE)) {
      agentRequest.put(PROP_TOOL_EXCHANGE, clientRequest.get(PROP_TOOL_EXCHANGE));
    }
  }

  /**
   * @param answer
   *     an answer object, or the {@code {"answer": ...}} envelope around one
   * @return {@code true} when the answer asks the client to execute tools rather than carrying text
   */
  public static boolean isToolCallAnswer(JSONObject answer) {
    if (answer == null) {
      return false;
    }
    JSONObject candidate = answer.optJSONObject(RestServiceUtil.PROP_ANSWER);
    if (candidate == null) {
      candidate = answer;
    }
    JSONArray calls = candidate.optJSONArray(PROP_TOOL_CALLS);
    return calls != null && calls.length() > 0;
  }

  /**
   * Translate the descriptors the client offered into LangChain4j tool specifications.
   * <p>
   * The parameter schema arrives as a JSON Schema. Each top-level property is passed through as a
   * raw schema instead of being translated field by field, so arbitrary nesting survives untouched.
   * WebMCP hands {@code inputSchema} over as a JSON <i>string</i>, so both a string and an object
   * are accepted.
   *
   * @param request
   *     the agent request payload
   * @return the specifications to offer the model; empty when the client offered none
   */
  public static List<ToolSpecification> toolSpecifications(JSONObject request) {
    List<ToolSpecification> specifications = new ArrayList<>();
    JSONArray clientTools = request.optJSONArray(PROP_CLIENT_TOOLS);
    if (clientTools == null) {
      return specifications;
    }
    for (int i = 0; i < clientTools.length(); i++) {
      JSONObject tool = clientTools.optJSONObject(i);
      if (tool == null || StringUtils.isBlank(tool.optString(PROP_NAME, null))) {
        log.warn("Ignoring a client tool descriptor without a name at position {}", i);
        continue;
      }
      ToolSpecification.Builder builder = ToolSpecification.builder().name(tool.optString(PROP_NAME));
      String description = tool.optString(PROP_DESCRIPTION, null);
      if (StringUtils.isNotBlank(description)) {
        builder.description(description);
      }
      JsonObjectSchema parameters = parameters(tool);
      if (parameters != null) {
        builder.parameters(parameters);
      }
      specifications.add(builder.build());
    }
    return specifications;
  }

  /**
   * Build the parameter schema of one tool descriptor.
   *
   * @param tool
   *     the descriptor
   * @return the schema, or {@code null} when the tool takes no arguments or declares none
   */
  private static JsonObjectSchema parameters(JSONObject tool) {
    JSONObject schema = asJsonObject(tool.opt(PROP_INPUT_SCHEMA), tool.optString(PROP_NAME, "?"));
    if (schema == null) {
      return null;
    }
    JSONObject properties = schema.optJSONObject("properties");
    if (properties == null || properties.length() == 0) {
      return null;
    }
    JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
    String description = schema.optString(PROP_DESCRIPTION, null);
    if (StringUtils.isNotBlank(description)) {
      builder.description(description);
    }
    JSONArray names = properties.names();
    for (int i = 0; names != null && i < names.length(); i++) {
      String property = names.optString(i);
      JSONObject sub = properties.optJSONObject(property);
      if (sub != null) {
        builder.addProperty(property, JsonRawSchema.from(sub.toString()));
      }
    }
    List<String> required = new ArrayList<>();
    JSONArray requiredNames = schema.optJSONArray("required");
    for (int i = 0; requiredNames != null && i < requiredNames.length(); i++) {
      required.add(requiredNames.optString(i));
    }
    if (!required.isEmpty()) {
      builder.required(required);
    }
    return builder.build();
  }

  /**
   * Accept a JSON object either as an object or as its string serialisation. WebMCP delivers
   * {@code inputSchema} as a string, unlike the rest of the descriptor.
   *
   * @param raw
   *     the value read from the descriptor
   * @param toolName
   *     the tool name, for the log message
   * @return the parsed object, or {@code null} when absent or unparseable
   */
  private static JSONObject asJsonObject(Object raw, String toolName) {
    if (raw instanceof JSONObject) {
      return (JSONObject) raw;
    }
    if (raw instanceof String && StringUtils.isNotBlank((String) raw)) {
      try {
        return new JSONObject((String) raw);
      } catch (JSONException e) {
        log.warn("Client tool '{}' has an inputSchema that is not valid JSON; ignoring it", toolName, e);
      }
    }
    return null;
  }

  /**
   * Append the rounds already exchanged for this question to the message list: for each round, the
   * assistant message that asked for the tools followed by one result message per call.
   *
   * @param messages
   *     the message list being built
   * @param request
   *     the agent request payload
   * @throws OBException
   *     when the client sent more rounds than the configured maximum
   */
  public static void appendExchange(List<ChatMessage> messages, JSONObject request) {
    JSONArray exchange = request.optJSONArray(PROP_TOOL_EXCHANGE);
    if (exchange == null || exchange.length() == 0) {
      return;
    }
    int maxRounds = maxToolRounds();
    if (exchange.length() > maxRounds) {
      throw new OBException(String.format(
          "Copilot (Java engine): the client sent %d tool rounds for a single question, the limit is %d. "
              + "Raise it with %s in Openbravo.properties.",
          exchange.length(), maxRounds, PROPERTY_MAX_TOOL_ROUNDS));
    }
    for (int round = 0; round < exchange.length(); round++) {
      JSONObject step = exchange.optJSONObject(round);
      if (step == null) {
        continue;
      }
      List<ToolExecutionRequest> calls = executionRequests(step.optJSONArray(PROP_CALLS));
      if (calls.isEmpty()) {
        continue;
      }
      messages.add(AiMessage.from(calls));
      appendResults(messages, step.optJSONArray(PROP_RESULTS), calls);
    }
  }

  /**
   * @return the configured round limit, or {@link #DEFAULT_MAX_TOOL_ROUNDS} when unset or invalid
   */
  public static int maxToolRounds() {
    String configured;
    try {
      configured = readPropertyWithLegacyCompatibility(
          OBPropertiesProvider.getInstance().getOpenbravoProperties(), PROPERTY_MAX_TOOL_ROUNDS, null);
    } catch (RuntimeException e) {
      // No Openbravo properties around (a plain unit test, for one). The default is the answer.
      return DEFAULT_MAX_TOOL_ROUNDS;
    }
    if (StringUtils.isBlank(configured)) {
      return DEFAULT_MAX_TOOL_ROUNDS;
    }
    try {
      int value = Integer.parseInt(configured.trim());
      return value > 0 ? value : DEFAULT_MAX_TOOL_ROUNDS;
    } catch (NumberFormatException e) {
      log.warn("Invalid value '{}' for {}, using {}", configured, PROPERTY_MAX_TOOL_ROUNDS,
          DEFAULT_MAX_TOOL_ROUNDS);
      return DEFAULT_MAX_TOOL_ROUNDS;
    }
  }

  /**
   * Add one result message per executed call, in the order the calls were requested.
   *
   * @param messages
   *     the message list being built
   * @param results
   *     the results reported by the client
   * @param calls
   *     the calls of this round, used to fill in a missing result
   */
  private static void appendResults(List<ChatMessage> messages, JSONArray results,
      List<ToolExecutionRequest> calls) {
    for (ToolExecutionRequest call : calls) {
      String text = findResult(results, call.id());
      if (text == null) {
        // The model must see something for every call it made, or the exchange is malformed.
        text = "The client did not return a result for this call.";
        log.warn("No client result for tool call '{}' ({})", call.id(), call.name());
      }
      messages.add(ToolExecutionResultMessage.from(call.id(), call.name(), text));
    }
  }

  /**
   * @param results
   *     the results reported by the client
   * @param callId
   *     the call to look up
   * @return the result text, or {@code null} when the client reported none
   */
  private static String findResult(JSONArray results, String callId) {
    for (int i = 0; results != null && i < results.length(); i++) {
      JSONObject result = results.optJSONObject(i);
      if (result != null && StringUtils.equals(result.optString(PROP_ID, null), callId)) {
        return result.optString(PROP_RESULT, "");
      }
    }
    return null;
  }

  /**
   * @param calls
   *     the calls of one round as reported by the client
   * @return the parsed execution requests, skipping malformed entries
   */
  private static List<ToolExecutionRequest> executionRequests(JSONArray calls) {
    List<ToolExecutionRequest> requests = new ArrayList<>();
    for (int i = 0; calls != null && i < calls.length(); i++) {
      JSONObject call = calls.optJSONObject(i);
      if (call == null || StringUtils.isBlank(call.optString(PROP_NAME, null))) {
        continue;
      }
      requests.add(ToolExecutionRequest.builder()
          .id(call.optString(PROP_ID, null))
          .name(call.optString(PROP_NAME))
          .arguments(call.optString(PROP_ARGUMENTS, "{}"))
          .build());
    }
    return requests;
  }

  /**
   * Build the answer that asks the client to execute tools and come back.
   *
   * @param aiMessage
   *     the assistant message carrying the tool execution requests
   * @param conversationId
   *     the conversation the calls belong to
   * @return the answer object
   * @throws JSONException
   *     when the answer cannot be assembled
   */
  public static JSONObject toolCallAnswer(AiMessage aiMessage, String conversationId) throws JSONException {
    return toolCallAnswer(aiMessage, conversationId, null);
  }

  /**
   * Build a mixed client/server call envelope. Server results are echoed by the client in that
   * round's normal {@code tool_exchange.results} array.
   */
  public static JSONObject toolCallAnswer(AiMessage aiMessage, String conversationId,
      JSONArray serverResults) throws JSONException {
    JSONArray calls = new JSONArray();
    for (ToolExecutionRequest requested : aiMessage.toolExecutionRequests()) {
      JSONObject call = new JSONObject();
      call.put(PROP_ID, requested.id());
      call.put(PROP_NAME, requested.name());
      call.put(PROP_ARGUMENTS, requested.arguments());
      calls.put(call);
    }
    JSONObject answer = new JSONObject();
    answer.put("role", ROLE_TOOL_CALL);
    answer.put(PROP_TOOL_CALLS, calls);
    // Any text the model produced alongside the calls, so the client can show progress.
    answer.put(RestServiceUtil.PROP_RESPONSE, StringUtils.defaultString(aiMessage.text()));
    if (StringUtils.isNotEmpty(conversationId)) {
      answer.put(RestServiceUtil.PROP_CONVERSATION_ID, conversationId);
    }
    if (serverResults != null && serverResults.length() > 0) {
      answer.put(PROP_SERVER_RESULTS, serverResults);
    }
    return answer;
  }
}
