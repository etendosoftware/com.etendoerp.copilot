package com.etendoerp.copilot.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

/**
 * {@code /executeTool} hands the usage a tool reported (what its model call cost) to the usage hooks
 * and answers the caller without it.
 */
public class RestServiceToolUsageTest {

  private final List<Object[]> received = new ArrayList<>();
  private final RestService.ToolUsageSink sink = (tool, agent, usage, ms) ->
      received.add(new Object[] { tool, agent, usage, ms });

  @Test
  public void theUsageIsRecordedAndNeverReachesTheCaller() throws Exception {
    String body = "{\"answer\":\"{\\\"total\\\":10}\",\"usage\":{\"model\":\"gemini/m\","
        + "\"inputTokens\":3,\"outputTokens\":12,\"costUsd\":4.7E-5}}";
    JSONObject request = new JSONObject().put("tool_name", "SimpleOcrTool").put("agent_id", "A1");

    String answer = RestService.takeToolUsage(body, request, 820L, sink);

    JSONObject sent = new JSONObject(answer);
    assertFalse(sent.has("usage"));
    assertEquals("{\"total\":10}", sent.getString("answer"));
    assertEquals(1, received.size());
    assertEquals("SimpleOcrTool", received.get(0)[0]);
    assertEquals("A1", received.get(0)[1]);
    assertEquals(4.7E-5, ((JSONObject) received.get(0)[2]).getDouble("costUsd"), 0);
    assertEquals(820L, received.get(0)[3]);
  }

  @Test
  public void anAnswerWithoutUsageIsSentUntouchedAndRecordsNothing() throws Exception {
    JSONObject request = new JSONObject().put("tool_name", "OtherTool");
    String plain = "{\"answer\":\"x\"}";
    String notJson = "Internal error";

    assertEquals(plain, RestService.takeToolUsage(plain, request, 1L, sink));
    assertEquals(notJson, RestService.takeToolUsage(notJson, request, 1L, sink));
    assertEquals(0, received.size());
  }

  @Test
  public void aFailingHookNeverChangesTheAnswer() throws Exception {
    JSONObject request = new JSONObject().put("tool_name", "SimpleOcrTool");
    String body = "{\"answer\":\"x\",\"usage\":{\"costUsd\":0.1}}";

    String answer = RestService.takeToolUsage(body, request, 1L, (tool, agent, usage, ms) -> {
      throw new IllegalStateException("database down");
    });

    assertEquals("x", new JSONObject(answer).getString("answer"));
    assertNull(new JSONObject(answer).optJSONObject("usage"));
  }
}
