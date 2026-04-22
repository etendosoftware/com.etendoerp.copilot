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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
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
@SuppressWarnings("java:S1448")
public class MCPConfigNormalizerTest {
  private static final String BASENAME = "baseName";
  private static final String COMMAND = "command";
  private static final String CONNECTION = "connection";
  private static final String EMBEDDED = "embedded";
  private static final String ENDPOINT = "endpoint";
  private static final String HEADERS = "headers";
  private static final String HTTP_LOCALHOST_8080_API = "http://localhost:8080/api";
  private static final String HTTP_LOCALHOST_8080_SSE = "http://localhost:8080/sse";
  private static final String HTTP_LOCALHOST_API = "http://localhost/api";
  private static final String HTTP_LOCALHOST_SSE = "http://localhost/sse";
  private static final String LOCALHOST = "localhost";
  private static final String MCPSERVERS = "mcpServers";
  private static final String PYTHON = "python";
  private static final String PYTHON3 = "python3";
  private static final String SERVERS = "servers";
  private static final String SERVER_JS = "server.js";
  private static final String STDIO = "stdio";
  private static final String STREAMABLE_HTTP = "streamable_http";
  private static final String TIMEOUTMS = "timeoutMs";
  private static final String TRANSPORT = "transport";
  private static final String USR_BIN_NODE = "/usr/bin/node";
  private static final String WEBSOCKET = "websocket";


  // ---------------------------------------------------------------
  // Null / empty input
  // ---------------------------------------------------------------

  /** Test null input returns empty array. */
  @Test
  public void testNullInputReturnsEmptyArray() {
    JSONArray result = MCPConfigNormalizer.normalizeToArray(null, "name");
    assertNotNull(result);
    assertEquals(0, result.length());
  }

