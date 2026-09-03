/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.rest.RestServiceUtil;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * Unit tests for {@link ClientToolSupport}, the translation layer between what the browser reports
 * through WebMCP and what LangChain4j expects.
 */
public class ClientToolSupportTest {

  private static final String LIST_WINDOWS = "etendo_list_windows";
  private static final String SCHEMA = "{\"type\":\"object\","
      + "\"properties\":{\"query\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}},"
      + "\"required\":[\"query\"]}";

  /**
   * WebMCP hands {@code inputSchema} over as a JSON string rather than an object. Reading it as an
   * object would silently produce a tool with no parameters, so the string form must be parsed.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void inputSchemaAsStringIsParsed() throws JSONException {
    JSONObject request = requestWithTool(SCHEMA);

    List<ToolSpecification> specifications = ClientToolSupport.toolSpecifications(request);

    assertEquals(1, specifications.size());
    ToolSpecification specification = specifications.get(0);
    assertEquals(LIST_WINDOWS, specification.name());
    assertNotNull(specification.parameters());
    assertEquals(2, specification.parameters().properties().size());
    assertTrue(specification.parameters().required().contains("query"));
  }

  /**
   * The object form must work too, so the contract does not depend on the browser's encoding.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void inputSchemaAsObjectIsParsed() throws JSONException {
    JSONObject request = requestWithTool(new JSONObject(SCHEMA));

    List<ToolSpecification> specifications = ClientToolSupport.toolSpecifications(request);

    assertEquals(1, specifications.size());
    assertEquals(2, specifications.get(0).parameters().properties().size());
  }

  /**
   * A tool that takes no arguments is legitimate and must not be dropped.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void toolWithoutPropertiesIsStillOffered() throws JSONException {
    JSONObject request = requestWithTool("{\"type\":\"object\",\"properties\":{}}");

    List<ToolSpecification> specifications = ClientToolSupport.toolSpecifications(request);

    assertEquals(1, specifications.size());
    assertNull(specifications.get(0).parameters());
  }

  /**
   * A descriptor without a name cannot be offered to the model; it must be skipped, not crash.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void namelessDescriptorIsSkipped() throws JSONException {
    JSONObject request = new JSONObject();
    request.put(ClientToolSupport.PROP_CLIENT_TOOLS,
        new JSONArray().put(new JSONObject().put(ClientToolSupport.PROP_DESCRIPTION, "no name")));

    assertTrue(ClientToolSupport.toolSpecifications(request).isEmpty());
  }

  /**
   * No client tools at all is the ordinary chat case.
   */
  @Test
  public void noClientToolsYieldsNoSpecifications() {
    assertTrue(ClientToolSupport.toolSpecifications(new JSONObject()).isEmpty());
  }

  /**
   * The tool-call envelope must be recognised both bare and wrapped in {@code answer}, because the
   * streaming and synchronous paths return different shapes.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void toolCallAnswerIsRecognisedBareAndWrapped() throws JSONException {
    JSONObject answer = new JSONObject().put(ClientToolSupport.PROP_TOOL_CALLS,
        new JSONArray().put(new JSONObject().put(ClientToolSupport.PROP_NAME, LIST_WINDOWS)));

    assertTrue(ClientToolSupport.isToolCallAnswer(answer));
    assertTrue(ClientToolSupport.isToolCallAnswer(
        new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer)));
  }

  /**
   * A plain textual answer must not be mistaken for a tool call, or it would never be tracked.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void plainAnswerIsNotAToolCall() throws JSONException {
    JSONObject answer = new JSONObject().put(RestServiceUtil.PROP_RESPONSE, "hello");

    assertFalse(ClientToolSupport.isToolCallAnswer(answer));
    assertFalse(ClientToolSupport.isToolCallAnswer(
        new JSONObject().put(RestServiceUtil.PROP_ANSWER, answer)));
    assertFalse(ClientToolSupport.isToolCallAnswer(null));
  }

  /**
   * One exchanged round becomes the assistant message that asked for the tool followed by the
   * result message the model needs in order to continue.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void exchangeBecomesAssistantAndResultMessages() throws JSONException {
    JSONObject request = new JSONObject().put(ClientToolSupport.PROP_TOOL_EXCHANGE,
        new JSONArray().put(round("call_1", "{\"query\":\"sales\"}", "call_1", "[{\"windowId\":\"143\"}]")));
    List<ChatMessage> messages = new ArrayList<>();

    ClientToolSupport.appendExchange(messages, request);

    assertEquals(2, messages.size());
    assertTrue(messages.get(0) instanceof AiMessage);
    assertTrue(((AiMessage) messages.get(0)).hasToolExecutionRequests());
    assertTrue(messages.get(1) instanceof ToolExecutionResultMessage);
    assertEquals("[{\"windowId\":\"143\"}]", ((ToolExecutionResultMessage) messages.get(1)).text());
  }

  /**
   * Every call the model made must be answered, otherwise the provider rejects the conversation.
   * A missing client result is replaced by an explicit placeholder rather than dropped.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void missingResultStillProducesAResultMessage() throws JSONException {
    JSONObject request = new JSONObject().put(ClientToolSupport.PROP_TOOL_EXCHANGE,
        new JSONArray().put(round("call_1", "{}", "another_call", "ignored")));
    List<ChatMessage> messages = new ArrayList<>();

    ClientToolSupport.appendExchange(messages, request);

    assertEquals(2, messages.size());
    assertTrue(messages.get(1) instanceof ToolExecutionResultMessage);
  }

  /**
   * The round cap is the backstop against a model that keeps calling tools forever.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void tooManyRoundsIsRejected() throws JSONException {
    JSONArray exchange = new JSONArray();
    for (int i = 0; i <= ClientToolSupport.MAX_TOOL_ROUNDS; i++) {
      exchange.put(round("call_" + i, "{}", "call_" + i, "ok"));
    }
    JSONObject request = new JSONObject().put(ClientToolSupport.PROP_TOOL_EXCHANGE, exchange);

    try {
      ClientToolSupport.appendExchange(new ArrayList<>(), request);
      fail("Expected the round limit to be enforced");
    } catch (OBException e) {
      assertTrue(e.getMessage().contains(String.valueOf(ClientToolSupport.MAX_TOOL_ROUNDS)));
    }
  }

  /**
   * Nothing exchanged yet must leave the message list untouched.
   */
  @Test
  public void emptyExchangeAddsNothing() {
    List<ChatMessage> messages = new ArrayList<>();

    ClientToolSupport.appendExchange(messages, new JSONObject());

    assertTrue(messages.isEmpty());
  }

