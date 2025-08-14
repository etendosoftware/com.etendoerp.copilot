package com.etendoerp.copilot.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
* Normalizes heterogeneous MCP configurations (names and structures) into a consistent schema
* Supports the Python client:
* transport ∈ { "stdio", "sse", "websocket", "streamable_http" }
* and top-level:
* - stdio: command, args[, env, cwd]
* - http/sse/ws: url[, headers, timeoutMs]
 */
public final class MCPConfigNormalizer {

  private static final Logger log = LogManager.getLogger(MCPConfigNormalizer.class);

  private MCPConfigNormalizer() {}

    /**
     * Converts a raw JSON (as saved in the window) into one or more normalized configurations.
     * If the JSON comes from multiple servers (map/array), all are returned.
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


  private static JSONObject normalizeSingle(JSONObject src) throws JSONException {
    if (src == null) return null;

    JSONObject obj = peel(src, "mcp", "server");

    if (looksNormalized(obj)) {
      String t = normalizeTransport(obj.optString("transport", null));
      if (t == null) {
        log.debug("Invalid transport. Entry is skipped.");
        return null;
      }
      obj.put("transport", t);
      ensureTopLevelForTransport(obj, t);
      return obj;
    }

    String transport = detectTransport(obj);
    if (transport == null) {
      transport = "stdio";
    }

    JSONObject out = new JSONObject();
    out.put("transport", transport);

    switch (transport) {
      case "stdio": {
        String command = null;
        JSONArray args = null;

        Object cmdRaw = obj.opt("command");
        if (cmdRaw instanceof JSONObject) {
          JSONObject c = (JSONObject) cmdRaw;
          command = StringUtils.trimToNull(c.optString("path", null));
          if (c.has("args")) args = coerceArray(c, "args", "argv");
        }
        if (StringUtils.isBlank(command)) {
          command = firstNonEmpty(obj, "command", "cmd", "bin", "executable", "path");
        }
        if (args == null) {
          args = coerceArray(obj, "args", "argv", "arguments", "cmdArgs");
        }

        if (StringUtils.isBlank(command)) {
          log.debug("Invalid stdio config: 'command' is missing. It is omitted.");
          return null;
        }

        out.put("command", command);
        if (args != null) out.put("args", args);

        JSONObject env = coerceObject(obj, "env", "environment", "envVars");
        if (env != null) out.put("env", env);

        String cwd = firstNonEmpty(obj, "cwd", "workingDir", "workdir");
        if (StringUtils.isNotBlank(cwd)) out.put("cwd", cwd);
        break;
      }

      case "streamable_http":
      case "sse":
      case "websocket": {
        String url = firstNonEmpty(obj, "url", "uri", "endpoint", "baseUrl", "serverUrl", "httpUrl");
        if (StringUtils.isBlank(url)) {
          // host/port/path → url
          String host = firstNonEmpty(obj, "host");
          String port = firstNonEmpty(obj, "port");
          String path = firstNonEmpty(obj, "path");
          if (StringUtils.isNotBlank(host) && StringUtils.isNotBlank(port)) {
            if (!StringUtils.startsWith(path, "/")) {
              path = StringUtils.isBlank(path) ? "" : ("/" + path);
            }
            String scheme = transport.equals("websocket") ? "ws" : "http";
            url = scheme + "://" + host + ":" + port + (path == null ? "" : path);
          }
        }
        if (StringUtils.isBlank(url)) {
          log.debug("Invalid config {}: 'url' is missing. Ignored.", transport);
          return null;
        }

        out.put("url", url);

        JSONObject headers = coerceObject(obj, "headers", "httpHeaders");
        if (headers != null) out.put("headers", headers);

        Integer timeout = coerceInt(obj, "timeout", "timeoutMs", "requestTimeoutMs");
        if (timeout != null) out.put("timeoutMs", timeout);

        break;
      }

      default:
        log.debug("Unrecognized transport: {}", transport);
        return null;
    }

    return out;
  }

  private static boolean looksNormalized(JSONObject obj) {
    if (!obj.has("transport")) return false;
    String t = normalizeTransport(obj.optString("transport", null));
    if (t == null) return false;

    if (t.equals("stdio")) return hasAny(obj, "command");
    return hasAny(obj, "url");
  }

  private static void ensureTopLevelForTransport(JSONObject obj, String t) throws JSONException {
    if (t.equals("stdio")) {
      if (!obj.has("command")) {
        JSONObject stdio = obj.optJSONObject("stdio");
        if (stdio != null) {
          String cmd = firstNonEmpty(stdio, "command", "path");
          if (StringUtils.isNotBlank(cmd)) obj.put("command", cmd);
          if (!obj.has("args")) {
            JSONArray a = coerceArray(stdio, "args", "argv");
            if (a != null) obj.put("args", a);
          }
        }
      }
    } else {
      if (!obj.has("url")) {
        String url = firstNonEmpty(obj,
            "url", "uri", "endpoint", "baseUrl", "serverUrl", "httpUrl");
        if (StringUtils.isNotBlank(url)) obj.put("url", url);
      }
    }
  }


  private static String detectTransport(JSONObject obj) {
    String raw = firstNonEmpty(obj, "transport", "connection", "type", "protocol");
    String t = normalizeTransport(raw);
    if (t != null) return t;

    if (hasAny(obj, "command", "cmd", "bin", "executable", "path")) return "stdio";

    String url = firstNonEmpty(obj, "url", "uri", "endpoint", "baseUrl", "serverUrl", "httpUrl");
    if (StringUtils.isNotBlank(url)) {
      String u = url.trim().toLowerCase(Locale.ROOT);
      if (u.startsWith("ws")) return "websocket";
      if (u.contains("/sse")) return "sse";
      return "streamable_http";
    }

    if (obj.has("websocket")) return "websocket";
    if (obj.has("sse")) return "sse";
    if (obj.has("http") || obj.has("streamable_http")) return "streamable_http";
    if (obj.has("stdio")) return "stdio";

    if (hasAny(obj, "host", "port")) return "streamable_http";

    return null;
  }

  private static String normalizeTransport(String t) {
    if (t == null) return null;
    String v = t.trim().toLowerCase(Locale.ROOT);
    if (v.equals("ws") || v.equals("websocket")) return "websocket";
    if (v.equals("sse")) return "sse";
    if (v.equals("http") || v.equals("https") || v.equals("streamable_http") || v.equals("http_stream")) {
      return "streamable_http";
    }
    if (v.equals("stdio")) return "stdio";
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

  private static JSONArray coerceArray(JSONObject obj, String... keys) {
    for (String k : keys) {
      Object v = obj.opt(k);
      if (v instanceof JSONArray) return (JSONArray) v;
      if (v instanceof Collection) {
        JSONArray a = new JSONArray();
        for (Object it : (Collection<?>) v) a.put(it);
        return a;
      }
      if (v instanceof String) {
        String s = ((String) v).trim();
        if (s.contains(" ")) {
          JSONArray a = new JSONArray();
          for (String p : splitArgs(s)) a.put(p);
          return a;
        } else if (!s.isEmpty()) {
          JSONArray a = new JSONArray();
          a.put(s);
          return a;
        }
      }
    }
    return null;
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
        try { return Integer.parseInt((String) v); } catch (NumberFormatException ignore) {}
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

  private static List<NamedObject> extractCandidates(JSONObject raw) {
    List<NamedObject> list = new ArrayList<>();

    JSONObject mcpServers = raw.optJSONObject("mcpServers");
    if (mcpServers != null) {
      for (Iterator<String> it = mcpServers.keys(); it.hasNext();) {
        String key = it.next();
        JSONObject obj = mcpServers.optJSONObject(key);
        if (obj != null) list.add(new NamedObject(key, obj));
      }
      return list;
    }

    JSONObject mcp = raw.optJSONObject("mcp");
    if (mcp != null) {
      JSONArray arr = mcp.optJSONArray("servers");
      if (arr != null) {
        for (int i = 0; i < arr.length(); i++) {
          JSONObject it = arr.optJSONObject(i);
          if (it != null) list.add(new NamedObject(null, it));
        }
        return list;
      }

      JSONObject map = mcp.optJSONObject("servers");
      if (map != null) {
        for (Iterator<String> it = map.keys(); it.hasNext();) {
          String key = it.next();
          JSONObject obj = map.optJSONObject(key);
          if (obj != null) list.add(new NamedObject(key, obj));
        }
        return list;
      }
    }

    JSONArray sArr = raw.optJSONArray("servers");
    if (sArr != null) {
      for (int i = 0; i < sArr.length(); i++) {
        JSONObject it = sArr.optJSONObject(i);
        if (it != null) list.add(new NamedObject(null, it));
      }
      return list;
    }
    JSONObject sMap = raw.optJSONObject("servers");
    if (sMap != null) {
      for (Iterator<String> it = sMap.keys(); it.hasNext();) {
        String key = it.next();
        JSONObject obj = sMap.optJSONObject(key);
        if (obj != null) list.add(new NamedObject(key, obj));
      }
      return list;
    }

    JSONObject zed = raw.optJSONObject("context_servers");
    if (zed != null) {
      for (Iterator<String> it = zed.keys(); it.hasNext();) {
        String key = it.next();
        JSONObject obj = zed.optJSONObject(key);
        if (obj != null) list.add(new NamedObject(key, obj));
      }
      return list;
    }

    return list;
  }

  private static class NamedObject {
    final String nameOverride;
    final JSONObject obj;
    NamedObject(String nameOverride, JSONObject obj) {
      this.nameOverride = nameOverride;
      this.obj = obj;
    }
  }
}
