/*
 *************************************************************************
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.etendoerp.copilot.engine.team.TeamMemoryRepository;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry.ResolvedTools;
import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.engine.mcp.McpClientToolLoader;
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
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;

/** In-process, bounded supervisor loop for team (langgraph) requests. */
public final class TeamEngine {
  private static final Logger log = LogManager.getLogger(TeamEngine.class);
  private static final int MAX_ITERATIONS = 100;
  private final ServerToolRegistry tools;
  private final Function<JSONObject, ChatModel> modelProvider;
  private final LangChain4jEngine.ContextProvider contextProvider;

  TeamEngine(ServerToolRegistry tools, Function<JSONObject, ChatModel> modelProvider,
      LangChain4jEngine.ContextProvider contextProvider) {
    this.tools = tools;
    this.modelProvider = modelProvider;
    this.contextProvider = contextProvider;
  }

  JSONObject execute(boolean async, HttpServletResponse response, JSONObject request, CopilotApp app)
      throws IOException, JSONException {
    if (async) LangChain4jEngine.beginTeamStream(response);
    Map<String, JSONObject> members = members(request.optJSONArray("assistants"));
    if (members.isEmpty()) throw new OBException("Copilot team has no supported members.");
    TeamMcpTools mcpTools = loadMcpTools(request, members);
    try {
      return executeLoaded(async, response, request, app, members, mcpTools);
    } finally {
      mcpTools.close();
    }
  }