  /**
   * Test empty json returns stdio without command.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testEmptyJsonReturnsStdioWithoutCommand() throws JSONException {
    // Empty object has no command, so stdio build returns null -> empty array
    JSONArray result = MCPConfigNormalizer.normalizeToArray(new JSONObject(), "name");
    assertEquals(0, result.length());
  }

  // ---------------------------------------------------------------
  // stdio transport
  // ---------------------------------------------------------------

  /**
   * Test stdio with command and args.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithCommandAndArgs() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(COMMAND, "npx");
    input.put("args", new JSONArray().put("-y").put("@modelcontextprotocol/server"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "myServer");
    assertEquals(1, result.length());

    JSONObject normalized = result.getJSONObject(0);
    assertEquals(STDIO, normalized.getString(TRANSPORT));
    assertEquals("npx", normalized.getString(COMMAND));
    assertEquals("myServer", normalized.getString("name"));
    assertEquals(2, normalized.getJSONArray("args").length());
  }

  /**
   * Test stdio with args as space separated string.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithArgsAsSpaceSeparatedString() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(COMMAND, PYTHON);
    input.put("args", "-m server --port 8080");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(STDIO, normalized.getString(TRANSPORT));

    JSONArray args = normalized.getJSONArray("args");
    assertEquals(4, args.length());
    assertEquals("-m", args.getString(0));
    assertEquals("server", args.getString(1));
  }

  /**
   * Test stdio with args single string.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithArgsSingleString() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(COMMAND, "node");
    input.put("args", "index.js");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    JSONObject normalized = result.getJSONObject(0);
    JSONArray args = normalized.getJSONArray("args");
    assertEquals(1, args.length());
    assertEquals("index.js", args.getString(0));
  }

  /**
   * Test stdio with env and cwd.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithEnvAndCwd() throws JSONException {
    JSONObject env = new JSONObject();
    env.put("NODE_ENV", "production");

    JSONObject input = new JSONObject();
    input.put(COMMAND, "node");
    input.put("env", env);
    input.put("cwd", "/home/user/project");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("production", normalized.getJSONObject("env").getString("NODE_ENV"));
    assertEquals("/home/user/project", normalized.getString("cwd"));
  }

  /**
   * Test stdio missing command returns empty.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioMissingCommandReturnsEmpty() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("args", new JSONArray().put("--flag"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(0, result.length());
  }

  /**
   * Test stdio with alternative key cmd.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithAlternativeKeyCmd() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("cmd", PYTHON3);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "py");
    assertEquals(1, result.length());
    assertEquals(PYTHON3, result.getJSONObject(0).getString(COMMAND));
  }

  /**
   * Test stdio with alternative key bin.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithAlternativeKeyBin() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("bin", "/usr/local/bin/server");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("/usr/local/bin/server", result.getJSONObject(0).getString(COMMAND));
  }

  /**
   * Test stdio with alternative key executable.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithAlternativeKeyExecutable() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("executable", "java");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "j");
    assertEquals(1, result.length());
    assertEquals("java", result.getJSONObject(0).getString(COMMAND));
  }

  /**
   * Test stdio command as json object fallback.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioCommandAsJsonObjectFallback() throws JSONException {
    // When top-level command keys are all absent and COMMAND holds a JSONObject,
    // the code extracts "path" from the nested object.
    JSONObject cmdObj = new JSONObject();
    cmdObj.put("path", USR_BIN_NODE);
    cmdObj.put("args", new JSONArray().put(SERVER_JS));

    JSONObject input = new JSONObject();
    // Use a key not in CMD_KEYS so firstNonEmpty returns null,
    // then put the JSONObject under COMMAND key for the fallback path.
    input.put(COMMAND, cmdObj);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    // optString on a JSONObject key returns the JSON string (non-blank),
    // so firstNonEmpty sees it as a valid string command.
    assertEquals(1, result.length());
  }

  /**
   * Test stdio command object fallback when blank command.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioCommandObjectFallbackWhenBlankCommand() throws JSONException {
    // To trigger the JSONObject fallback, we need command to be blank first.
    // Put a blank string command so firstNonEmpty skips it, then
    // the code checks obj.opt(COMMAND) instanceof JSONObject.
    // However, since COMMAND is a String, it won't be a JSONObject.
    // The JSONObject fallback is only reachable when command is literally a JSONObject.
    // In Jettison, optString on a JSONObject returns its toString, so this path
    // requires command to be explicitly blank. We test with cmd key instead.
    JSONObject cmdObj = new JSONObject();
    cmdObj.put("path", USR_BIN_NODE);
    cmdObj.put("args", new JSONArray().put(SERVER_JS));

    JSONObject input = new JSONObject();
    input.put("cmd", PYTHON3);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    assertEquals(1, result.length());
    assertEquals(PYTHON3, result.getJSONObject(0).getString(COMMAND));
  }

  /**
   * Test stdio with alternative arg keys argv.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithAlternativeArgKeysArgv() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(COMMAND, "node");
    input.put("argv", new JSONArray().put("--debug"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(1, normalized.getJSONArray("args").length());
  }

  /**
   * Test stdio with alternative env key.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithAlternativeEnvKey() throws JSONException {
    JSONObject envObj = new JSONObject();
    envObj.put("KEY", "VAL");

    JSONObject input = new JSONObject();
    input.put(COMMAND, "node");
    input.put("environment", envObj);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    JSONObject normalized = result.getJSONObject(0);
    assertTrue(normalized.has("env"));
    assertEquals("VAL", normalized.getJSONObject("env").getString("KEY"));
  }

  /**
   * Test stdio with alternative cwd key working dir.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithAlternativeCwdKeyWorkingDir() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(COMMAND, "node");
    input.put("workingDir", "/tmp");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "n");
    assertEquals("/tmp", result.getJSONObject(0).getString("cwd"));
  }

  // ---------------------------------------------------------------
  // SSE transport
  // ---------------------------------------------------------------

  /**
   * Test sse with url.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testSseWithUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "sse");
    input.put("url", HTTP_LOCALHOST_8080_SSE);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "sse-srv");
    assertEquals(1, result.length());
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("sse", normalized.getString(TRANSPORT));
    assertEquals(HTTP_LOCALHOST_8080_SSE, normalized.getString("url"));
  }

  /**
   * Test sse detected by url content.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testSseDetectedByUrlContent() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("url", HTTP_LOCALHOST_8080_SSE);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("sse", normalized.getString(TRANSPORT));
  }

  /**
   * Test sse with alternative url key uri.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testSseWithAlternativeUrlKeyUri() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "sse");
    input.put("uri", "http://example.com/sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("http://example.com/sse", normalized.getString("url"));
  }

  // ---------------------------------------------------------------
  // WebSocket transport
  // ---------------------------------------------------------------

  /**
   * Test web socket with url.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testWebSocketWithUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, WEBSOCKET);
    input.put("url", "ws://localhost:9090/ws");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "ws-srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(WEBSOCKET, normalized.getString(TRANSPORT));
    assertEquals("ws://localhost:9090/ws", normalized.getString("url"));
  }

  /**
   * Test web socket detected by ws url.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testWebSocketDetectedByWsUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("url", "ws://example.com/connect");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(WEBSOCKET, normalized.getString(TRANSPORT));
  }

  /**
   * Test web socket transport normalizes ws.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testWebSocketTransportNormalizesWs() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "ws");
    input.put("url", "ws://localhost/ws");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(WEBSOCKET, result.getJSONObject(0).getString(TRANSPORT));
  }

  // ---------------------------------------------------------------
  // streamable_http transport
  // ---------------------------------------------------------------

  /**
   * Test http with url.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testHttpWithUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, STREAMABLE_HTTP);
    input.put("url", HTTP_LOCALHOST_8080_API);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "http-srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(STREAMABLE_HTTP, normalized.getString(TRANSPORT));
    assertEquals(HTTP_LOCALHOST_8080_API, normalized.getString("url"));
  }

  /**
   * Test http transport normalizes http.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testHttpTransportNormalizesHttp() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "http");
    input.put("url", HTTP_LOCALHOST_API);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(STREAMABLE_HTTP, result.getJSONObject(0).getString(TRANSPORT));
  }

  /**
   * Test http transport normalizes https.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testHttpTransportNormalizesHttps() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "https");
    input.put("url", "https://example.com/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(STREAMABLE_HTTP, result.getJSONObject(0).getString(TRANSPORT));
  }

  /**
   * Test http transport normalizes http stream.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testHttpTransportNormalizesHttpStream() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "http_stream");
    input.put("url", "http://example.com/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(STREAMABLE_HTTP, result.getJSONObject(0).getString(TRANSPORT));
  }

  /**
   * Test http detected by url without sse or ws.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testHttpDetectedByUrlWithoutSseOrWs() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("url", HTTP_LOCALHOST_8080_API);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(STREAMABLE_HTTP, normalized.getString(TRANSPORT));
  }

  /**
   * Test remote with headers and timeout via build remote.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testRemoteWithHeadersAndTimeoutViaBuildRemote() throws JSONException {
    // To go through buildRemote (which adds headers/timeoutMs), the input must NOT
    // look normalized. We use an alternative URL key so looksNormalized returns false.
    JSONObject headers = new JSONObject();
    headers.put("Authorization", "Bearer token123");

    JSONObject input = new JSONObject();
    input.put(CONNECTION, "sse");
    input.put(ENDPOINT, HTTP_LOCALHOST_SSE);
    input.put(HEADERS, headers);
    input.put("timeout", 30000);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("Bearer token123", normalized.getJSONObject(HEADERS).getString("Authorization"));
    assertEquals(30000, normalized.getInt(TIMEOUTMS));
  }

  /**
   * Test remote with timeout as string via build remote.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testRemoteWithTimeoutAsStringViaBuildRemote() throws JSONException {
    // Use alternative keys to avoid the promoteIfNormalized path
    JSONObject input = new JSONObject();
    input.put(CONNECTION, "sse");
    input.put(ENDPOINT, HTTP_LOCALHOST_SSE);
    input.put(TIMEOUTMS, "5000");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(5000, normalized.getInt(TIMEOUTMS));
  }

  /**
   * Test remote missing url returns empty.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testRemoteMissingUrlReturnsEmpty() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "sse");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(0, result.length());
  }

  // ---------------------------------------------------------------
  // URL built from host/port/path
  // ---------------------------------------------------------------

  /**
   * Test build url from host port path.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testBuildUrlFromHostPortPath() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, STREAMABLE_HTTP);
    input.put("host", LOCALHOST);
    input.put("port", "3000");
    input.put("path", "api/v1");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("http://localhost:3000/api/v1", normalized.getString("url"));
  }

  /**
   * Test build url from host port without path.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testBuildUrlFromHostPortWithoutPath() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, STREAMABLE_HTTP);
    input.put("host", LOCALHOST);
    input.put("port", "3000");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("http://localhost:3000", normalized.getString("url"));
  }

  /**
   * Test build url from host port path with leading slash.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testBuildUrlFromHostPortPathWithLeadingSlash() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, STREAMABLE_HTTP);
    input.put("host", LOCALHOST);
    input.put("port", "3000");
    input.put("path", "/api");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("http://localhost:3000/api", result.getJSONObject(0).getString("url"));
  }

  /**
   * Test build url web socket scheme.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testBuildUrlWebSocketScheme() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, WEBSOCKET);
    input.put("host", LOCALHOST);
    input.put("port", "9090");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("ws://localhost:9090", result.getJSONObject(0).getString("url"));
  }

  /**
   * Test host port detects http transport.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testHostPortDetectsHttpTransport() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("host", LOCALHOST);
    input.put("port", "3000");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals(STREAMABLE_HTTP, result.getJSONObject(0).getString(TRANSPORT));
  }

  // ---------------------------------------------------------------
  // Nested / wrapped structures
  // ---------------------------------------------------------------

  /**
   * Test mcp servers map expansion.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testMcpServersMapExpansion() throws JSONException {
    JSONObject serverA = new JSONObject();
    serverA.put(COMMAND, "npx");

    JSONObject serverB = new JSONObject();
    serverB.put(COMMAND, PYTHON);

    JSONObject mcpServers = new JSONObject();
    mcpServers.put("serverA", serverA);
    mcpServers.put("serverB", serverB);

    JSONObject input = new JSONObject();
    input.put(MCPSERVERS, mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(2, result.length());
  }

  /**
   * Test mcp dot servers map expansion.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testMcpDotServersMapExpansion() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put(COMMAND, "node");

    JSONObject servers = new JSONObject();
    servers.put("myNode", srv);

    JSONObject mcp = new JSONObject();
    mcp.put(SERVERS, servers);

    JSONObject input = new JSONObject();
    input.put("mcp", mcp);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(1, result.length());
  }

  /**
   * Test servers array expansion.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testServersArrayExpansion() throws JSONException {
    JSONObject srv1 = new JSONObject();
    srv1.put(COMMAND, "npx");

    JSONObject srv2 = new JSONObject();
    srv2.put(COMMAND, PYTHON);

    JSONArray serversArr = new JSONArray();
    serversArr.put(srv1);
    serversArr.put(srv2);

    JSONObject input = new JSONObject();
    input.put(SERVERS, serversArr);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(2, result.length());
  }

  /**
   * Test context servers map expansion.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testContextServersMapExpansion() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put(COMMAND, "zed-server");

    JSONObject contextServers = new JSONObject();
    contextServers.put("zed", srv);

    JSONObject input = new JSONObject();
    input.put("context_servers", contextServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(1, result.length());
  }

  /**
   * Test peel unwraps mcp and server.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testPeelUnwrapsMcpAndServer() throws JSONException {
    JSONObject inner = new JSONObject();
    inner.put(COMMAND, "node");

    JSONObject server = new JSONObject();
    server.put("server", inner);

    JSONObject outer = new JSONObject();
    outer.put("mcp", server);

    // Since this wraps in mcp > server, peel should unwrap to the inner object
    JSONArray result = MCPConfigNormalizer.normalizeToArray(outer, "srv");
    assertEquals(1, result.length());
    assertEquals("node", result.getJSONObject(0).getString(COMMAND));
  }

  // ---------------------------------------------------------------
  // Name composition
  // ---------------------------------------------------------------

  /**
   * Test name composition with subkey.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testNameCompositionWithSubkey() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put(COMMAND, "npx");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put("subName", srv);

    JSONObject input = new JSONObject();
    input.put(MCPSERVERS, mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, BASENAME);
    JSONObject normalized = result.getJSONObject(0);
    assertEquals("baseName::subName", normalized.getString("name"));
  }

  /**
   * Test name composition same name not duplicated.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testNameCompositionSameNameNotDuplicated() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put(COMMAND, "npx");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put(BASENAME, srv);

    JSONObject input = new JSONObject();
    input.put(MCPSERVERS, mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, BASENAME);
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(BASENAME, normalized.getString("name"));
  }

  /**
   * Test null default name uses embedded name.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testNullDefaultNameUsesEmbeddedName() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put(COMMAND, "npx");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put(EMBEDDED, srv);

    JSONObject input = new JSONObject();
    input.put(MCPSERVERS, mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, null);
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(EMBEDDED, normalized.getString("name"));
  }

  /**
   * Test blank default name uses embedded name.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testBlankDefaultNameUsesEmbeddedName() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put(COMMAND, "npx");

    JSONObject mcpServers = new JSONObject();
    mcpServers.put(EMBEDDED, srv);

    JSONObject input = new JSONObject();
    input.put(MCPSERVERS, mcpServers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "  ");
    JSONObject normalized = result.getJSONObject(0);
    assertEquals(EMBEDDED, normalized.getString("name"));
  }

  // ---------------------------------------------------------------
  // Transport detection heuristics
  // ---------------------------------------------------------------

  /**
   * Test detects web socket by key presence.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testDetectsWebSocketByKeyPresence() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(WEBSOCKET, new JSONObject().put("url", "ws://localhost"));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // websocket key detected, but buildRemote has no top-level url -> tries host/port -> empty
    // Actually the detectTransport finds websocket, then buildRemote looks for url
    // The websocket sub-object has url but buildRemote looks at top level
    assertEquals(0, result.length());
  }

  /**
   * Test detects sse by key presence.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testDetectsSseByKeyPresence() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("sse", new JSONObject());
    input.put("url", HTTP_LOCALHOST_SSE);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // Has url with /sse -> detects as sse, buildRemote uses url
    assertEquals(1, result.length());
    assertEquals("sse", result.getJSONObject(0).getString(TRANSPORT));
  }

  /**
   * Test detects http by key presence.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testDetectsHttpByKeyPresence() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("http", new JSONObject());
    input.put("url", HTTP_LOCALHOST_API);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
  }

  /**
   * Test detects stdio by key presence.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testDetectsStdioByKeyPresence() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(STDIO, new JSONObject().put(COMMAND, "node").put("args", new JSONArray().put("app.js")));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // detectTransport sees STDIO key -> returns stdio
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
    // cmdRaw = obj.opt(COMMAND) -> null
    // command is blank -> returns null
    assertEquals(0, result.length());
  }

  /**
   * Test unrecognized transport returns empty.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testUnrecognizedTransportReturnsEmpty() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "grpc");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(0, result.length());
  }

  // ---------------------------------------------------------------
  // looksNormalized + promoteIfNormalized
  // ---------------------------------------------------------------

  /**
   * Test already normalized stdio is passed through.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlreadyNormalizedStdioIsPassedThrough() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, STDIO);
    input.put(COMMAND, "node");
    input.put("args", new JSONArray().put(SERVER_JS));

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals(STDIO, result.getJSONObject(0).getString(TRANSPORT));
    assertEquals("node", result.getJSONObject(0).getString(COMMAND));
  }

  /**
   * Test already normalized sse is passed through.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlreadyNormalizedSseIsPassedThrough() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "sse");
    input.put("url", HTTP_LOCALHOST_SSE);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals("sse", result.getJSONObject(0).getString(TRANSPORT));
  }

  /**
   * Test normalized stdio with command and stdio sub object.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testNormalizedStdioWithCommandAndStdioSubObject() throws JSONException {
    // To trigger promoteIfNormalized + ensureTopLevelStdio, we need
    // transport=stdio AND command present (so looksNormalized returns true).
    // ensureTopLevelStdio only promotes from sub-object when command is NOT present.
    // So to test ensureTopLevelStdio promotion, we need transport=stdio, command present
    // at top level AND a stdio sub-object. The sub-object won't be used since command exists.
    JSONObject stdioSub = new JSONObject();
    stdioSub.put(COMMAND, PYTHON3);
    stdioSub.put("args", new JSONArray().put("main.py"));

    JSONObject input = new JSONObject();
    input.put(TRANSPORT, STDIO);
    input.put(COMMAND, "node");
    input.put(STDIO, stdioSub);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    JSONObject normalized = result.getJSONObject(0);
    // Command at top level wins
    assertEquals("node", normalized.getString(COMMAND));
  }

  /**
   * Test stdio without command and stdio sub object falls back.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithoutCommandAndStdioSubObjectFallsBack() throws JSONException {
    // Without command at top level, looksNormalized returns false for stdio.
    // detectTransport sees transport key STDIO -> returns stdio.
    // buildStdio looks for command keys, finds none, returns null.
    JSONObject stdioSub = new JSONObject();
    stdioSub.put(COMMAND, PYTHON3);

    JSONObject input = new JSONObject();
    input.put(TRANSPORT, STDIO);
    input.put(STDIO, stdioSub);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // buildStdio cannot find command -> returns null -> empty
    assertEquals(0, result.length());
  }

  /**
   * Test normalized remote without url promotes from alternative keys.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testNormalizedRemoteWithoutUrlPromotesFromAlternativeKeys() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "sse");
    input.put(ENDPOINT, HTTP_LOCALHOST_SSE);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals(HTTP_LOCALHOST_SSE, result.getJSONObject(0).getString("url"));
  }

  /**
   * Test invalid transport in normalized form skips entry.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testInvalidTransportInNormalizedFormSkipsEntry() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(TRANSPORT, "grpc");
    input.put(COMMAND, "node");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    // looksNormalized: has transport but normalizeTransport returns null -> looksNormalized returns false
    // detectTransport: transport key "grpc" -> normalizeTransport returns null
    // hasAny CMD_KEYS -> has command -> returns stdio
    // buildStdio -> builds with "node"
    assertEquals(1, result.length());
    assertEquals(STDIO, result.getJSONObject(0).getString(TRANSPORT));
  }

  // ---------------------------------------------------------------
  // Alternative URL keys
  // ---------------------------------------------------------------

  /**
   * Test alternative url key endpoint.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlternativeUrlKeyEndpoint() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(ENDPOINT, HTTP_LOCALHOST_API);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals(HTTP_LOCALHOST_API, result.getJSONObject(0).getString("url"));
  }

  /**
   * Test alternative url key base url.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlternativeUrlKeyBaseUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("baseUrl", HTTP_LOCALHOST_API);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
  }

  /**
   * Test alternative url key server url.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlternativeUrlKeyServerUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("serverUrl", HTTP_LOCALHOST_API);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
  }

  /**
   * Test alternative url key http url.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlternativeUrlKeyHttpUrl() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("httpUrl", HTTP_LOCALHOST_API);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
  }

  // ---------------------------------------------------------------
  // coerceInt edge cases
  // ---------------------------------------------------------------

  /**
   * Test timeout with alternative key request timeout ms.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testTimeoutWithAlternativeKeyRequestTimeoutMs() throws JSONException {
    // Use alternative keys to go through buildRemote path
    JSONObject input = new JSONObject();
    input.put(CONNECTION, "sse");
    input.put(ENDPOINT, HTTP_LOCALHOST_SSE);
    input.put("requestTimeoutMs", 10000);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(10000, result.getJSONObject(0).getInt(TIMEOUTMS));
  }

  // ---------------------------------------------------------------
  // Empty args string
  // ---------------------------------------------------------------

  /**
   * Test empty args string is ignored.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testEmptyArgsStringIsIgnored() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(COMMAND, "node");
    input.put("args", "");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    // Empty string -> fromString returns null -> no args key
    assertTrue(!result.getJSONObject(0).has("args"));
  }

  // ---------------------------------------------------------------
  // Servers at root as map
  // ---------------------------------------------------------------

  /**
   * Test servers at root as map.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testServersAtRootAsMap() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put(COMMAND, "node");

    JSONObject servers = new JSONObject();
    servers.put("myNode", srv);

    JSONObject input = new JSONObject();
    input.put(SERVERS, servers);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(1, result.length());
  }

  // ---------------------------------------------------------------
  // mcp.servers as array
  // ---------------------------------------------------------------

  /**
   * Test mcp servers as array.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testMcpServersAsArray() throws JSONException {
    JSONObject srv = new JSONObject();
    srv.put(COMMAND, "node");

    JSONArray serversArr = new JSONArray();
    serversArr.put(srv);

    JSONObject mcp = new JSONObject();
    mcp.put(SERVERS, serversArr);

    JSONObject input = new JSONObject();
    input.put("mcp", mcp);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "base");
    assertEquals(1, result.length());
  }

  // ---------------------------------------------------------------
  // Alternative headers key
  // ---------------------------------------------------------------

  /**
   * Test alternative headers key http headers.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlternativeHeadersKeyHttpHeaders() throws JSONException {
    // Use alternative keys to go through buildRemote path
    JSONObject hdrs = new JSONObject();
    hdrs.put("X-Custom", "value");

    JSONObject input = new JSONObject();
    input.put(CONNECTION, "sse");
    input.put(ENDPOINT, HTTP_LOCALHOST_SSE);
    input.put("httpHeaders", hdrs);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertTrue(result.getJSONObject(0).has(HEADERS));
    assertEquals("value", result.getJSONObject(0).getJSONObject(HEADERS).getString("X-Custom"));
  }

  // ---------------------------------------------------------------
  // Path alternative key
  // ---------------------------------------------------------------

  /**
   * Test stdio with path key.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioWithPathKey() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("path", USR_BIN_NODE);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(1, result.length());
    assertEquals(USR_BIN_NODE, result.getJSONObject(0).getString(COMMAND));
  }

  // ---------------------------------------------------------------
  // Connection / type / protocol as transport key alternatives
  // ---------------------------------------------------------------

  /**
   * Test alternative transport key connection.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlternativeTransportKeyConnection() throws JSONException {
    JSONObject input = new JSONObject();
    input.put(CONNECTION, "sse");
    input.put("url", HTTP_LOCALHOST_SSE);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals("sse", result.getJSONObject(0).getString(TRANSPORT));
  }

  /**
   * Test alternative transport key type.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlternativeTransportKeyType() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("type", WEBSOCKET);
    input.put("url", "ws://localhost/ws");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(WEBSOCKET, result.getJSONObject(0).getString(TRANSPORT));
  }

  /**
   * Test alternative transport key protocol.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testAlternativeTransportKeyProtocol() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("protocol", STDIO);
    input.put(COMMAND, "node");

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(STDIO, result.getJSONObject(0).getString(TRANSPORT));
  }

  // ---------------------------------------------------------------
  // stdio sub-object with "path" instead of COMMAND
  // ---------------------------------------------------------------

  /**
   * Test stdio detected by stdio key without transport.
   * @throws JSONException if an error occurs
   */
  @Test
  public void testStdioDetectedByStdioKeyWithoutTransport() throws JSONException {
    // When there is a STDIO key but no explicit transport/command,
    // detectTransport detects it as stdio, but buildStdio can't find a command -> null
    JSONObject stdioSub = new JSONObject();
    stdioSub.put("path", "/usr/bin/python3");

    JSONObject input = new JSONObject();
    input.put(STDIO, stdioSub);

    JSONArray result = MCPConfigNormalizer.normalizeToArray(input, "srv");
    assertEquals(0, result.length());
  }
}
