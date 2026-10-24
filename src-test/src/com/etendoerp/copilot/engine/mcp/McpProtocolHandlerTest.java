/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

public class McpProtocolHandlerTest {
  private final McpProtocolHandler handler = new McpProtocolHandler(new McpProtocolHandler.ToolProvider() {
    @Override
    public JSONArray list() throws JSONException {
      return new JSONArray().put(new JSONObject().put("name", "echo")
          .put("inputSchema", new JSONObject().put("type", "object")));
    }

    @Override
    public McpProtocolHandler.ToolResult call(String name, JSONObject arguments) {
      if (!"echo".equals(name)) {
        return null;
      }
      return new McpProtocolHandler.ToolResult(arguments.optString("value"), false);
    }
  });
  @Test
  public void negotiatesLegacyProtocolVersionWithoutCreatingAStickySession() throws Exception {
    JSONObject initialize = new JSONObject(
        "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\"}}");
    JSONObject response = handler.handle(initialize);
    assertEquals("2025-11-25", response.getJSONObject("result").getString("protocolVersion"));
    assertTrue(!response.getJSONObject("result").has("sessionId"));
    assertEquals("2025-03-26", handler.responseVersion(new JSONObject(
        "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"unsupported\"}}")));
  }

  @Test
  public void initializeReturnsPinnedProtocolAndCapabilities() throws Exception {
    JSONObject response = handler.handle(new JSONObject("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"));
    assertEquals(McpProtocolHandler.PROTOCOL_VERSION, response.getJSONObject("result").getString("protocolVersion"));
    assertTrue(response.getJSONObject("result").getJSONObject("capabilities").has("tools"));
  }

  @Test
  public void listsAndCallsToolsWithJsonRpcEnvelope() throws Exception {
    JSONObject listed = handler.handle(new JSONObject("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"));
    assertEquals("echo", listed.getJSONObject("result").getJSONArray("tools").getJSONObject(0).getString("name"));
    JSONObject called = handler.handle(new JSONObject(
        "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"value\":\"ok\"}}}"));
    assertEquals("ok", called.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text"));
    assertEquals(false, called.getJSONObject("result").getBoolean("isError"));
  }

  @Test
  public void handlesNotificationsWithoutResponseAndUnknownToolsAsRpcErrors() throws Exception {
    assertNull(handler.handle(new JSONObject("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")));
    JSONObject unknown = handler.handle(new JSONObject(
        "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"missing\"}}"));
    assertEquals(-32602, unknown.getJSONObject("error").getInt("code"));
  }

  @Test
  public void rejectsUnsupportedMethodsAndInvalidVersions() throws Exception {
    JSONObject unsupported = handler.handle(new JSONObject("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"resources/list\"}"));
    assertEquals(-32601, unsupported.getJSONObject("error").getInt("code"));
    JSONObject invalid = handler.handle(new JSONObject("{\"jsonrpc\":\"1.0\",\"id\":8,\"method\":\"ping\"}"));
    assertEquals(-32600, invalid.getJSONObject("error").getInt("code"));
  }
}
