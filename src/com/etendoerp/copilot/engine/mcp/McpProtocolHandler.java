/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.mcp;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/** Stateless JSON-RPC 2.0 core for the synchronous Streamable HTTP endpoint. */
public final class McpProtocolHandler {
  public static final String PROTOCOL_VERSION = "2025-03-26";
  public static final int MAX_REQUEST_BYTES = 1024 * 1024;
  private static final String JSONRPC = "jsonrpc";
  private static final String ID = "id";
  private static final Set<String> SUPPORTED_VERSIONS = new HashSet<>(Arrays.asList(
      "2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25"));
  private final ToolProvider tools;

  public McpProtocolHandler(ToolProvider tools) {
    this.tools = tools;
  }

  public static boolean supportsProtocolVersion(String version) {
    return SUPPORTED_VERSIONS.contains(version);
  }

  public String responseVersion(JSONObject request) {
    JSONObject params = request == null ? null : request.optJSONObject("params");
    String requested = params == null ? null : params.optString("protocolVersion", null);
    return supportsProtocolVersion(requested) ? requested : PROTOCOL_VERSION;
  }

  /** Return null for notifications, otherwise a JSON-RPC response object. */
  public JSONObject handle(JSONObject request) throws JSONException {
    if (request == null || !"2.0".equals(request.optString(JSONRPC))) {
      return error(request == null ? null : request.opt(ID), -32600, "Invalid Request");
    }
    Object id = request.opt(ID);
    boolean notification = id == null || id == JSONObject.NULL;
    String method = request.optString("method", "");
    JSONObject params = request.optJSONObject("params");
    switch (method) {
      case "initialize":
        return notification ? null : success(id, new JSONObject()
            .put("protocolVersion", responseVersion(request))
            .put("capabilities", new JSONObject().put("tools", new JSONObject().put("listChanged", false)))
            .put("serverInfo", new JSONObject().put("name", "etendo-copilot").put("version", "1.0.0")));
      case "notifications/initialized":
        return null;
      case "ping":
        return notification ? null : success(id, new JSONObject());
      case "tools/list":
        return notification ? null : success(id, new JSONObject().put("tools", tools.list()));
      case "tools/call":
        if (notification) {
          return null;
        }
        if (params == null || StringUtils.isBlank(params.optString("name"))) {
          return error(id, -32602, "Invalid params");
        }
        String name = params.getString("name");
        JSONObject arguments = params.optJSONObject("arguments");
        if (arguments == null) {
          arguments = new JSONObject();
        }
        ToolResult result = tools.call(name, arguments);
        if (result == null) {
          return error(id, -32602, "Unknown tool");
        }
        return success(id, new JSONObject().put("content", new JSONArray()
            .put(new JSONObject().put("type", "text").put("text", result.text)))
            .put("isError", result.error));
      default:
        return notification ? null : error(id, -32601, "Method not found");
    }
  }

  private JSONObject success(Object id, JSONObject result) throws JSONException {
    return new JSONObject().put(JSONRPC, "2.0").put(ID, id).put("result", result);
  }

  private JSONObject error(Object id, int code, String message) throws JSONException {
    return new JSONObject().put(JSONRPC, "2.0").put(ID, id == null ? JSONObject.NULL : id)
        .put("error", new JSONObject().put("code", code).put("message", message));
  }

  public interface ToolProvider {
    JSONArray list() throws JSONException;

    ToolResult call(String name, JSONObject arguments);
  }

  public static final class ToolResult {
    private final String text;
    private final boolean error;

    public ToolResult(String text, boolean error) {
      this.text = StringUtils.defaultString(text);
      this.error = error;
    }

    public String text() {
      return text;
    }

    public boolean error() {
      return error;
    }
  }
}
