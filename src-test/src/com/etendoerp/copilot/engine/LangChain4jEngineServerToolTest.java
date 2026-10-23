/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"). You may not use this file except in compliance with
 * the License.
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.rest.RestServiceUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/** Pure loop tests using a fake model and a test-only server tool. */
public class LangChain4jEngineServerToolTest {

  private static final String SERVER_TOOL = "TestEchoTool";
  private static final String CLIENT_TOOL = "browser_lookup";

  @Test
  public void serverToolResultIsFedBackToTheModel() throws Exception {
    AtomicInteger toolExecutions = new AtomicInteger();
    ServerTool tool = tool(SERVER_TOOL, (arguments, context) -> {
      toolExecutions.incrementAndGet();
      return arguments.getString("value") + "-executed";
    });
    List<ChatRequest> requests = new ArrayList<>();
    ChatModel model = scriptedModel(requests,
        request(SERVER_TOOL, "call-1", "{\"value\":\"hello\"}"), AiMessage.from("finished"));
    ToolExecutionContext context = context();

    JSONObject response = engine(tool, model, context).execute(false, null, requestWithTools(), app());

    assertEquals("finished", response.getJSONObject(RestServiceUtil.PROP_ANSWER)
        .getString(RestServiceUtil.PROP_RESPONSE));
    assertEquals(1, toolExecutions.get());
    List<ChatMessage> secondCall = requests.get(1).messages();
    assertTrue(secondCall.get(secondCall.size() - 1) instanceof ToolExecutionResultMessage);
    assertEquals("hello-executed", ((ToolExecutionResultMessage) secondCall.get(secondCall.size() - 1)).text());
  }

  @Test
  public void failingServerToolBecomesAnErrorResultAndTheLoopContinues() throws Exception {
    ServerTool tool = tool(SERVER_TOOL, (arguments, context) -> {
      throw new IllegalStateException("secret-token-value");
    });
    List<ChatRequest> requests = new ArrayList<>();
    ChatModel model = scriptedModel(requests, request(SERVER_TOOL, "call-1", "{}"),
        AiMessage.from("I recovered"));

    JSONObject response = engine(tool, model, context()).execute(false, null, requestWithTools(), app());

    assertEquals("I recovered", response.getJSONObject(RestServiceUtil.PROP_ANSWER)
        .getString(RestServiceUtil.PROP_RESPONSE));
    ToolExecutionResultMessage result = (ToolExecutionResultMessage) requests.get(1).messages()
        .get(requests.get(1).messages().size() - 1);
    assertTrue(result.text().contains("failed during execution"));
    assertFalse(result.text().contains("secret-token-value"));
  }

  @Test
  public void webServiceToolIsRejectedForRolesWithoutTheCapability() throws Exception {
    AtomicInteger toolExecutions = new AtomicInteger();
    ServerTool tool = tool(SERVER_TOOL, true, (arguments, context) -> {
      toolExecutions.incrementAndGet();
      return "should not run";
    });
    List<ChatRequest> requests = new ArrayList<>();
    ChatModel model = scriptedModel(requests, request(SERVER_TOOL, "call-1", "{}"),
        AiMessage.from("I understand"));

    JSONObject response = engine(tool, model, context(false))
        .execute(false, null, requestWithTools(), app());

    assertEquals(0, toolExecutions.get());
    ToolExecutionResultMessage result = (ToolExecutionResultMessage) requests.get(1).messages()
        .get(requests.get(1).messages().size() - 1);
    assertTrue(result.text().contains("requires a role with web services enabled"));
    assertEquals("I understand", response.getJSONObject(RestServiceUtil.PROP_ANSWER)
        .getString(RestServiceUtil.PROP_RESPONSE));
  }

  @Test
  public void serverToolLoopStopsAtConfiguredRoundLimit() throws Exception {
    int limit = ClientToolSupport.maxToolRounds();
    AtomicInteger toolExecutions = new AtomicInteger();
    ServerTool tool = tool(SERVER_TOOL, (arguments, context) -> {
      toolExecutions.incrementAndGet();
      return "ok";
    });
    List<AiMessage> responses = new ArrayList<>();
    for (int i = 0; i <= limit; i++) {
      responses.add(request(SERVER_TOOL, "call-" + i, "{}"));
    }
    List<ChatRequest> requests = new ArrayList<>();
    ChatModel model = scriptedModel(requests, responses.toArray(new AiMessage[0]));

    JSONObject response = engine(tool, model, context()).execute(false, null, requestWithTools(), app());

    assertEquals(limit, toolExecutions.get());
    assertEquals("The server tool round limit was reached. Please ask the assistant to continue.",
        response.getJSONObject(RestServiceUtil.PROP_ANSWER).getString(RestServiceUtil.PROP_RESPONSE));
    assertEquals(limit + 1, requests.size());
  }

