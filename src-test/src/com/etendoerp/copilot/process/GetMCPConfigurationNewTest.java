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
package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.CopilotUtils;

/**
 * Tests for GetMCPConfiguration that exercise real code paths.
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("java:S1448")
public class GetMCPConfigurationNewTest {
  private static final String AGENT_ONE = "Agent One";
  private static final String DIRECT = "Direct";
  private static final String HEADERS = "headers";
  private static final String HELLO = "hello";
  private static final String HTTP_EXAMPLE_COM = "http://example.com";
  private static final String HTTP_LOCALHOST_5006 = "http://localhost:5006";
  private static final String MCP_REMOTE = "mcp-remote";
  private static final String MCP_REMOTE_MODE = "mcp_remote_mode";
  private static final String MYKEY = "mykey";
  private static final String VSCODE = "VSCode";


  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<OBPropertiesProvider> mockedOBPropertiesProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private Properties props;

  /** Set up. */
  @Before
  public void setUp() {
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedOBPropertiesProvider = mockStatic(OBPropertiesProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedCopilotUtils.when(CopilotUtils::generateEtendoToken).thenReturn("TEST_TOKEN");

    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(any(String.class))).thenAnswer(inv ->
      inv.getArgument(0)
    );

    OBPropertiesProvider mockProv = mock(OBPropertiesProvider.class);
    props = new Properties();
    mockedOBPropertiesProvider.when(OBPropertiesProvider::getInstance).thenReturn(mockProv);
    when(mockProv.getOpenbravoProperties()).thenReturn(props);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    if (mockedCopilotUtils != null) mockedCopilotUtils.close();
    if (mockedOBPropertiesProvider != null) mockedOBPropertiesProvider.close();
    if (mockedOBMessageUtils != null) mockedOBMessageUtils.close();
  }

  private CopilotApp createMockAgent(String id, String name) {
    CopilotApp a = mock(CopilotApp.class);
    when(a.getId()).thenReturn(id);
    when(a.getName()).thenReturn(name);
    return a;
  }

  // --- normalize tests ---

  /** Test normalize simple. */
  @Test
  public void testNormalizeSimple() {
    assertEquals(HELLO, GetMCPConfiguration.normalize("Hello"));
  }

  /** Test normalize with spaces. */
  @Test
  public void testNormalizeWithSpaces() {
    assertEquals("my-agent", GetMCPConfiguration.normalize("My Agent"));
  }

  /** Test normalize with special chars. */
  @Test
  public void testNormalizeWithSpecialChars() {
    assertEquals("test--agent-", GetMCPConfiguration.normalize("Test! Agent."));
  }

  /** Test normalize preserves underscores and dashes. */
  @Test
  public void testNormalizePreservesUnderscoresAndDashes() {
    assertEquals("my_agent-v2", GetMCPConfiguration.normalize("my_agent-v2"));
  }

  /** Test normalize upper case to lower. */
  @Test
  public void testNormalizeUpperCaseToLower() {
    assertEquals("abcdef", GetMCPConfiguration.normalize("ABCDEF"));
  }

  // --- readOptString tests ---

  /**
   * Test read opt string null literal.
   * @throws Exception if an error occurs
   */
  @Test
  public void testReadOptStringNullLiteral() throws Exception {
    JSONObject j = new JSONObject();
    j.put("key", "null");
    assertNull(GetMCPConfiguration.readOptString(j, "key"));
  }

  /**
   * Test read opt string null literal upper case.
   * @throws Exception if an error occurs
   */
  @Test
  public void testReadOptStringNullLiteralUpperCase() throws Exception {
    JSONObject j = new JSONObject();
    j.put("key", "NULL");
    assertNull(GetMCPConfiguration.readOptString(j, "key"));
  }

  /**
   * Test read opt string missing property.
   * @throws Exception if an error occurs
   */
  @Test
  public void testReadOptStringMissingProperty() throws Exception {
    assertNull(GetMCPConfiguration.readOptString(new JSONObject(), "missing"));
  }

  /**
   * Test read opt string normal value.
   * @throws Exception if an error occurs
   */
  @Test
  public void testReadOptStringNormalValue() throws Exception {
    JSONObject j = new JSONObject();
    j.put("key", HELLO);
    assertEquals(HELLO, GetMCPConfiguration.readOptString(j, "key"));
  }

  /**
   * Test read opt string empty value.
   * @throws Exception if an error occurs
   */
  @Test
  public void testReadOptStringEmptyValue() throws Exception {
    JSONObject j = new JSONObject();
    j.put("key", "");
    assertEquals("", GetMCPConfiguration.readOptString(j, "key"));
  }

  // --- getConfig tests ---

  /**
   * Test get config standard mode.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetConfigStandardMode() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent1", AGENT_ONE);
    req.setOptions(false, false, "mytoken", HTTP_LOCALHOST_5006, null, false, null);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    assertNotNull(config);

    String key = config.keys().next().toString();
    assertEquals("agent-one", key);

    JSONObject inner = config.getJSONObject(key);
    assertEquals(AGENT_ONE, inner.getString("name"));
    assertEquals("http://localhost:5006/agent1/mcp", inner.getString("url"));
    assertEquals("http", inner.getString("type"));
    assertTrue(inner.has(HEADERS));
    assertEquals("Bearer mytoken", inner.getJSONObject(HEADERS).getString("Authorization"));
  }

  /**
   * Test get config direct mode.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetConfigDirectMode() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent2", "Agent Two");
    req.setOptions(true, false, "tok2", HTTP_EXAMPLE_COM, null, false, null);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    JSONObject inner = config.getJSONObject(key);

    assertEquals("http://example.com/agent2/direct/mcp", inner.getString("url"));
  }

  /**
   * Test get config remote mode.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetConfigRemoteMode() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent3", "Agent Three");
    req.setOptions(false, true, "tok3", HTTP_EXAMPLE_COM, null, false, null);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    JSONObject inner = config.getJSONObject(key);

    assertEquals("npx", inner.getString("command"));
    JSONArray args = inner.getJSONArray("args");
    assertEquals(MCP_REMOTE, args.getString(0));
    assertEquals("http://example.com/agent3/mcp", args.getString(1));
    assertEquals("--header", args.getString(2));
    assertEquals("Authorization: Bearer tok3", args.getString(3));
  }

  /**
   * Test get config custom name without prefix.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetConfigCustomNameWithoutPrefix() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent4", "Agent Four");
    req.setOptions(false, false, "tok4", HTTP_EXAMPLE_COM, "CustomName", false, null);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    assertEquals("customname", key);

    JSONObject inner = config.getJSONObject(key);
    assertEquals("CustomName", inner.getString("name"));
  }

  /**
   * Test get config custom name with prefix.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetConfigCustomNameWithPrefix() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent5", "Agent Five");
    req.setOptions(false, false, "tok5", HTTP_EXAMPLE_COM, "Prefix", true, null);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    assertEquals("prefix-agent-five", key);

    JSONObject inner = config.getJSONObject(key);
    assertEquals("Prefix-Agent Five", inner.getString("name"));
  }

  /**
   * Test get config remote direct mode.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetConfigRemoteDirectMode() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent6", "Agent Six");
    req.setOptions(true, true, "tok6", HTTP_EXAMPLE_COM, null, false, null);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    JSONObject inner = config.getJSONObject(key);

    JSONArray args = inner.getJSONArray("args");
    assertEquals("http://example.com/agent6/direct/mcp", args.getString(1));
  }

  // --- getServerUrlFromConfig tests ---

  /**
   * Test get server url from config standard.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetServerUrlFromConfigStandard() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("url", "https://server.com/api");
    cfg.put(MYKEY, inner);

    assertEquals("https://server.com/api", g.getServerUrlFromConfig(cfg));
  }

  /**
   * Test get server url from config remote args.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetServerUrlFromConfigRemoteArgs() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    JSONArray args = new JSONArray();
    args.put(MCP_REMOTE);
    args.put("https://remote.com");
    inner.put("args", args);
    cfg.put(MYKEY, inner);

    assertEquals("https://remote.com", g.getServerUrlFromConfig(cfg));
  }

  /**
   * Test get server url from config empty.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetServerUrlFromConfigEmpty() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    assertNull(g.getServerUrlFromConfig(cfg));
  }

  /**
   * Test get server url from config no url no args.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetServerUrlFromConfigNoUrlNoArgs() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("name", "test");
    cfg.put(MYKEY, inner);

    assertNull(g.getServerUrlFromConfig(cfg));
  }

  /**
   * Test get server url from config args with one element.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetServerUrlFromConfigArgsWithOneElement() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    JSONArray args = new JSONArray();
    args.put(MCP_REMOTE);
    inner.put("args", args);
    cfg.put(MYKEY, inner);

    assertNull(g.getServerUrlFromConfig(cfg));
  }

  // --- detectLocalhostFromConfigs tests ---

  /**
   * Test detect localhost from configs true.
   * @throws Exception if an error occurs
   */
  @Test
  public void testDetectLocalhostFromConfigsTrue() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("url", "http://localhost:5006/agent/mcp");
    cfg.put("k", inner);

    assertTrue(g.detectLocalhostFromConfigs(List.of(cfg)));
  }

  /**
   * Test detect localhost from configs false.
   * @throws Exception if an error occurs
   */
  @Test
  public void testDetectLocalhostFromConfigsFalse() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("url", "https://example.com/agent/mcp");
    cfg.put("k", inner);

    assertFalse(g.detectLocalhostFromConfigs(List.of(cfg)));
  }

  /** Test detect localhost from configs empty. */
  @Test
  public void testDetectLocalhostFromConfigsEmpty() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertFalse(g.detectLocalhostFromConfigs(Collections.emptyList()));
  }

  // --- getContextUrlMCP tests ---

  /** Test get context url m c p custom url. */
  @Test
  public void testGetContextUrlMCPCustomUrl() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("https://custom.com", g.getContextUrlMCP("https://custom.com"));
  }

  /** Test get context url m c p from property. */
  @Test
  public void testGetContextUrlMCPFromProperty() {
    props.setProperty("context.url.copilot.mcp", "https://prop.com");
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("https://prop.com", g.getContextUrlMCP(null));
  }

  /** Test get context url m c p fallback to localhost with port. */
  @Test
  public void testGetContextUrlMCPFallbackToLocalhostWithPort() {
    props.setProperty("copilot.port.mcp", "9999");
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("http://localhost:9999", g.getContextUrlMCP(null));
  }

  /** Test get context url m c p fallback default port. */
  @Test
  public void testGetContextUrlMCPFallbackDefaultPort() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals(HTTP_LOCALHOST_5006, g.getContextUrlMCP(null));
  }

  /** Test get context url m c p blank custom url. */
  @Test
  public void testGetContextUrlMCPBlankCustomUrl() {
    props.setProperty("copilot.port.mcp", "5006");
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals(HTTP_LOCALHOST_5006, g.getContextUrlMCP(""));
  }

  // --- buildMessage tests ---

  /** Test build message. */
  @Test
  public void testBuildMessage() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildMessage("test content");
    assertTrue(result.contains("test content"));
    assertTrue(result.contains("<span"));
    assertTrue(result.contains("</span>"));
  }

  // --- buildLocalhostWarning tests ---

  /** Test build localhost warning with localhost url. */
  @Test
  public void testBuildLocalhostWarningWithLocalhostUrl() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildLocalhostWarning(HTTP_LOCALHOST_5006);
    assertFalse(result.isEmpty());
    assertTrue(result.contains("ETCOP_ContextURLMCP_Warning"));
  }

  /** Test build localhost warning with remote url. */
  @Test
  public void testBuildLocalhostWarningWithRemoteUrl() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildLocalhostWarning("https://example.com");
    assertEquals("", result);
  }

  /** Test build localhost warning with null. */
  @Test
  public void testBuildLocalhostWarningWithNull() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("", g.buildLocalhostWarning(null));
  }

  // --- buildInstallBadge tests ---

  /** Test build install badge. */
  @Test
  public void testBuildInstallBadge() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildInstallBadge("vscode:mcp/install?test");
    assertTrue(result.contains("vscode:mcp/install?test"));
    assertTrue(result.contains("<a href="));
    assertTrue(result.contains("Install%20in%20-VSCode"));
  }

  // --- buildFixedMessage tests ---

  /** Test build fixed message. */
  @Test
  public void testBuildFixedMessage() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildFixedMessage();
    assertTrue(result.contains("ETCOP_MCPInstallation"));
  }

  // --- buildCodeBlock tests ---

  /**
   * Test build code block.
   * @throws Exception if an error occurs
   */
  @Test
  public void testBuildCodeBlock() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String json = "{ \"test\": \"value\" }";
    String result = g.buildCodeBlock(json, "testSuffix", VSCODE);
    assertTrue(result.contains(VSCODE));
    assertTrue(result.contains("copilotMCP_btnCopiar_testSuffix"));
    assertTrue(result.contains("copilotMCP_json_testSuffix"));
    assertTrue(result.contains("Copy"));
  }

  // --- buildConfigFragment tests ---

  /**
   * Test build config fragment.
   * @throws Exception if an error occurs
   */
  @Test
  public void testBuildConfigFragment() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("url", "http://example.com/mcp");
    inner.put("type", "http");
    inner.put("name", "TestAgent");
    JSONObject headers = new JSONObject();
    headers.put("Authorization", "Bearer tok");
    inner.put(HEADERS, headers);
    cfg.put("test-agent", inner);

    String result = g.buildConfigFragment(cfg);
    assertNotNull(result);
    assertTrue(result.contains("vscode:mcp/install"));
    assertTrue(result.contains(VSCODE));
    assertTrue(result.contains("Other MCP clients"));
  }

  // --- buildConfigsFromAgents tests ---

  /**
   * Test build configs from agents single.
   * @throws Exception if an error occurs
   */
  @Test
  public void testBuildConfigsFromAgentsSingle() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp agent = createMockAgent("AG1", AGENT_ONE);
    List<JSONObject> configs = g.buildConfigsFromAgents(
        List.of(agent), false, false, "tok", HTTP_EXAMPLE_COM, null, false, null);

    assertEquals(1, configs.size());
    JSONObject cfg = configs.get(0);
    assertTrue(cfg.has("agent-one"));
  }

  /**
   * Test build configs from agents multiple.
   * @throws Exception if an error occurs
   */
  @Test
  public void testBuildConfigsFromAgentsMultiple() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a1 = createMockAgent("AG1", AGENT_ONE);
    CopilotApp a2 = createMockAgent("AG2", "Agent Two");
    List<JSONObject> configs = g.buildConfigsFromAgents(
        List.of(a1, a2), true, false, "tok", HTTP_EXAMPLE_COM, null, false, null);

    assertEquals(2, configs.size());
  }

  /**
   * Test build configs from agents empty.
   * @throws Exception if an error occurs
   */
  @Test
  public void testBuildConfigsFromAgentsEmpty() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    List<JSONObject> configs = g.buildConfigsFromAgents(
        Collections.emptyList(), false, false, "tok", HTTP_EXAMPLE_COM, null, false, null);

    assertTrue(configs.isEmpty());
  }

  // --- getHTMLConfigurations tests ---

  /**
   * Test get h t m l configurations standard.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetHTMLConfigurationsStandard() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = createMockAgent("A1", "TestAgent");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(MCP_REMOTE_MODE, false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertNotNull(html);
    assertTrue(html.contains("A1"));
    assertTrue(html.contains("/mcp"));
  }

  /**
   * Test get h t m l configurations direct remote.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetHTMLConfigurationsDirectRemote() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = createMockAgent("A2", "TestAgent2");

    JSONObject params = new JSONObject();
    params.put(DIRECT, true);
    params.put(MCP_REMOTE_MODE, true);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("npx"));
    assertTrue(html.contains("/direct/mcp"));
  }

  /**
   * Test get h t m l configurations with custom url.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetHTMLConfigurationsWithCustomUrl() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = createMockAgent("A3", "Agent3");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(MCP_REMOTE_MODE, false);
    params.put("custom_url", "https://myserver.com");

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("https://myserver.com"));
    assertFalse(html.contains("ETCOP_ContextURLMCP_Warning"));
  }

  // --- ConfigRequest tests ---

  /** Test config request construction. */
  @Test
  public void testConfigRequestConstruction() {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("id1", "name1");
    assertEquals("id1", req.agentId);
    assertEquals("name1", req.agentName);
  }

  /** Test config request set options. */
  @Test
  public void testConfigRequestSetOptions() {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("id", "name");
    req.setOptions(true, true, "tok", "url", "custom", true, null);
    assertTrue(req.direct);
    assertTrue(req.mcpRemoteCompatibilityMode);
    assertEquals("tok", req.token);
    assertEquals("url", req.contextUrlMcp);
    assertEquals("custom", req.customName);
    assertTrue(req.prefixMode);
  }

  // --- getInputClass test ---

  /** Test get input class. */
  @Test
  public void testGetInputClass() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals(com.etendoerp.webhookevents.data.DefinedwebhookToken.class, g.getInputClass());
  }

  // --- buildConfigFragment with JetBrains adaptation ---

  /**
   * Test build config fragment adapts headers for jet brains.
   * @throws Exception if an error occurs
   */
  @Test
  public void testBuildConfigFragmentAdaptsHeadersForJetBrains() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();

    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent7", "Agent Seven");
    req.setOptions(false, false, "tok7", HTTP_EXAMPLE_COM, null, false, null);
    JSONObject cfg = GetMCPConfiguration.getConfig(req);

    String result = g.buildConfigFragment(cfg);
    // The JetBrains section should have requestInit containing headers
    assertTrue(result.contains("requestInit"));
    assertTrue(result.contains("Other MCP clients"));
  }
}
