package com.etendoerp.copilot.engine;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.engine.mcp.McpClientToolLoader;
import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry.ResolvedTools;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.engine.tool.openapi.OpenApiToolGenerator;
import com.etendoerp.copilot.util.CopilotConstants;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * Runs the agent inside Tomcat with LangChain4j, so a plain chat agent needs no Python container.
 * <p>
 * The engine deliberately reads everything from the request payload rather than from the database:
 * the system prompt, the conversation history, the provider, the model and the temperature are all
 * already resolved by {@code RestServiceUtil.buildRequestJson}. The conversation memory is the one
 * Etendo already keeps in {@code ETCOP_MESSAGE} and ships as {@code history}, so no additional
 * memory store is involved.
 * <p>
 * Server-sent events keep the Python runtime's answer shape. Mixed tool-call responses carry the
 * additional server-result echo needed by the existing WebMCP POST loop; token-level streaming
 * remains intentionally out of scope because the front-end renders each SSE frame as a message.
 */
public class LangChain4jEngine implements CopilotEngine {

  private static final Logger log = LogManager.getLogger(LangChain4jEngine.class);

  private static final String PROP_ROLE = "role";
  private static final String PROP_CONTENT = "content";
  private static final String PROP_EXTRA_INFO = "extra_info";

  private final ServerToolRegistry serverToolRegistry;
  private final Function<JSONObject, ChatModel> modelProvider;
  private final ContextProvider contextProvider;

  public LangChain4jEngine() {
    this(new ServerToolRegistry(), ChatModelFactory::build, ToolExecutionContext::capture);
  }

  LangChain4jEngine(ServerToolRegistry serverToolRegistry, Function<JSONObject, ChatModel> modelProvider,
      ContextProvider contextProvider) {
    this.serverToolRegistry = serverToolRegistry;
    this.modelProvider = modelProvider;
    this.contextProvider = contextProvider;
  }

  public JSONObject execute(boolean asyncRequest, HttpServletResponse queue, JSONObject request,
      CopilotApp app) throws IOException, JSONException {
    boolean hasKnowledgeBase = app.getETCOPAppSourceList().stream().anyMatch(CopilotConstants::isKbBehaviour);
    ResolvedTools localTools = serverToolRegistry.resolve(request, hasKnowledgeBase,
        new OpenApiToolGenerator().tools(request.optJSONArray("specs")));
    List<ToolSpecification> clientTools = ClientToolSupport.toolSpecifications(request);
    Set<String> reserved = toolNames(localTools.specifications());
    reserved.addAll(toolNames(clientTools));
    McpClientToolLoader.LoadedTools mcpTools = new McpClientToolLoader()
        .load(request.optJSONArray("mcp_servers"), reserved);
    try {
      return executeWithMcp(asyncRequest, queue, request, app, mcpTools, localTools, clientTools);
    } finally {
      mcpTools.close();
    }
  }

  private JSONObject executeWithMcp(boolean asyncRequest, HttpServletResponse queue, JSONObject request,
      CopilotApp app, McpClientToolLoader.LoadedTools mcpTools, ResolvedTools serverTools,
      List<ToolSpecification> clientTools) throws IOException, JSONException {
    List<ChatMessage> messages = buildMessages(request);
    List<ToolSpecification> offeredTools = offeredSpecifications(serverTools, mcpTools, clientTools);
    String conversationId = request.optString(RestServiceUtil.PROP_CONVERSATION_ID, null);
    if (CopilotConstants.APP_TYPE_LANGGRAPH.equalsIgnoreCase(
        request.optString(RestServiceUtil.PROP_TYPE, ""))) {
      return new TeamEngine(serverToolRegistry, modelProvider, contextProvider)
          .execute(asyncRequest, queue, request, app);
    }
    // Tool descriptors and request-scoped MCP clients were resolved before entering this loop.

    // Capture once, before the first model call. A failure is returned as a tool result only if a
    // server tool is actually requested; chat-only and client-only behavior stays unchanged.
    ToolExecutionContext context = null;
    if (serverTools.hasTools()) {
      try {
        context = contextProvider.capture(app, conversationId, request.optJSONObject(PROP_EXTRA_INFO));
        context.attributes().put("kb_search_k", request.optInt(RestServiceUtil.PROP_KB_SEARCH_K, 4));
      } catch (RuntimeException e) {
        log.warn("Unable to capture the user's context for server tools for agent '{}'.", app.getName());
      }
    }

    ChatModel model = modelProvider.apply(request);
    AiMessage aiMessage = chat(model, messages, offeredTools, app);
    Set<String> clientToolNames = toolNames(clientTools);
    int serverToolRounds = 0;

    while (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
      List<ToolExecutionRequest> serverCalls = new ArrayList<>();
      List<ToolExecutionRequest> clientCalls = new ArrayList<>();
      for (ToolExecutionRequest call : aiMessage.toolExecutionRequests()) {
        if (serverTools.tool(call.name()) != null || mcpTools.hasTool(call.name())
            || !clientToolNames.contains(call.name())) {
          serverCalls.add(call);
        } else {
          clientCalls.add(call);
        }
      }
      if (!clientCalls.isEmpty()) {
        JSONArray serverResults = executeServerCalls(serverCalls, serverTools, mcpTools, context, app,
            serverToolRounds >= ClientToolSupport.maxToolRounds());
        JSONObject toolCall = serverCalls.isEmpty()
            ? ClientToolSupport.toolCallAnswer(aiMessage, conversationId)
            : ClientToolSupport.toolCallAnswer(aiMessage, conversationId, serverResults);
        log.info("Copilot engine: asking the client to execute {} tool(s) for agent '{}'",
            clientCalls.size(), app.getName());
        if (asyncRequest) {
          stream(queue, toolCall);
          return toolCall;
        }
        return new JSONObject().put(RestServiceUtil.PROP_ANSWER, toolCall);
      }

      if (serverToolRounds >= ClientToolSupport.maxToolRounds()) {
        String cappedAnswer = StringUtils.defaultIfBlank(aiMessage.text(),
            "The server tool round limit was reached. Please ask the assistant to continue.");
        return complete(asyncRequest, queue, cappedAnswer, conversationId);
      }

      serverToolRounds++;
      messages.add(aiMessage);
      for (ToolExecutionRequest call : aiMessage.toolExecutionRequests()) {
        String result = executeServerTool(serverTools.tool(call.name()), mcpTools, call, context, app);
        messages.add(ToolExecutionResultMessage.from(call.id(), call.name(), result));
      }
      aiMessage = chat(model, messages, offeredTools, app);
    }

    String answerText = aiMessage == null ? null : aiMessage.text();
    if (StringUtils.isEmpty(answerText)) {
      throw new OBException("Copilot (Java engine) returned an empty response.");
    }
    return complete(asyncRequest, queue, answerText, conversationId);
  }