  /**
   * Only the client-tool fields travel from the HTTP body into the agent payload.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void onlyClientToolFieldsAreCopied() throws JSONException {
    JSONObject clientRequest = new JSONObject();
    clientRequest.put(ClientToolSupport.PROP_CLIENT_TOOLS, new JSONArray());
    clientRequest.put(ClientToolSupport.PROP_TOOL_EXCHANGE, new JSONArray());
    clientRequest.put("something_else", "must not travel");
    JSONObject agentRequest = new JSONObject();

    ClientToolSupport.copyClientToolFields(clientRequest, agentRequest);

    assertTrue(agentRequest.has(ClientToolSupport.PROP_CLIENT_TOOLS));
    assertTrue(agentRequest.has(ClientToolSupport.PROP_TOOL_EXCHANGE));
    assertFalse(agentRequest.has("something_else"));
  }

  /**
   * A request with no client body at all must be left alone.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void nullClientRequestIsIgnored() throws JSONException {
    JSONObject agentRequest = new JSONObject();

    ClientToolSupport.copyClientToolFields(null, agentRequest);

    assertEquals(0, agentRequest.length());
  }

  /**
   * Build a payload offering a single tool with the given schema.
   *
   * @param schema
   *     the {@code inputSchema} value, as a string or as a {@link JSONObject}
   * @return the payload
   * @throws JSONException
   *     when the payload cannot be built
   */
  private JSONObject requestWithTool(Object schema) throws JSONException {
    JSONObject tool = new JSONObject();
    tool.put(ClientToolSupport.PROP_NAME, LIST_WINDOWS);
    tool.put(ClientToolSupport.PROP_DESCRIPTION, "List the windows the user can open");
    tool.put(ClientToolSupport.PROP_INPUT_SCHEMA, schema);
    return new JSONObject().put(ClientToolSupport.PROP_CLIENT_TOOLS, new JSONArray().put(tool));
  }

  /**
   * Build one exchanged round.
   *
   * @param callId
   *     the id of the call the model made
   * @param arguments
   *     the arguments the model passed
   * @param resultId
   *     the id the client reported the result under
   * @param result
   *     the result text
   * @return the round
   * @throws JSONException
   *     when the round cannot be built
   */
  private JSONObject round(String callId, String arguments, String resultId, String result)
      throws JSONException {
    JSONObject call = new JSONObject();
    call.put(ClientToolSupport.PROP_ID, callId);
    call.put(ClientToolSupport.PROP_NAME, LIST_WINDOWS);
    call.put(ClientToolSupport.PROP_ARGUMENTS, arguments);
    JSONObject reported = new JSONObject();
    reported.put(ClientToolSupport.PROP_ID, resultId);
    reported.put(ClientToolSupport.PROP_NAME, LIST_WINDOWS);
    reported.put(ClientToolSupport.PROP_RESULT, result);
    JSONObject step = new JSONObject();
    step.put(ClientToolSupport.PROP_CALLS, new JSONArray().put(call));
    step.put(ClientToolSupport.PROP_RESULTS, new JSONArray().put(reported));
    return step;
  }
}
