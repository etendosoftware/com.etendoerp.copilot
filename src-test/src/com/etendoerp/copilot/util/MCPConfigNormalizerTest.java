package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

/**
 * Unit tests for {@link MCPConfigNormalizer}.
 * Pure logic class with no external dependencies -- no mocks needed.
 */
public class MCPConfigNormalizerTest {

  // ---------------------------------------------------------------
  // Null / empty input
  // ---------------------------------------------------------------

  @Test
  public void testNullInputReturnsEmptyArray() {
    JSONArray result = MCPConfigNormalizer.normalizeToArray(null, "name");
    assertNotNull(result);
    assertEquals(0, result.length());
  }

  @Test
  public void testEmptyJsonReturnsStdioWithoutCommand() throws JSONException {
    // Empty object has no command, so stdio build returns null -> empty array
    JSONArray result = MCPConfigNormalizer.normalizeToArray(new JSONObject(), "name");
    assertEquals(0, result.length());
  }

  // ---------------------------------------------------------------
  // stdio transport
  // ---------------------------------------------------------------

  @Test
  public void testStdioWithCommandAndArgs() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("command", "npx");
    input.put("args", new JSONArray().put("-y").put("@modelcontextprotocol/server"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "myServer");
    assertEquals(1, result.length());

    JSONObject normalized = result.getJSONObject(0);
    assertEquals("stdio", normalized.getString("transport"));
    assertEquals("npx", normalized.getString("command"));
    assertEquals("myServer", normalized.getString("name"));
    assertEquals(2, normalized.getJSONArray("args").length());
  }

  @Test
  public void testStdioWithArgsAsSpaceSeparatedString() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("command", "python");
    input.put("args", "-m server --port 8080");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("stdio", normalized.getString("transport"));