  private List<ToolSpecification> offeredSpecifications(ResolvedTools serverTools,
      McpClientToolLoader.LoadedTools mcpTools, List<ToolSpecification> clientTools) {
    List<ToolSpecification> all = new ArrayList<>(serverTools.specifications());
    Set<String> names = toolNames(all);
    for (ToolSpecification mcpTool : mcpTools.specifications()) {
      if (names.add(mcpTool.name())) {
        all.add(mcpTool);
      }
    }
    for (ToolSpecification clientTool : clientTools) {
      if (names.add(clientTool.name())) {
        all.add(clientTool);
      }
    }
    return all;
  }

  private AiMessage chat(ChatModel model, List<ChatMessage> messages, List<ToolSpecification> tools,
      CopilotApp app) {
    try {
      ChatRequest.Builder request = ChatRequest.builder().messages(messages);
      if (!tools.isEmpty()) {
        request.toolSpecifications(tools);
      }
      return model.chat(request.build()).aiMessage();
    } catch (RuntimeException e) {
      log.error("The Java Copilot engine failed while calling the model for agent '{}'", app.getName(), e);
      throw new OBException("Copilot (Java engine) error: " + e.getMessage(), e);
    }
  }

  private JSONArray executeServerCalls(List<ToolExecutionRequest> calls, ResolvedTools tools,
      McpClientToolLoader.LoadedTools mcpTools, ToolExecutionContext context, CopilotApp app,
      boolean roundLimitReached) throws JSONException {
    JSONArray results = new JSONArray();
    for (ToolExecutionRequest call : calls) {
      String result = roundLimitReached
          ? "The server tool round limit was reached; this call was not executed."
          : executeServerTool(tools.tool(call.name()), mcpTools, call, context, app);
      results.put(toolResult(call, result));
    }
    return results;
  }

  static String executeServerTool(ServerTool tool, McpClientToolLoader.LoadedTools mcpTools,
      ToolExecutionRequest call, ToolExecutionContext context, CopilotApp app) {
    if (tool == null) {
      return mcpTools.hasTool(call.name()) ? mcpTools.execute(call.name(), call.arguments())
          : "Tool '" + call.name() + "' has no Java implementation.";
    }
    if (context == null) {
      return "The user's context for server tool execution could not be initialized.";
    }
    try {
      if (tool.needsWebService() && !context.webServiceEnabled()) {
        return "Tool '" + call.name() + "' requires a role with web services enabled.";
      }
      return context.call(tool.timeout(), () -> tool.execute(new JSONObject(call.arguments()), context));
    } catch (Throwable failure) {
      log.warn("Server tool '{}' failed for agent '{}' ({}).", call.name(), app.getName(),
          failure.getClass().getSimpleName());
      return "Tool '" + call.name() + "' failed during execution.";
    }
  }

