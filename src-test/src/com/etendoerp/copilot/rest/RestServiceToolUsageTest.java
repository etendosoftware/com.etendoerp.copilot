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

  private static final String TOOL_NAME = "tool_name";
  private static final String OCR_TOOL = "SimpleOcrTool";

  private final List<Object[]> received = new ArrayList<>();
  private final RestService.ToolUsageSink sink = (tool, agent, usage, ms) ->
      received.add(new Object[] { tool, agent, usage, ms });

  /** The usage is handed to the hooks and stripped from the answer sent to the browser. */
  @Test
  public void theUsageIsRecordedAndNeverReachesTheCaller() throws Exception {
    String body = "{\"answer\":\"{\\\"total\\\":10}\",\"usage\":{\"model\":\"gemini/m\","
        + "\"inputTokens\":3,\"outputTokens\":12,\"costUsd\":4.7E-5}}";
    JSONObject request = new JSONObject().put(TOOL_NAME, OCR_TOOL).put("agent_id", "A1");

    String answer = RestService.takeToolUsage(body, request, 820L, sink);

    JSONObject sent = new JSONObject(answer);
    assertFalse(sent.has("usage"));
    assertEquals("{\"total\":10}", sent.getString("answer"));
    assertEquals(1, received.size());
    assertEquals(OCR_TOOL, received.get(0)[0]);
    assertEquals("A1", received.get(0)[1]);
    assertEquals(4.7E-5, ((JSONObject) received.get(0)[2]).getDouble("costUsd"), 0);
    assertEquals(820L, received.get(0)[3]);
  }

  /** An answer without usage, or one that is not JSON, is sent as is and records nothing. */
  @Test
  public void anAnswerWithoutUsageIsSentUntouchedAndRecordsNothing() throws Exception {
    JSONObject request = new JSONObject().put(TOOL_NAME, "OtherTool");
    String plain = "{\"answer\":\"x\"}";
    String notJson = "Internal error";

    assertEquals(plain, RestService.takeToolUsage(plain, request, 1L, sink));
    assertEquals(notJson, RestService.takeToolUsage(notJson, request, 1L, sink));
    assertEquals(0, received.size());
  }

  /** A hook that throws never changes what the caller receives. */
  @Test
  public void aFailingHookNeverChangesTheAnswer() throws Exception {
    JSONObject request = new JSONObject().put(TOOL_NAME, OCR_TOOL);
    String body = "{\"answer\":\"x\",\"usage\":{\"costUsd\":0.1}}";

    String answer = RestService.takeToolUsage(body, request, 1L, (tool, agent, usage, ms) -> {
      throw new IllegalStateException("database down");
    });

    assertEquals("x", new JSONObject(answer).getString("answer"));
    assertNull(new JSONObject(answer).optJSONObject("usage"));
  }
}