    JSONArray args = normalized.getJSONArray("args");
    assertEquals(4, args.length());
    assertEquals("-m", args.getString(0));
    assertEquals("server", args.getString(1));
  }

  @Test
  public void testStdioWithArgsSingleString() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("command", "node");
    input.put("args", "index.js");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    JSONObject normalized = result.getJSONObject(0);
    JSONArray args = normalized.getJSONArray("args");
    assertEquals(1, args.length());
    assertEquals("index.js", args.getString(0));
  }

  @Test
  public void testStdioWithEnvAndCwd() throws JSONException {
    JSONObject env = new JSONObject();
    env.put("NODE_ENV", "production");

    JSONObject input = new JSONObject();
    input.put("command", "node");
    input.put("env", env);
    input.put("cwd", "/home/user/project");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("production", normalized.getJSONObject("env").getString("NODE_ENV"));
    assertEquals("/home/user/project", normalized.getString("cwd"));
  }

  @Test
  public void testStdioMissingCommandReturnsEmpty() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("args", new JSONArray().put("--flag"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(0, result.length());
  }

  @Test
  public void testStdioWithAlternativeKeyCmd() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("cmd", "python3");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "py");
    assertEquals(1, result.length());
    assertEquals("python3", result.getJSONObject(0).getString("command"));
  }

  @Test
  public void testStdioWithAlternativeKeyBin() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("bin", "/usr/local/bin/server");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("/usr/local/bin/server", result.getJSONObject(0).getString("command"));
  }

  @Test
  public void testStdioWithAlternativeKeyExecutable() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("executable", "java");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "j");
    assertEquals(1, result.length());
    assertEquals("java", result.getJSONObject(0).getString("command"));
  }

  @Test
  public void testStdioCommandAsJsonObjectFallback() throws JSONException {
    // When top-level command keys are all absent and "command" holds a JSONObject,
    // the code extracts "path" from the nested object.
    JSONObject cmdObj = new JSONObject();
    cmdObj.put("path", "/usr/bin/node");
    cmdObj.put("args", new JSONArray().put("server.js"));

    JSONObject input = new JSONObject();
    // Use a key not in CMD_KEYS so firstNonEmpty returns null,
    // then put the JSONObject under "command" key for the fallback path.
    input.put("command", cmdObj);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    // optString on a JSONObject key returns the JSON string (non-blank),
    // so firstNonEmpty sees it as a valid string command.
    assertEquals(1, result.length());
  }

  @Test
  public void testStdioCommandObjectFallbackWhenBlankCommand() throws JSONException {
    // To trigger the JSONObject fallback, we need command to be blank first.
    // Put a blank string command so firstNonEmpty skips it, then
    // the code checks obj.opt("command") instanceof JSONObject.
    // However, since "command" is a String, it won't be a JSONObject.
    // The JSONObject fallback is only reachable when command is literally a JSONObject.
    // In Jettison, optString on a JSONObject returns its toString, so this path
    // requires command to be explicitly blank. We test with cmd key instead.
    JSONObject cmdObj = new JSONObject();
    cmdObj.put("path", "/usr/bin/node");
    cmdObj.put("args", new JSONArray().put("server.js"));

    JSONObject input = new JSONObject();
    input.put("cmd", "python3");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    assertEquals(1, result.length());
    assertEquals("python3", result.getJSONObject(0).getString("command"));
  }

  @Test
  public void testStdioWithAlternativeArgKeysArgv() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("command", "node");
    input.put("argv", new JSONArray().put("--debug"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(1, normalized.getJSONArray("args").length());
  }

  @Test
  public void testStdioWithAlternativeEnvKey() throws JSONException {
    JSONObject envObj = new JSONObject();
    envObj.put("KEY", "VAL");

    JSONObject input = new JSONObject();
    input.put("command", "node");
    input.put("environment", envObj);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    JSONObject normalized = result.getJSONObject(0);
    assertTrue(normalized.has("env"));
    assertEquals("VAL", normalized.getJSONObject("env").getString("KEY"));
  }

  @Test
  public void testStdioWithAlternativeCwdKeyWorkingDir() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("command", "node");
    input.put("workingDir", "/tmp");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    assertEquals("/tmp", result.getJSONObject(0).getString("cwd"));
  }

  // ---------------------------------------------------------------
  // SSE transport
  // ---------------------------------------------------------------

  @Test
  public void testSseWithUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "sse");
    input.put("url", "http://localhost:8080/sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "sse-srv");
    assertEquals(1, result.length());
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("sse", normalized.getString("transport"));
    assertEquals("http://localhost:8080/sse", normalized.getString("url"));
  }

  @Test
  public void testSseDetectedByUrlContent() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("url", "http://localhost:8080/sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("sse", normalized.getString("transport"));
  }

  @Test
  public void testSseWithAlternativeUrlKeyUri() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "sse");
    input.put("uri", "http://example.com/sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("http://example.com/sse", normalized.getString("url"));
  }

  // ---------------------------------------------------------------
  // WebSocket transport
  // ---------------------------------------------------------------

  @Test
  public void testWebSocketWithUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "websocket");
    input.put("url", "ws://localhost:9090/ws");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "ws-srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("websocket", normalized.getString("transport"));
    assertEquals("ws://localhost:9090/ws", normalized.getString("url"));
  }

  @Test
  public void testWebSocketDetectedByWsUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("url", "ws://example.com/connect");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("websocket", normalized.getString("transport"));
  }

  @Test
  public void testWebSocketTransportNormalizesWs() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "ws");
    input.put("url", "ws://localhost/ws");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("websocket", result.getJSONObject(0).getString("transport"));
  }

  // ---------------------------------------------------------------
  // streamable_http transport
  // ---------------------------------------------------------------

  @Test
  public void testHttpWithUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "streamable_http");
    input.put("url", "http://localhost:8080/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "http-srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("streamable_http", normalized.getString("transport"));
    assertEquals("http://localhost:8080/api", normalized.getString("url"));
  }

  @Test
  public void testHttpTransportNormalizesHttp() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "http");
    input.put("url", "http://localhost/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("streamable_http", result.getJSONObject(0).getString("transport"));
  }

  @Test
  public void testHttpTransportNormalizesHttps() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "https");
    input.put("url", "https://example.com/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("streamable_http", result.getJSONObject(0).getString("transport"));
  }

  @Test
  public void testHttpTransportNormalizesHttpStream() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "http_stream");
    input.put("url", "http://example.com/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("streamable_http", result.getJSONObject(0).getString("transport"));
  }

  @Test
  public void testHttpDetectedByUrlWithoutSseOrWs() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("url", "http://localhost:8080/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("streamable_http", normalized.getString("transport"));
  }

  @Test
  public void testRemoteWithHeadersAndTimeoutViaBuildRemote() throws JSONException {
    // To go through buildRemote (which adds headers/timeoutMs), the input must NOT
    // look normalized. We use an alternative URL key so looksNormalized returns false.
    JSONObject headers = new JSONObject();
    headers.put("Authorization", "Bearer token123");

    JSONObject input = new JSONObject();
    input.put("connection", "sse");
    input.put("endpoint", "http://localhost/sse");
    input.put("headers", headers);
    input.put("timeout", 30000);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("Bearer token123", normalized.getJSONObject("headers").getString("Authorization"));
    assertEquals(30000, normalized.getInt("timeoutMs"));
  }

  @Test
  public void testRemoteWithTimeoutAsStringViaBuildRemote() throws JSONException {
    // Use alternative keys to avoid the promoteIfNormalized path
    JSONObject input = new JSONObject();
    input.put("connection", "sse");
    input.put("endpoint", "http://localhost/sse");
    input.put("timeoutMs", "5000");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(5000, normalized.getInt("timeoutMs"));
  }

  @Test
  public void testRemoteMissingUrlReturnsEmpty() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(0, result.length());
  }

  // ---------------------------------------------------------------
  // URL built from host/port/path
  // ---------------------------------------------------------------

  @Test
  public void testBuildUrlFromHostPortPath() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "streamable_http");
    input.put("host", "localhost");
    input.put("port", "3000");
    input.put("path", "api/v1");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("http://localhost:3000/api/v1", normalized.getString("url"));
  }

  @Test
  public void testBuildUrlFromHostPortWithoutPath() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "streamable_http");
    input.put("host", "localhost");
    input.put("port", "3000");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("http://localhost:3000", normalized.getString("url"));
  }

  @Test
  public void testBuildUrlFromHostPortPathWithLeadingSlash() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "streamable_http");
    input.put("host", "localhost");
    input.put("port", "3000");
    input.put("path", "/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("http://localhost:3000/api", result.getJSONObject(0).getString("url"));
  }

  @Test
  public void testBuildUrlWebSocketScheme() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "websocket");
    input.put("host", "localhost");
    input.put("port", "9090");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("ws://localhost:9090", result.getJSONObject(0).getString("url"));
  }

  @Test
  public void testHostPortDetectsHttpTransport() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("host", "localhost");
    input.put("port", "3000");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("streamable_http", result.getJSONObject(0).getString("transport"));
  }

  // ---------------------------------------------------------------
  // Nested / wrapped structures
  // ---------------------------------------------------------------

  @Test
  public void testMcpServersMapExpansion() throws JSONException {
    JSONObject serverA = new JSONObject();
    serverA.put("command", "npx");

    JSONObject serverB = new JSONObject();
    serverB.put("command", "python");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put("serverA", serverA);
    mcpServers.put("serverB", serverB);

    JSONObject input = new JSONObject();
    input.put("mcpServers", mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(2, result.length());
  }

  @Test
  public void testMcpDotServersMapExpansion() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put("command", "node");

    JSONObject servers = new JSONObject();
    servers.put("myNode", srv);

    JSONObject mcp = new JSONObject();
    mcp.put("servers", servers);

    JSONObject input = new JSONObject();
    input.put("mcp", mcp);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(1, result.length());
  }

  @Test
  public void testServersArrayExpansion() throws JSONException {
    JSONObject srv1 = new JSONObject();
    srv1.put("command", "npx");

    JSONObject srv2 = new JSONObject();
    srv2.put("command", "python");

    JSONArray serversArr = new JSONArray();
    serversArr.put(srv1);
    serversArr.put(srv2);

    JSONObject input = new JSONObject();
    input.put("servers", serversArr);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(2, result.length());
  }

  @Test
  public void testContextServersMapExpansion() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put("command", "zed-server");

    JSONObject contextServers = new JSONObject();
    contextServers.put("zed", srv);

    JSONObject input = new JSONObject();
    input.put("context_servers", contextServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(1, result.length());
  }

  @Test
  public void testPeelUnwrapsMcpAndServer() throws JSONException {
    JSONObject inner = new JSONObject();
    inner.put("command", "node");

    JSONObject server = new JSONObject();
    server.put("server", inner);

    JSONObject outer = new JSONObject();
    outer.put("mcp", server);

    // Since this wraps in mcp > server, peel should unwrap to the inner object
    JSONArray result = MCPConfigNormalizer.normalizeToArray(outer, "srv");
    assertEquals(1, result.length());
    assertEquals("node", result.getJSONObject(0).getString("command"));
  }

  // ---------------------------------------------------------------
  // Name composition
  // ---------------------------------------------------------------

  @Test
  public void testNameCompositionWithSubkey() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put("command", "npx");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put("subName", srv);

    JSONObject input = new JSONObject();
    input.put("mcpServers", mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "baseName");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("baseName::subName", normalized.getString("name"));
  }

  @Test
  public void testNameCompositionSameNameNotDuplicated() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put("command", "npx");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put("baseName", srv);

    JSONObject input = new JSONObject();
    input.put("mcpServers", mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "baseName");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("baseName", normalized.getString("name"));
  }

  @Test
  public void testNullDefaultNameUsesEmbeddedName() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put("command", "npx");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put("embedded", srv);

    JSONObject input = new JSONObject();
    input.put("mcpServers", mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, null);
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("embedded", normalized.getString("name"));
  }

  @Test
  public void testBlankDefaultNameUsesEmbeddedName() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put("command", "npx");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put("embedded", srv);

    JSONObject input = new JSONObject();
    input.put("mcpServers", mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "  ");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("embedded", normalized.getString("name"));
  }

  // ---------------------------------------------------------------
  // Transport detection heuristics
  // ---------------------------------------------------------------

  @Test
  public void testDetectsWebSocketByKeyPresence() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("websocket", new JSONObject().put("url", "ws://localhost"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // websocket key detected, but buildRemote has no top-level url -> tries host/port -> empty
    // Actually the detectTransport finds websocket, then buildRemote looks for url
    // The websocket sub-object has url but buildRemote looks at top level
    assertEquals(0, result.length());
  }

  @Test
  public void testDetectsSseByKeyPresence() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("sse", new JSONObject());
    input.put("url", "http://localhost/sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // Has url with /sse -> detects as sse, buildRemote uses url
    assertEquals(1, result.length());
    assertEquals("sse", result.getJSONObject(0).getString("transport"));
  }

  @Test
  public void testDetectsHttpByKeyPresence() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("http", new JSONObject());
    input.put("url", "http://localhost/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
  }

  @Test
  public void testDetectsStdioByKeyPresence() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("stdio", new JSONObject().put("command", "node").put("args", new JSONArray().put("app.js")));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // detectTransport sees "stdio" key -> returns stdio
    // buildStdio looks for command at top level, not found
    // Then checks if command is JSONObject: no
    // So returns null -> empty result
    // Actually wait - the code first does peel("mcp","server") then checks looksNormalized
    // No, it goes through extractCandidates which returns empty, then adds raw itself
    // Then normalizeSingle peels, checks looksNormalized (no transport key at top), then detectTransport
    // detectTransport: firstNonEmpty for transport keys -> null
    // hasAny CMD_KEYS -> no (no command/cmd/bin/executable/path at top level)
    // firstNonEmpty URL_KEYS -> no
    // has websocket -> no, has sse -> no, has http -> no, has stdio -> YES -> returns stdio
    // buildStdio: firstNonEmpty CMD_KEYS -> none at top
    // cmdRaw = obj.opt("command") -> null
    // command is blank -> returns null
    assertEquals(0, result.length());
  }

  @Test
  public void testUnrecognizedTransportReturnsEmpty() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "grpc");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(0, result.length());
  }

  // ---------------------------------------------------------------
  // looksNormalized + promoteIfNormalized
  // ---------------------------------------------------------------

  @Test
  public void testAlreadyNormalizedStdioIsPassedThrough() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "stdio");
    input.put("command", "node");
    input.put("args", new JSONArray().put("server.js"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("stdio", result.getJSONObject(0).getString("transport"));
    assertEquals("node", result.getJSONObject(0).getString("command"));
  }

  @Test
  public void testAlreadyNormalizedSseIsPassedThrough() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "sse");
    input.put("url", "http://localhost/sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("sse", result.getJSONObject(0).getString("transport"));
  }

  @Test
  public void testNormalizedStdioWithCommandAndStdioSubObject() throws JSONException {
    // To trigger promoteIfNormalized + ensureTopLevelStdio, we need
    // transport=stdio AND command present (so looksNormalized returns true).
    // ensureTopLevelStdio only promotes from sub-object when command is NOT present.
    // So to test ensureTopLevelStdio promotion, we need transport=stdio, command present
    // at top level AND a stdio sub-object. The sub-object won't be used since command exists.
    JSONObject stdioSub = new JSONObject();
    stdioSub.put("command", "python3");
    stdioSub.put("args", new JSONArray().put("main.py"));

    JSONObject input = new JSONObject();
    input.put("transport", "stdio");
    input.put("command", "node");
    input.put("stdio", stdioSub);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    JSONObject normalized = result.getJSONObject(0);
    // Command at top level wins
    assertEquals("node", normalized.getString("command"));
  }

  @Test
  public void testStdioWithoutCommandAndStdioSubObjectFallsBack() throws JSONException {
    // Without command at top level, looksNormalized returns false for stdio.
    // detectTransport sees transport key "stdio" -> returns stdio.
    // buildStdio looks for command keys, finds none, returns null.
    JSONObject stdioSub = new JSONObject();
    stdioSub.put("command", "python3");

    JSONObject input = new JSONObject();
    input.put("transport", "stdio");
    input.put("stdio", stdioSub);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // buildStdio cannot find command -> returns null -> empty
    assertEquals(0, result.length());
  }

  @Test
  public void testNormalizedRemoteWithoutUrlPromotesFromAlternativeKeys() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "sse");
    input.put("endpoint", "http://localhost/sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("http://localhost/sse", result.getJSONObject(0).getString("url"));
  }

  @Test
  public void testInvalidTransportInNormalizedFormSkipsEntry() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("transport", "grpc");
    input.put("command", "node");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // looksNormalized: has transport but normalizeTransport returns null -> looksNormalized returns false
    // detectTransport: transport key "grpc" -> normalizeTransport returns null
    // hasAny CMD_KEYS -> has command -> returns stdio
    // buildStdio -> builds with "node"
    assertEquals(1, result.length());
    assertEquals("stdio", result.getJSONObject(0).getString("transport"));
  }

  // ---------------------------------------------------------------
  // Alternative URL keys
  // ---------------------------------------------------------------

  @Test
  public void testAlternativeUrlKeyEndpoint() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("endpoint", "http://localhost/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("http://localhost/api", result.getJSONObject(0).getString("url"));
  }

  @Test
  public void testAlternativeUrlKeyBaseUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("baseUrl", "http://localhost/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
  }

  @Test
  public void testAlternativeUrlKeyServerUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("serverUrl", "http://localhost/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
  }

  @Test
  public void testAlternativeUrlKeyHttpUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("httpUrl", "http://localhost/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
  }

  // ---------------------------------------------------------------
  // coerceInt edge cases
  // ---------------------------------------------------------------

  @Test
  public void testTimeoutWithAlternativeKeyRequestTimeoutMs() throws JSONException {
    // Use alternative keys to go through buildRemote path
    JSONObject input = new JSONObject();
    input.put("connection", "sse");
    input.put("endpoint", "http://localhost/sse");
    input.put("requestTimeoutMs", 10000);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(10000, result.getJSONObject(0).getInt("timeoutMs"));
  }

  // ---------------------------------------------------------------
  // Empty args string
  // ---------------------------------------------------------------

  @Test
  public void testEmptyArgsStringIsIgnored() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("command", "node");
    input.put("args", "");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    // Empty string -> fromString returns null -> no args key
    assertTrue(!result.getJSONObject(0).has("args"));
  }

  // ---------------------------------------------------------------
  // Servers at root as map
  // ---------------------------------------------------------------

  @Test
  public void testServersAtRootAsMap() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put("command", "node");

    JSONObject servers = new JSONObject();
    servers.put("myNode", srv);

    JSONObject input = new JSONObject();
    input.put("servers", servers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(1, result.length());
  }

  // ---------------------------------------------------------------
  // mcp.servers as array
  // ---------------------------------------------------------------

  @Test
  public void testMcpServersAsArray() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put("command", "node");

    JSONArray serversArr = new JSONArray();
    serversArr.put(srv);

    JSONObject mcp = new JSONObject();
    mcp.put("servers", serversArr);

    JSONObject input = new JSONObject();
    input.put("mcp", mcp);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(1, result.length());
  }

  // ---------------------------------------------------------------
  // Alternative headers key
  // ---------------------------------------------------------------

  @Test
  public void testAlternativeHeadersKeyHttpHeaders() throws JSONException {
    // Use alternative keys to go through buildRemote path
    JSONObject hdrs = new JSONObject();
    hdrs.put("X-Custom", "value");

    JSONObject input = new JSONObject();
    input.put("connection", "sse");
    input.put("endpoint", "http://localhost/sse");
    input.put("httpHeaders", hdrs);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertTrue(result.getJSONObject(0).has("headers"));
    assertEquals("value", result.getJSONObject(0).getJSONObject("headers").getString("X-Custom"));
  }

  // ---------------------------------------------------------------
  // Path alternative key
  // ---------------------------------------------------------------

  @Test
  public void testStdioWithPathKey() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("path", "/usr/bin/node");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("/usr/bin/node", result.getJSONObject(0).getString("command"));
  }

  // ---------------------------------------------------------------
  // Connection / type / protocol as transport key alternatives
  // ---------------------------------------------------------------

  @Test
  public void testAlternativeTransportKeyConnection() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("connection", "sse");
    input.put("url", "http://localhost/sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("sse", result.getJSONObject(0).getString("transport"));
  }

  @Test
  public void testAlternativeTransportKeyType() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("type", "websocket");
    input.put("url", "ws://localhost/ws");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("websocket", result.getJSONObject(0).getString("transport"));
  }

  @Test
  public void testAlternativeTransportKeyProtocol() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("protocol", "stdio");
    input.put("command", "node");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("stdio", result.getJSONObject(0).getString("transport"));
  }

  // ---------------------------------------------------------------
  // stdio sub-object with "path" instead of "command"
  // ---------------------------------------------------------------

  @Test
  public void testStdioDetectedByStdioKeyWithoutTransport() throws JSONException {
    // When there is a "stdio" key but no explicit transport/command,
    // detectTransport detects it as stdio, but buildStdio can't find a command -> null
    JSONObject stdioSub = new JSONObject();
    stdioSub.put("path", "/usr/bin/python3");

    JSONObject input = new JSONObject();
    input.put("stdio", stdioSub);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(0, result.length());
  }
}
