package com.etendoerp.copilot.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normalizes heterogeneous MCP configurations (names and structures) into a consistent schema.
 * Supported transports for the Python client: "stdio", "sse", "websocket", "streamable_http".
 * Normalized top-level:
 *   - stdio:      transport, command, args[, env, cwd]
 *   - http/sse/ws: transport, url[, headers, timeoutMs]
 */
public final class MCPConfigNormalizer {

  private static final Logger log = LogManager.getLogger(MCPConfigNormalizer.class);

  private MCPConfigNormalizer() {}

  // ---- Keys / constants (avoid literal duplication for Sonar) ----
  private static final String K_TRANSPORT = "transport";
  private static final String K_STDIO     = "stdio";
  private static final String K_SSE       = "sse";
  private static final String K_WS        = "websocket";
  private static final String K_HTTP      = "streamable_http";

  private static final String K_COMMAND   = "command";
  private static final String K_ARGS      = "args";
  private static final String K_URL       = "url";
  private static final String K_SERVERS   = "servers";

  private static final String[] URL_KEYS       = { K_URL, "uri", "endpoint", "baseUrl", "serverUrl", "httpUrl" };
  private static final String[] CMD_KEYS       = { K_COMMAND, "cmd", "bin", "executable", "path" };
  private static final String[] ARG_KEYS       = {"args", "argv", "arguments", "cmdArgs"};
  private static final String[] ENV_KEYS       = {"env", "environment", "envVars"};
  private static final String[] CWD_KEYS       = {"cwd", "workingDir", "workdir"};
  private static final String[] TRANSPORT_KEYS = { K_TRANSPORT, "connection", "type", "protocol" };
  private static final String SLASH = "/";


  /**
   * Converts a raw JSON (as saved in the window) into one or more
   * normalized configurations.
   *
   * <p>If the JSON contains multiple servers (map or array), expands them and returns all of them.</p>
   *
   * @param rawJson Raw JSON as saved in the window.
   * @param defaultName Base server name (from DB). If there are subkeys,
   * it is composed as "defaultName::subkey".
   * @return JSONArray with the normalized MCP configurations (can be empty).
   */
  public static JSONArray normalizeToArray(JSONObject rawJson, String defaultName) {
    JSONArray out = new JSONArray();
    if (rawJson == null) return out;

    // Expand possible nested structures (mcpServers, mcp.servers, servers, context_servers, etc.)
    List<NamedObject> items = extractCandidates(rawJson);
    if (items.isEmpty()) items.add(new NamedObject(null, rawJson));

    for (NamedObject it : items) {
      try {
        JSONObject normalized = normalizeSingle(it.obj);
        if (normalized == null) continue;

        // Final name (keeps the DB name and adds a subkey if necessary)
        String finalName = StringUtils.trimToNull(defaultName);
        String embedded = StringUtils.trimToNull(it.nameOverride);
        if (embedded != null && finalName != null && !embedded.equalsIgnoreCase(finalName)) {
          finalName = finalName + "::" + embedded;
        } else if (finalName == null) {
          finalName = embedded;
        }
        if (StringUtils.isNotBlank(finalName)) {
          normalized.put("name", finalName);
        }

        out.put(normalized);
      } catch (Exception e) {
        log.error("Could not normalize an MCP configuration.", e);
      }
    }
    return out;
  }

  // --------------------------------------------------------------
  // Core normalization
  // --------------------------------------------------------------
  private static JSONObject normalizeSingle(JSONObject src) throws JSONException {
    if (src == null) return null;

    JSONObject obj = peel(src, "mcp", "server");

    // If it's already almost normalized, just promote nested fields and return
    if (looksNormalized(obj)) {
      return promoteIfNormalized(obj); // may return null if invalid transport
    }

    String transport = detectTransport(obj);
    if (transport == null) transport = K_STDIO;

    switch (transport) {
      case K_STDIO:
        return buildStdio(obj);
      case K_HTTP:
      case K_SSE:
      case K_WS:
        return buildRemote(obj, transport);
      default:
        log.debug("Unrecognized transport: {}", transport);
        return null;
    }
  }

  private static JSONObject promoteIfNormalized(JSONObject obj) throws JSONException {
    String t = normalizeTransport(obj.optString(K_TRANSPORT, null));
    if (t == null) {
      log.debug("Invalid transport. Entry is skipped.");
      return null;
    }
    obj.put(K_TRANSPORT, t);
    ensureTopLevelForTransport(obj, t);
    return obj;
  }