  private JSONObject executeLoaded(boolean async, HttpServletResponse response, JSONObject request, CopilotApp app,
      Map<String, JSONObject> members, TeamMcpTools mcpTools) throws IOException, JSONException {
    String conversationId = request.optString(RestServiceUtil.PROP_CONVERSATION_ID, null);
    ResolvedTools supervisorTools = resolveTools(request);
    boolean needsToolContext = supervisorTools.hasTools() || hasMemberTools(members);
    ToolExecutionContext context = null;
    try {
      context = contextProvider.capture(app, conversationId, request.optJSONObject("extra_info"));
    } catch (RuntimeException e) {
      if (needsToolContext) log.warn("Unable to capture user context for team tools for agent '{}'.", app.getName());
    }

    Map<String, JSONObject> memberByToolName = new HashMap<>();
    Map<String, String> memberKeyByToolName = new HashMap<>();
    List<ToolSpecification> specifications = new ArrayList<>(supervisorTools.specifications());
    specifications.addAll(mcpTools.supervisor.specifications());
    Set<String> names = new HashSet<>();
    for (ToolSpecification specification : specifications) names.add(specification.name());
    for (Map.Entry<String, JSONObject> entry : members.entrySet()) {
      String toolName = memberToolName(entry.getKey());
      if (!names.add(toolName)) throw new OBException("Team member tool name conflicts with another team tool: " + toolName);
      memberByToolName.put(toolName, entry.getValue());
      memberKeyByToolName.put(toolName, entry.getKey());
      specifications.add(ToolSpecification.builder().name(toolName)
          .description(StringUtils.defaultIfBlank(entry.getValue().optString("description", ""), "Delegate work to " + entry.getKey()))
          .parameters(JsonObjectSchema.builder()
              .addProperty("task", JsonRawSchema.from("{\"type\":\"string\",\"description\":\"Task or instruction for this team member\"}"))
              .required(List.of("task")).build()).build());
    }

    List<ChatMessage> messages = supervisorMessages(request, members);
    LangChain4jEngine.Usage usage = new LangChain4jEngine.Usage();
    ChatModel supervisor;
    try {
      supervisor = modelProvider.apply(request);
    } catch (RuntimeException e) {
      emit(async, response, "error", "The team supervisor could not be initialized.", conversationId);
      throw new OBException("Copilot team supervisor could not be initialized.", e);
    }
    Map<String, List<ChatMessage>> memberMemories = new HashMap<>();
    emit(async, response, "node", "Supervisor is thinking…", conversationId);
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      AiMessage answer;
      try {
        ChatRequest.Builder chatRequest = ChatRequest.builder().messages(messages);
        if (!specifications.isEmpty()) chatRequest.toolSpecifications(specifications);
        String outputSchema = request.optString(RestServiceUtil.PROP_OUTPUT_SCHEMA, "");
        if (StringUtils.isNotBlank(outputSchema)) {
          JsonSchema schema = JsonSchema.builder().name("response")
              .rootElement(JsonRawSchema.from(new JSONObject(outputSchema).toString())).build();
          chatRequest.responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON)
              .jsonSchema(schema).build());
        }
        dev.langchain4j.model.chat.response.ChatResponse modelResponse = supervisor.chat(chatRequest.build());
        usage.add(modelResponse.tokenUsage());
        answer = modelResponse.aiMessage();
      } catch (RuntimeException e) {
        log.error("Team supervisor model failed for agent '{}'.", app.getName(), e);
        emit(async, response, "error", "The team supervisor could not complete the request.", conversationId);
        throw new OBException("Copilot team supervisor failed: " + e.getMessage(), e);
      }
      if (answer == null) {
        emit(async, response, "error", "The team supervisor returned no response.", conversationId);
        throw new OBException("Copilot team supervisor returned no response.");
      }
      if (!answer.hasToolExecutionRequests()) {
        String finalText = answer.text();
        if (StringUtils.isBlank(finalText)) {
          emit(async, response, "error", "The team supervisor returned an empty response.", conversationId);
          throw new OBException("Copilot team supervisor returned an empty response.");
        }
        emit(async, response, "node", "Writing the answer…", conversationId);
        emit(async, response, null, finalText, conversationId, usage);
        return answerObject(finalText, conversationId, async, usage);
      }

      messages.add(answer);
      boolean delegated = false;
      for (ToolExecutionRequest call : answer.toolExecutionRequests()) {
        JSONObject member = memberByToolName.get(call.name());
        String result;
        if (member != null) {
          delegated = true;
          String task = new JSONObject(call.arguments()).optString("task", "");
          emit(async, response, "node", "Delegating to " + member.optString("name") + "…", conversationId);
          boolean memberFailed = false;
          try {
            result = runMember(member, task, request.optJSONArray(RestServiceUtil.PROP_HISTORY),
                context, app, memberMemories, async, response, conversationId,
                mcpTools.members.get(memberKeyByToolName.get(call.name())), usage);
          } catch (Throwable failure) {
            log.warn("Team member '{}' failed; continuing supervisor loop ({}).", member.optString("name"),
                failure.getClass().getSimpleName());
            result = "Team member '" + member.optString("name") + "' failed; continue with another member or answer based on available information.";
            memberFailed = true;
            emit(async, response, "error", result, conversationId);
          }
          if (!memberFailed) emit(async, response, "node", member.optString("name") + ": " + result, conversationId);
        } else {
          emit(async, response, "tool",
              (mcpTools.supervisor.hasTool(call.name()) ? "Using MCP tool " : "Using tool ")
                  + call.name() + "…", conversationId);
          result = LangChain4jEngine.executeServerTool(supervisorTools.tool(call.name()), mcpTools.supervisor,
              call, context, app);
        }
        messages.add(ToolExecutionResultMessage.from(call.id(), call.name(), StringUtils.defaultString(result)));
      }
      emit(async, response, "node", delegated ? "Supervisor is thinking…" : "Supervisor is processing tool results…", conversationId);
    }
    emit(async, response, "error", "The team reached its maximum number of supervisor iterations.", conversationId);
    throw new OBException("Copilot team reached the maximum of " + MAX_ITERATIONS + " supervisor iterations.");
  }
  private TeamMcpTools loadMcpTools(JSONObject request, Map<String, JSONObject> members) {
    Set<String> supervisorNames = new HashSet<>();
    for (ToolSpecification specification : resolveTools(request).specifications()) {
      supervisorNames.add(specification.name());
    }
    for (Map.Entry<String, JSONObject> entry : members.entrySet()) {
      supervisorNames.add(memberToolName(entry.getKey()));
    }
    McpClientToolLoader loader = new McpClientToolLoader();
    McpClientToolLoader.LoadedTools supervisor = loader.load(request.optJSONArray("mcp_servers"), supervisorNames);
    Map<String, McpClientToolLoader.LoadedTools> memberTools = new HashMap<>();
    try {
      for (Map.Entry<String, JSONObject> entry : members.entrySet()) {
        Set<String> reserved = new HashSet<>();
        for (ToolSpecification specification : resolveTools(entry.getValue()).specifications()) {
          reserved.add(specification.name());
        }
        memberTools.put(entry.getKey(),
            loader.load(entry.getValue().optJSONArray("mcp_servers"), reserved));
      }
      return new TeamMcpTools(supervisor, memberTools);
    } catch (RuntimeException e) {
      supervisor.close();
      for (McpClientToolLoader.LoadedTools loaded : memberTools.values()) loaded.close();
      throw e;
    }
  }

  private static final class TeamMcpTools implements AutoCloseable {
    private final McpClientToolLoader.LoadedTools supervisor;
    private final Map<String, McpClientToolLoader.LoadedTools> members;

    private TeamMcpTools(McpClientToolLoader.LoadedTools supervisor,
        Map<String, McpClientToolLoader.LoadedTools> members) {
      this.supervisor = supervisor;
      this.members = members;
    }

    @Override
    public void close() {
      supervisor.close();
      for (McpClientToolLoader.LoadedTools loaded : members.values()) loaded.close();
    }
  }



  private String runMember(JSONObject member, String task, JSONArray history, ToolExecutionContext context,
      CopilotApp app, Map<String, List<ChatMessage>> memories, boolean async, HttpServletResponse response,
      String conversationId, McpClientToolLoader.LoadedTools memberMcpTools, LangChain4jEngine.Usage usage) {
    String type = member.optString("type", "");
    if (!CopilotConstants.APP_TYPE_LANGCHAIN.equalsIgnoreCase(type)
        && !CopilotConstants.APP_TYPE_MULTIMODEL.equalsIgnoreCase(type)) {
      throw new OBException("Unsupported team member type '" + type + "'.");
    }
    ChatModel model = modelProvider.apply(member);
    String name = member.optString("name", "member");
    emit(async, response, "node", name + " is thinking…", conversationId);
    ResolvedTools memberTools = resolveTools(member);
    if (memberTools.hasTools() && context == null) {
      return "The user's context for server tool execution could not be initialized.";
    }
    String memberId = StringUtils.defaultIfBlank(member.optString(RestServiceUtil.PROP_ASSISTANT_ID, null), name);
    List<ChatMessage> messages = memories.computeIfAbsent(memberId, ignored ->
        TeamMemoryRepository.DEFAULT.load(context, conversationId, memberId,
            initialMemberMessages(member, history)));
    messages.add(UserMessage.from(task));
    int maxToolRounds = ClientToolSupport.maxToolRounds();
    for (int round = 0; round <= maxToolRounds; round++) {
      ChatRequest.Builder req = ChatRequest.builder().messages(messages);
      if (memberTools.hasTools()) req.toolSpecifications(memberTools.specifications());
      if (memberMcpTools.hasTools()) req.toolSpecifications(memberMcpTools.specifications());
      dev.langchain4j.model.chat.response.ChatResponse modelResponse = model.chat(req.build());
      usage.add(modelResponse.tokenUsage());
      AiMessage answer = modelResponse.aiMessage();
      if (answer == null) throw new OBException("Member '" + name + "' returned no response.");
      if (!answer.hasToolExecutionRequests()) {
        String text = answer.text();
        if (StringUtils.isBlank(text)) throw new OBException("Member '" + name + "' returned an empty response.");
        messages.add(answer);
        TeamMemoryRepository.DEFAULT.save(context, conversationId, memberId, messages);
        return text;
      }
      if (round == maxToolRounds) return StringUtils.defaultIfBlank(answer.text(), "Member tool round limit reached.");
      messages.add(answer);
      for (ToolExecutionRequest call : answer.toolExecutionRequests()) {
        emit(async, response, "tool", name + " is using tool " + call.name() + "…", conversationId);
        String result = LangChain4jEngine.executeServerTool(memberTools.tool(call.name()), memberMcpTools,
            call, context, app);
        messages.add(ToolExecutionResultMessage.from(call.id(), call.name(), result));
      }
      }
    return "Member tool round limit reached.";
  }

  private List<ChatMessage> initialMemberMessages(JSONObject member, JSONArray history) {
    List<ChatMessage> messages = new ArrayList<>();
    String prompt = member.optString("system_prompt", null);
    if (StringUtils.isNotBlank(prompt)) messages.add(SystemMessage.from(prompt));
    if (history != null) {
      for (int i = 0; i < history.length(); i++) {
        JSONObject stored = history.optJSONObject(i);
        if (stored == null) continue;
        String content = stored.optString("content", null);
        if (StringUtils.isBlank(content)) continue;
        String role = stored.optString("role", "");
        if ("USER".equalsIgnoreCase(role)) messages.add(UserMessage.from(content));
        else if ("ASSISTANT".equalsIgnoreCase(role)) messages.add(AiMessage.from(content));
      }
    }
    return messages;
  }


  private Map<String, JSONObject> members(JSONArray source) throws JSONException {
    Map<String, JSONObject> result = new java.util.LinkedHashMap<>();
    if (source == null) return result;
    for (int i = 0; i < source.length(); i++) {
      JSONObject member = source.getJSONObject(i);
      String name = member.optString("name", "");
      if (StringUtils.isBlank(name)) continue;
      String stableId = StringUtils.defaultIfBlank(member.optString(RestServiceUtil.PROP_ASSISTANT_ID, null), name);
      String key = stableId;
      int duplicate = 1;
      while (result.containsKey(key)) key = stableId + "_" + duplicate++;
      result.put(key, member);
    }
    return result;
  }

  private boolean hasMemberTools(Map<String, JSONObject> members) {
    for (JSONObject member : members.values()) {
      if ((member.optJSONArray("tools") != null && member.optJSONArray("tools").length() > 0)
          || (member.optJSONArray("specs") != null && member.optJSONArray("specs").length() > 0)) return true;
    }
    return false;
  }
  private ResolvedTools resolveTools(JSONObject request) {
    return tools.resolve(request, false,
        new com.etendoerp.copilot.engine.tool.openapi.OpenApiToolGenerator()
            .tools(request.optJSONArray("specs")));
  }

  private List<ChatMessage> supervisorMessages(JSONObject request, Map<String, JSONObject> members)
      throws JSONException {
    List<ChatMessage> messages = new ArrayList<>();
    StringBuilder prompt = new StringBuilder(request.optString(RestServiceUtil.PROP_SYSTEM_PROMPT, ""));
    prompt.append("\n\nYou coordinate a team. Delegate a concrete task by calling the matching team-member tool. "
        + "Use member results and declared tools to answer the user's request. Do not claim a member's work unless it returned a result.\nAvailable members:");
    for (Map.Entry<String, JSONObject> entry : members.entrySet()) {
      JSONObject member = entry.getValue();
      prompt.append("\n- ").append(member.optString("name", entry.getKey())).append(" (tool: ")
          .append(memberToolName(entry.getKey())).append("): ")
          .append(StringUtils.defaultIfBlank(member.optString("description", ""), "No description provided."));
    }
    messages.add(SystemMessage.from(prompt.toString()));
    JSONArray history = request.optJSONArray(RestServiceUtil.PROP_HISTORY);
    if (history != null) for (int i = 0; i < history.length(); i++) {
      JSONObject item = history.getJSONObject(i);
      String content = item.optString("content", null);
      if (StringUtils.isBlank(content)) continue;
      String role = item.optString("role", "");
      if ("USER".equalsIgnoreCase(role)) messages.add(UserMessage.from(content));
      else if ("ASSISTANT".equalsIgnoreCase(role)) messages.add(dev.langchain4j.data.message.AiMessage.from(content));
    }
    String question = request.optString(RestServiceUtil.PROP_QUESTION, null);
    if (StringUtils.isBlank(question)) throw new OBException("Copilot team request carries no question.");
    messages.add(LangChain4jEngine.userMessage(question, request.optJSONArray("local_file_ids")));
    return messages;
  }

  private String memberToolName(String memberName) {
    String safe = memberName.replaceAll("[^A-Za-z0-9_-]", "_");
    if (safe.length() > 50) safe = safe.substring(0, 50);
    return "team_member_" + safe.toLowerCase(java.util.Locale.ROOT);
  }

  private JSONObject answerObject(String text, String conversationId, boolean async,
      LangChain4jEngine.Usage usage) throws JSONException {
    JSONObject answer = new JSONObject().put(RestServiceUtil.PROP_RESPONSE, text);
    if (StringUtils.isNotEmpty(conversationId)) answer.put(RestServiceUtil.PROP_CONVERSATION_ID, conversationId);
    answer.put("role", JSONObject.NULL);
    JSONObject metadata = usage.metadata();
    if (metadata.length() > 0) answer.put("metadata", metadata);
    return async ? answer : new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer);
  }

  private boolean streamDebugEnabled() {
    String configured = System.getProperty("copilot.stream.debug");
    if (configured == null) {
      try {
        configured = org.openbravo.base.session.OBPropertiesProvider.getInstance()
            .getOpenbravoProperties().getProperty("copilot.stream.debug", "false");
      } catch (RuntimeException ignored) {
        return false;
      }
    }
    return Boolean.parseBoolean(configured);
  }

  private void emit(boolean async, HttpServletResponse response, String role, String text, String conversationId) {
    emit(async, response, role, text, conversationId, null);
  }

  private void emit(boolean async, HttpServletResponse response, String role, String text, String conversationId,
      LangChain4jEngine.Usage usage) {
    if (!async) return;
    try {
      JSONObject answer = new JSONObject().put(RestServiceUtil.PROP_RESPONSE, text);
      if (StringUtils.isNotEmpty(conversationId)) answer.put(RestServiceUtil.PROP_CONVERSATION_ID, conversationId);
      answer.put("role", role == null ? JSONObject.NULL : role);
      if (usage != null) {
        JSONObject metadata = usage.metadata();
        if (metadata.length() > 0) answer.put("metadata", metadata);
      }
      LangChain4jEngine.streamTeamFrame(response, answer);
      if (streamDebugEnabled() && !"debug".equals(role)) {
        JSONObject debug = new JSONObject().put(RestServiceUtil.PROP_RESPONSE,
            "Team event: " + StringUtils.defaultString(role, "final") + " — " + text);
        if (StringUtils.isNotEmpty(conversationId)) debug.put(RestServiceUtil.PROP_CONVERSATION_ID, conversationId);
        debug.put("role", "debug");
        LangChain4jEngine.streamTeamFrame(response, debug);
      }
    } catch (IOException | JSONException e) {
      throw new OBException("Unable to stream team progress event.", e);
    }
  }
}
