/*
 *************************************************************************
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry;
import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.rest.RestServiceUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
public class TeamEngineTest {
  private static final String MEMBER_TOOL = "team_member_researcher";

  @Test
  public void routesToMemberAndStreamsProgressBeforeFinalAnswer() throws Exception {
    AtomicInteger supervisorCalls = new AtomicInteger();
    ChatModel supervisor = scripted(request -> supervisorCalls.getAndIncrement() == 0
        ? toolCall(MEMBER_TOOL, "call-1", "{\"task\":\"find facts\"}") : AiMessage.from("Here is the answer."));
    AtomicInteger memberHistoryMessages = new AtomicInteger();
    ChatModel member = scripted(request -> {
      memberHistoryMessages.set(request.messages().size());
      return AiMessage.from("Research result");
    });
    StringWriter output = new StringWriter();
    HttpServletResponse response = response(output);
    JSONObject result = engine(request(), supervisor, member).execute(true, response, request(), app());

    assertEquals("Here is the answer.", result.getString(RestServiceUtil.PROP_RESPONSE));
    String frames = output.toString();
    assertTrue(frames.indexOf("\"role\":\"node\"") < frames.indexOf("Delegating to Researcher"));
    assertTrue(frames.indexOf("Delegating to Researcher") < frames.indexOf("Here is the answer."));
    assertTrue(frames.contains("\"role\":\"node\""));
    assertTrue(frames.contains("\"response\":\"Researcher: Research result\""));
    assertEquals(4, memberHistoryMessages.get());
    assertTrue(frames.contains("\"response\":\"Here is the answer.\""));
  }
  @Test
  public void structuredOutputAndUsageIncludeSupervisorAndMemberCalls() throws Exception {
    JSONObject request = request().put(RestServiceUtil.PROP_OUTPUT_SCHEMA,
        "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}");
    List<ChatRequest> supervisorRequests = new ArrayList<>();
    List<ChatRequest> memberRequests = new ArrayList<>();
    AtomicInteger supervisorCalls = new AtomicInteger();
    ChatModel supervisor = scriptedWithUsage(supervisorRequests, new TokenUsage(1, 2), ignored ->
        supervisorCalls.getAndIncrement() == 0
            ? toolCall(MEMBER_TOOL, "usage-call", "{\"task\":\"research\"}") : AiMessage.from("{\"ok\":true}"));
    ChatModel member = scriptedWithUsage(memberRequests, new TokenUsage(3, 4),
        ignored -> AiMessage.from("evidence"));

    JSONObject answer = engine(request, supervisor, member).execute(false, null, request, app())
        .getJSONObject(RestServiceUtil.PROP_ANSWER);

    assertEquals(5, answer.getJSONObject("metadata").getInt("input_tokens"));
    assertEquals(8, answer.getJSONObject("metadata").getInt("output_tokens"));
    assertEquals(dev.langchain4j.model.chat.request.ResponseFormatType.JSON,
        supervisorRequests.get(0).responseFormat().type());
    assertEquals(null, memberRequests.get(0).responseFormat());
  }

  @Test
  public void memberFailureIsIsolatedAndSupervisorCanFinish() throws Exception {
    AtomicInteger supervisorCalls = new AtomicInteger();
    ChatModel supervisor = scripted(request -> supervisorCalls.getAndIncrement() == 0
        ? toolCall(MEMBER_TOOL, "call-1", "{\"task\":\"do work\"}") : AiMessage.from("Partial but useful answer."));
    ChatModel failedMember = scripted(request -> { throw new IllegalStateException("private provider detail"); });
    StringWriter output = new StringWriter();
    JSONObject result = engine(request(), supervisor, failedMember).execute(true, response(output), request(), app());

    assertEquals("Partial but useful answer.", result.getString(RestServiceUtil.PROP_RESPONSE));
    assertTrue(output.toString().contains("\"role\":\"error\""));
    assertTrue(output.toString().contains("failed; continue with another member"));
    assertTrue(!output.toString().contains("private provider detail"));
  }

  @Test(expected = OBException.class)
  public void supervisorIterationLimitStopsCycles() throws Exception {
    ChatModel supervisor = scripted(request -> toolCall(MEMBER_TOOL, "call", "{\"task\":\"repeat\"}"));
    ChatModel member = scripted(request -> AiMessage.from("done"));
    engine(request(), supervisor, member).execute(false, null, request(), app());
  }

  @Test
  public void memberToolRoundsAreBounded() throws Exception {
    AtomicInteger supervisorCalls = new AtomicInteger();
    AtomicInteger memberCalls = new AtomicInteger();
    AtomicInteger toolExecutions = new AtomicInteger();
    ChatModel supervisor = scripted(request -> supervisorCalls.getAndIncrement() == 0
        ? toolCall(MEMBER_TOOL, "dispatch", "{\"task\":\"repeat\"}") : AiMessage.from("Done"));
    ChatModel member = scripted(request -> {
      int call = memberCalls.incrementAndGet();
      return toolCall("LoopTool", "loop-" + call, "{}");
    });
    ServerTool loopTool = new ServerTool() {
      @Override
      public String name() { return "LoopTool"; }

      @Override
      public ToolSpecification specification() {
        return ToolSpecification.builder().name(name()).description("Test loop tool").build();
      }

      @Override
      public String execute(JSONObject arguments, ToolExecutionContext context) {
        toolExecutions.incrementAndGet();
        return "again";
      }
    };
    ToolExecutionContext context = mock(ToolExecutionContext.class);
    when(context.webServiceEnabled()).thenReturn(true);
    when(context.call(any(Duration.class), any(Callable.class))).thenAnswer(invocation ->
        ((Callable<?>) invocation.getArgument(1)).call());
    ServerToolRegistry registry = new ServerToolRegistry(name -> "LoopTool".equals(name) ? loopTool : null);
    JSONObject request = request();
    request.getJSONArray("assistants").getJSONObject(0).put("tools",
        new JSONArray().put(new JSONObject().put("type", "function")
            .put("function", new JSONObject().put("name", "LoopTool"))));
    TeamEngine team = new TeamEngine(registry,
        modelRequest -> "langgraph".equals(modelRequest.optString("type")) ? supervisor : member,
        (app, conversation, extra) -> context);

    StringWriter output = new StringWriter();
    JSONObject result = team.execute(true, response(output), request, app());

    assertEquals("Done", result.getString(RestServiceUtil.PROP_RESPONSE));
    assertEquals(26, memberCalls.get());
    assertEquals(25, toolExecutions.get());
    String frames = output.toString();
    assertTrue(frames.contains("\"role\":\"tool\""));
    assertTrue(frames.indexOf("Delegating to Researcher") < frames.indexOf("Researcher is using tool LoopTool"));
    assertTrue(frames.indexOf("Researcher is using tool LoopTool") < frames.indexOf("Writing the answer"));
    assertTrue(frames.indexOf("Writing the answer") < frames.indexOf("\"response\":\"Done\""));
  }

  private TeamEngine engine(JSONObject root, ChatModel supervisor, ChatModel member) {
    return new TeamEngine(new ServerToolRegistry(name -> null), request ->
        "langgraph".equals(request.optString("type")) ? supervisor : member, (app, conversation, extra) -> null);
  }

  private JSONObject request() {
    try {
      JSONObject member = new JSONObject().put("name", "Researcher").put("type", "langchain")
          .put("description", "Researches facts").put("model", "test").put("provider", "openai")
          .put("system_prompt", "Research system prompt");
      JSONArray history = new JSONArray()
          .put(new JSONObject().put("role", "USER").put("content", "Earlier user turn"))
          .put(new JSONObject().put("role", "ASSISTANT").put("content", "Earlier assistant turn"))
          .put(new JSONObject().put("role", "ERROR").put("content", "Skip this transient error"));
      return new JSONObject().put("type", "langgraph").put("question", "Answer this")
          .put("conversation_id", "conversation-1").put("assistants", new JSONArray().put(member))
          .put("history", history).put("system_prompt", "Delegate work when helpful.");
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  private CopilotApp app() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getName()).thenReturn("Team");
    return app;
  }

  private HttpServletResponse response(StringWriter output) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(output));
    return response;
  }

  private ChatModel scriptedWithUsage(List<ChatRequest> requests, TokenUsage usage,
      java.util.function.Function<ChatRequest, AiMessage> behavior) {
    ChatModel model = mock(ChatModel.class);
    try {
      when(model.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
        ChatRequest request = invocation.getArgument(0);
        requests.add(request);
        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(behavior.apply(request));
        when(response.tokenUsage()).thenReturn(usage);
        return response;
      });
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    return model;
  }
  private ChatModel scripted(java.util.function.Function<ChatRequest, AiMessage> behavior) {
    ChatModel model = mock(ChatModel.class);
    try {
      when(model.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
        AiMessage aiMessage = behavior.apply(invocation.getArgument(0));
        ChatResponse response = mock(ChatResponse.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        return response;
      });
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    return model;
  }

  private AiMessage toolCall(String name, String id, String arguments) {
    return AiMessage.from(Collections.singletonList(ToolExecutionRequest.builder().id(id).name(name)
        .arguments(arguments).build()));
  }
}