  // --------------------------------------------------------------
  // Builders by transport
  // --------------------------------------------------------------
  private static JSONObject buildStdio(JSONObject obj) throws JSONException {
    String command = firstNonEmpty(obj, CMD_KEYS);
    JSONArray args = coerceArray(obj, ARG_KEYS);

    if (StringUtils.isBlank(command)) {
      Object cmdRaw = obj.opt(K_COMMAND);
      if (cmdRaw instanceof JSONObject) {
        JSONObject c = (JSONObject) cmdRaw;
        command = StringUtils.trimToNull(c.optString("path", null));
        if (args == null) args = coerceArray(c, "args", "argv");
      }
    }

    if (StringUtils.isBlank(command)) {
      log.debug("Invalid stdio config: '{}' is missing. Omitted.", K_COMMAND);
      return null;
    }

    JSONObject out = new JSONObject()
        .put(K_TRANSPORT, K_STDIO)
        .put(K_COMMAND, command);

    if (args != null) out.put(K_ARGS, args);

    JSONObject env = coerceObject(obj, ENV_KEYS);
    if (env != null) out.put("env", env);

    String cwd = firstNonEmpty(obj, CWD_KEYS);
    if (StringUtils.isNotBlank(cwd)) out.put("cwd", cwd);

    return out;
  }

  private static JSONObject buildRemote(JSONObject obj, String transport) throws JSONException {
    String url = firstNonEmpty(obj, URL_KEYS);
    if (StringUtils.isBlank(url)) url = buildUrlFromHostPortPath(obj, transport);

    if (StringUtils.isBlank(url)) {
      log.debug("Invalid config {}: '{}' is missing. Ignored.", transport, K_URL);
      return null;
    }

    JSONObject out = new JSONObject()
        .put(K_TRANSPORT, transport)
        .put(K_URL, url);

    JSONObject headers = coerceObject(obj, "headers", "httpHeaders");
    if (headers != null) out.put("headers", headers);

    Integer timeout = coerceInt(obj, "timeout", "timeoutMs", "requestTimeoutMs");
    if (timeout != null) out.put("timeoutMs", timeout);

    return out;
  }

  private static String buildUrlFromHostPortPath(JSONObject obj, String transport) {
    String host = firstNonEmpty(obj, "host");
    String port = firstNonEmpty(obj, "port");
    String path = firstNonEmpty(obj, "path");
    if (StringUtils.isBlank(host) || StringUtils.isBlank(port)) return null;

    if (StringUtils.isBlank(path)) path = "";
    else if (!path.startsWith(SLASH)) path = SLASH + path;

    String scheme = transport.equals(K_WS) ? "ws" : "http";
    return scheme + "://" + host + ":" + port + path;
  }

  // --------------------------------------------------------------
  // Heuristics & helpers
  // --------------------------------------------------------------
  private static boolean looksNormalized(JSONObject obj) {
    if (!obj.has(K_TRANSPORT)) return false;
    String t = normalizeTransport(obj.optString(K_TRANSPORT, null));
    if (t == null) return false;

    if (t.equals(K_STDIO)) return hasAny(obj, K_COMMAND);
    return hasAny(obj, K_URL);
  }

  private static void ensureTopLevelForTransport(JSONObject obj, String t) throws JSONException {
    if (K_STDIO.equals(t)) ensureTopLevelStdio(obj);
    else ensureTopLevelRemote(obj);
  }

  private static void ensureTopLevelStdio(JSONObject obj) throws JSONException {
    if (obj.has(K_COMMAND)) return;
    JSONObject stdio = obj.optJSONObject(K_STDIO);
    if (stdio == null) return;

    String cmd = firstNonEmpty(stdio, K_COMMAND, "path");
    if (StringUtils.isNotBlank(cmd)) obj.put(K_COMMAND, cmd);

    if (!obj.has(K_ARGS)) {
      JSONArray a = coerceArray(stdio, "args", "argv");
      if (a != null) obj.put(K_ARGS, a);
    }
  }

  private static void ensureTopLevelRemote(JSONObject obj) throws JSONException {
    if (obj.has(K_URL)) return;
    String url = firstNonEmpty(obj, URL_KEYS);
    if (StringUtils.isNotBlank(url)) obj.put(K_URL, url);
  }

  private static String detectTransport(JSONObject obj) {
    String raw = firstNonEmpty(obj, TRANSPORT_KEYS);
    String t = normalizeTransport(raw);
    if (t != null) return t;

    // Content-based inference
    if (hasAny(obj, CMD_KEYS)) return K_STDIO;

    String url = firstNonEmpty(obj, URL_KEYS);
    if (StringUtils.isNotBlank(url)) {
      String u = url.trim().toLowerCase(Locale.ROOT);
      if (u.startsWith("ws")) return K_WS;
      if (u.contains("/sse")) return K_SSE;
      return K_HTTP;
    }

    if (obj.has(K_WS)) return K_WS;
    if (obj.has(K_SSE)) return K_SSE;
    if (obj.has("http") || obj.has(K_HTTP)) return K_HTTP;
    if (obj.has(K_STDIO)) return K_STDIO;

    if (hasAny(obj, "host", "port")) return K_HTTP;

    return null;
  }