  private JSONObject toolResult(ToolExecutionRequest call, String result) throws JSONException {
    JSONObject toolResult = new JSONObject();
    toolResult.put(ClientToolSupport.PROP_ID, call.id());
    toolResult.put(ClientToolSupport.PROP_NAME, call.name());
    toolResult.put(ClientToolSupport.PROP_RESULT, StringUtils.defaultString(result));
    return toolResult;
  }

  private Set<String> toolNames(List<ToolSpecification> tools) {
    Set<String> names = new HashSet<>();
    for (ToolSpecification tool : tools) {
      names.add(tool.name());
    }
    return names;
  }

  private JSONObject complete(boolean asyncRequest, HttpServletResponse queue, String answerText,
      String conversationId) throws IOException, JSONException {
    JSONObject answer = new JSONObject();
    answer.put(RestServiceUtil.PROP_RESPONSE, answerText);
    if (StringUtils.isNotEmpty(conversationId)) {
      answer.put(RestServiceUtil.PROP_CONVERSATION_ID, conversationId);
    }
    if (!asyncRequest) {
      return new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer);
    }
    stream(queue, answer);
    return answer;
  }

  interface ContextProvider {
    ToolExecutionContext capture(CopilotApp app, String conversationId, JSONObject extraInfo);
  }

  /**
   * Write the answer to the client as server-sent events, in the same frame shape the Python
   * runtime emits.
   *
   * @param queue
   *     the servlet response to stream to
   * @param answer
   *     the answer object to send
   * @throws IOException
   *     when the response writer cannot be obtained
   * @throws JSONException
   *     when the frame cannot be assembled
   */
  private void stream(HttpServletResponse queue, JSONObject answer) throws IOException, JSONException {
    RestServiceUtil.setEventStreamMode(queue);
    PrintWriter writerToFront = queue.getWriter();
    // The Python relay opens the stream with an empty frame; keep the same handshake.
    RestServiceUtil.sendEventToFront(writerToFront, new JSONObject(), true);
    RestServiceUtil.sendEventToFront(writerToFront, new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer),
        true);
    writerToFront.flush();
  }
  static void beginTeamStream(HttpServletResponse queue) throws IOException, JSONException {
    RestServiceUtil.setEventStreamMode(queue);
    PrintWriter writer = queue.getWriter();
    RestServiceUtil.sendEventToFront(writer, new JSONObject(), true);
    writer.flush();
  }

  static void streamTeamFrame(HttpServletResponse queue, JSONObject answer) throws IOException, JSONException {
    RestServiceUtil.setEventStreamMode(queue);
    PrintWriter writer = queue.getWriter();
    RestServiceUtil.sendEventToFront(writer, new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer), true);
    writer.flush();
  }


  /**
   * Turn the request payload into the message list handed to the model: the system prompt, the
   * stored conversation history and the current question.
   *
   * @param request
   *     the payload built by {@code RestServiceUtil.buildRequestJson}
   * @return the ordered message list
   * @throws JSONException
   *     when the history cannot be read
   */
  private List<ChatMessage> buildMessages(JSONObject request) throws JSONException {
    List<ChatMessage> messages = new ArrayList<>();

    String systemPrompt = request.optString(RestServiceUtil.PROP_SYSTEM_PROMPT, null);
    if (StringUtils.isNotBlank(systemPrompt)) {
      messages.add(SystemMessage.from(systemPrompt));
    }

    JSONArray history = request.optJSONArray(RestServiceUtil.PROP_HISTORY);
    if (history != null) {
      for (int i = 0; i < history.length(); i++) {
        addHistoryMessage(messages, history.getJSONObject(i));
      }
    }

    String question = request.optString(RestServiceUtil.PROP_QUESTION, null);
    if (StringUtils.isBlank(question)) {
      throw new OBException("Copilot (Java engine): the request carries no question.");
    }
    messages.add(UserMessage.from(question));

    // Rounds of client-executed tools already played out for this same question.
    ClientToolSupport.appendExchange(messages, request);

    return messages;
  }

  /**
   * Append one stored message to the model conversation. Messages tracked with the error role are
   * skipped: they describe a failure of a previous turn, not something the model said.
   *
   * @param messages
   *     the list being built
   * @param stored
   *     one entry of the {@code history} array, with {@code role} and {@code content}
   */
  private void addHistoryMessage(List<ChatMessage> messages, JSONObject stored) {
    String content = stored.optString(PROP_CONTENT, null);
    if (StringUtils.isBlank(content)) {
      return;
    }
    String role = stored.optString(PROP_ROLE, null);
    if (StringUtils.equalsIgnoreCase(role, CopilotConstants.MESSAGE_USER)) {
      messages.add(UserMessage.from(content));
    } else if (StringUtils.equalsIgnoreCase(role, CopilotConstants.MESSAGE_ASSISTANT)) {
      messages.add(AiMessage.from(content));
    } else {
      log.debug("Skipping history message with role '{}'", role);
    }
  }
}