  @Test
  public void mixedBatchCarriesServerResultsInTheClientToolEnvelope() throws Exception {
    AtomicInteger serverCalls = new AtomicInteger();
    ServerTool tool = tool(SERVER_TOOL, (arguments, context) -> {
      serverCalls.incrementAndGet();
      return "server-result";
    });
    AiMessage mixedRequest = AiMessage.from(Arrays.asList(
        ToolExecutionRequest.builder().id("server-call").name(SERVER_TOOL).arguments("{}").build(),
        ToolExecutionRequest.builder().id("client-call").name(CLIENT_TOOL).arguments("{}").build()));
    ChatModel model = scriptedModel(new ArrayList<>(), mixedRequest);
    JSONObject request = requestWithTools().put(ClientToolSupport.PROP_CLIENT_TOOLS,
        new JSONArray().put(new JSONObject().put(ClientToolSupport.PROP_NAME, CLIENT_TOOL)));

    JSONObject response = engine(tool, model, context()).execute(false, null, request, app());

    JSONObject answer = response.getJSONObject(RestServiceUtil.PROP_ANSWER);
    assertEquals(2, answer.getJSONArray(ClientToolSupport.PROP_TOOL_CALLS).length());
    assertEquals(1, answer.getJSONArray(ClientToolSupport.PROP_SERVER_RESULTS).length());
    JSONObject serverResult = answer.getJSONArray(ClientToolSupport.PROP_SERVER_RESULTS).getJSONObject(0);
    assertEquals("server-call", serverResult.getString(ClientToolSupport.PROP_ID));
    assertEquals("server-result", serverResult.getString(ClientToolSupport.PROP_RESULT));
    assertEquals(1, serverCalls.get());
    JSONArray echoedResults = new JSONArray().put(serverResult)
        .put(new JSONObject().put(ClientToolSupport.PROP_ID, "client-call")
            .put(ClientToolSupport.PROP_NAME, CLIENT_TOOL).put(ClientToolSupport.PROP_RESULT, "client-result"));
    JSONObject echoedRound = new JSONObject().put(ClientToolSupport.PROP_CALLS,
        answer.getJSONArray(ClientToolSupport.PROP_TOOL_CALLS)).put(ClientToolSupport.PROP_RESULTS, echoedResults);
    List<ChatMessage> replayedExchange = new ArrayList<>();
    ClientToolSupport.appendExchange(replayedExchange,
        new JSONObject().put(ClientToolSupport.PROP_TOOL_EXCHANGE, new JSONArray().put(echoedRound)));
    assertEquals(3, replayedExchange.size());
    assertEquals("server-result", ((ToolExecutionResultMessage) replayedExchange.get(1)).text());
    assertEquals("client-result", ((ToolExecutionResultMessage) replayedExchange.get(2)).text());
  }

  private LangChain4jEngine engine(ServerTool tool, ChatModel model, ToolExecutionContext context) {
    ServerToolRegistry registry = new ServerToolRegistry(name -> SERVER_TOOL.equals(name) ? tool : null);
    return new LangChain4jEngine(registry, request -> model, (app, conversationId, extraInfo) -> context);
  }

  private ToolExecutionContext context() {
    return context(true);
  }

  private ToolExecutionContext context(boolean webServiceEnabled) {
    ToolExecutionContext context = mock(ToolExecutionContext.class);
    when(context.webServiceEnabled()).thenReturn(webServiceEnabled);
    try {
      when(context.call(any(Duration.class), any(Callable.class))).thenAnswer(invocation -> {
        Callable<?> body = invocation.getArgument(1);
        return body.call();
      });
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    return context;
  }

  private CopilotApp app() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getName()).thenReturn("Test agent");
    return app;
  }

  private JSONObject requestWithTools() throws JSONException {
    JSONObject descriptor = new JSONObject().put("type", "function")
        .put("function", new JSONObject().put(ClientToolSupport.PROP_NAME, SERVER_TOOL));
    return new JSONObject().put(RestServiceUtil.PROP_QUESTION, "run it")
        .put(RestServiceUtil.PROP_CONVERSATION_ID, "conversation-1")
        .put(RestServiceUtil.PROP_TOOLS, new JSONArray().put(descriptor));
  }

  private ServerTool tool(String name, ToolAction action) {
    return tool(name, false, action);
  }

  private ServerTool tool(String name, boolean needsWebService, ToolAction action) {
    return new ServerTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public ToolSpecification specification() {
        return ToolSpecification.builder().name(name).description("Test tool").build();
      }

      @Override
      public String execute(JSONObject arguments, ToolExecutionContext context) throws Exception {
        return action.execute(arguments, context);
      }

      @Override
      public boolean needsWebService() {
        return needsWebService;
      }
    };
  }

  private AiMessage request(String name, String id, String arguments) {
    return AiMessage.from(Collections.singletonList(ToolExecutionRequest.builder()
        .id(id).name(name).arguments(arguments).build()));
  }

  private ChatModel scriptedModel(List<ChatRequest> seenRequests, AiMessage... responses) {
    ChatModel model = mock(ChatModel.class);
    AtomicInteger index = new AtomicInteger();
    try {
      when(model.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
        seenRequests.add(invocation.getArgument(0));
        ChatResponse response = mock(ChatResponse.class);
        int responseIndex = Math.min(index.getAndIncrement(), responses.length - 1);
        when(response.aiMessage()).thenReturn(responses[responseIndex]);
        return response;
      });
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    return model;
  }

  @FunctionalInterface
  private interface ToolAction {
    String execute(JSONObject arguments, ToolExecutionContext context) throws Exception;
  }
}