  private static String normalizeTransport(String t) {
    if (t == null) return null;
    String v = t.trim().toLowerCase(Locale.ROOT);
    if (v.equals("ws") || v.equals(K_WS)) return K_WS;
    if (v.equals(K_SSE)) return K_SSE;
    if (v.equals("http") || v.equals("https") || v.equals(K_HTTP) || v.equals("http_stream")) return K_HTTP;
    if (v.equals(K_STDIO)) return K_STDIO;
    return null;
  }

  private static boolean hasAny(JSONObject obj, String... keys) {
    for (String k : keys) if (obj.has(k)) return true;
    return false;
  }

  private static String firstNonEmpty(JSONObject obj, String... keys) {
    for (String k : keys) {
      String v = obj.optString(k, null);
      if (StringUtils.isNotBlank(v)) return v;
    }
    return null;
  }

  // coerceArray entrypoints (object + set of keys)
  private static JSONArray coerceArray(JSONObject obj, String... keys) {
    for (String k : keys) {
      JSONArray a = toJSONArray(obj.opt(k));
      if (a != null) return a;
    }
    return null;
  }

  private static JSONArray toJSONArray(Object v) {
    if (v instanceof JSONArray) return (JSONArray) v;
    if (v instanceof Collection) return fromCollection((Collection<?>) v);
    if (v instanceof String) return fromString((String) v);
    return null;
  }

  private static JSONArray fromCollection(Collection<?> c) {
    JSONArray a = new JSONArray();
    for (Object it : c) a.put(it);
    return a;
  }

  private static JSONArray fromString(String s) {
    String trimmed = s.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.contains(" ")) {
      JSONArray a = new JSONArray();
      for (String p : splitArgs(trimmed)) a.put(p);
      return a;
    }
    JSONArray a = new JSONArray();
    a.put(trimmed);
    return a;
  }

  private static List<String> splitArgs(String s) {
    return Arrays.stream(s.trim().split("\\s+"))
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toList());
  }

  private static JSONObject coerceObject(JSONObject obj, String... keys) {
    for (String k : keys) {
      Object v = obj.opt(k);
      if (v instanceof JSONObject) return (JSONObject) v;
      if (v instanceof Map) return new JSONObject((Map<?, ?>) v);
    }
    return null;
  }

  private static Integer coerceInt(JSONObject obj, String... keys) {
    for (String k : keys) {
      Object v = obj.opt(k);
      if (v instanceof Number) return ((Number) v).intValue();
      if (v instanceof String && StringUtils.isNumeric((String) v)) {
        try { return Integer.parseInt((String) v); } catch (NumberFormatException ignore) { /* noop */ }
      }
    }
    return null;
  }

  private static JSONObject peel(JSONObject src, String... wrappers) {
    JSONObject out = src;
    for (String w : wrappers) {
      JSONObject inner = out.optJSONObject(w);
      if (inner != null) out = inner;
    }
    return out;
  }

  // --------------------------------------------------------------
  // Candidate extraction (array/map helpers)
  // --------------------------------------------------------------
  private static List<NamedObject> extractCandidates(JSONObject raw) {
    List<NamedObject> list = new ArrayList<>();

    // VS / Cursor / Windsurf
    addChildrenFromMap(raw.optJSONObject("mcpServers"), list);

    // "mcp.servers" (array or map)
    JSONObject mcp = raw.optJSONObject("mcp");
    if (mcp != null) {
      addChildrenFromArray(mcp, K_SERVERS, list);
      addChildrenFromMap(mcp.optJSONObject(K_SERVERS), list);
    }

    // "servers" at root (array or map)
    addChildrenFromArray(raw, K_SERVERS, list);
    addChildrenFromMap(raw.optJSONObject(K_SERVERS), list);

    // Zed
    addChildrenFromMap(raw.optJSONObject("context_servers"), list);

    return list;
  }

  private static void addChildrenFromArray(JSONObject parent, String key, List<NamedObject> out) {
    if (parent == null) return;
    JSONArray arr = parent.optJSONArray(key);
    if (arr == null) return;
    for (int i = 0; i < arr.length(); i++) {
      JSONObject it = arr.optJSONObject(i);
      if (it != null) out.add(new NamedObject(null, it));
    }
  }

  private static void addChildrenFromMap(JSONObject map, List<NamedObject> out) {
    if (map == null) return;
    for (Iterator<String> it = map.keys(); it.hasNext();) {
      String key = it.next();
      JSONObject obj = map.optJSONObject(key);
      if (obj != null) out.add(new NamedObject(key, obj));
    }
  }

  // --------------------------------------------------------------
  // Small wrapper to keep sub-names when expanding maps
  // --------------------------------------------------------------
  private static class NamedObject {
    final String nameOverride;
    final JSONObject obj;
    NamedObject(String nameOverride, JSONObject obj) {
      this.nameOverride = nameOverride;
      this.obj = obj;
    }
  }
}
