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
public class GetMCPConfigurationNewTest {

  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<OBPropertiesProvider> mockedOBPropertiesProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private OBPropertiesProvider mockProv;
  private Properties props;

  @Before
  public void setUp() {
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedOBPropertiesProvider = mockStatic(OBPropertiesProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedCopilotUtils.when(CopilotUtils::generateEtendoToken).thenReturn("TEST_TOKEN");

    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(any(String.class))).thenAnswer(inv -> {
      String key = inv.getArgument(0);
      return key;
    });

    mockProv = mock(OBPropertiesProvider.class);
    props = new Properties();
    mockedOBPropertiesProvider.when(OBPropertiesProvider::getInstance).thenReturn(mockProv);
    when(mockProv.getOpenbravoProperties()).thenReturn(props);
  }

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

  @Test
  public void testNormalizeSimple() {
    assertEquals("hello", GetMCPConfiguration.normalize("Hello"));
  }

  @Test
  public void testNormalizeWithSpaces() {
    assertEquals("my-agent", GetMCPConfiguration.normalize("My Agent"));
  }

  @Test
  public void testNormalizeWithSpecialChars() {
    assertEquals("test--agent-", GetMCPConfiguration.normalize("Test! Agent."));
  }

  @Test
  public void testNormalizePreservesUnderscoresAndDashes() {
    assertEquals("my_agent-v2", GetMCPConfiguration.normalize("my_agent-v2"));
  }

  @Test
  public void testNormalizeUpperCaseToLower() {
    assertEquals("abcdef", GetMCPConfiguration.normalize("ABCDEF"));
  }

  // --- readOptString tests ---

  @Test
  public void testReadOptStringNullLiteral() throws Exception {
    JSONObject j = new JSONObject();
    j.put("key", "null");
    assertNull(GetMCPConfiguration.readOptString(j, "key"));
  }

  @Test
  public void testReadOptStringNullLiteralUpperCase() throws Exception {
    JSONObject j = new JSONObject();
    j.put("key", "NULL");
    assertNull(GetMCPConfiguration.readOptString(j, "key"));
  }

  @Test
  public void testReadOptStringMissingProperty() throws Exception {
    assertNull(GetMCPConfiguration.readOptString(new JSONObject(), "missing"));
  }

  @Test
  public void testReadOptStringNormalValue() throws Exception {
    JSONObject j = new JSONObject();
    j.put("key", "hello");
    assertEquals("hello", GetMCPConfiguration.readOptString(j, "key"));
  }

  @Test
  public void testReadOptStringEmptyValue() throws Exception {
    JSONObject j = new JSONObject();
    j.put("key", "");
    assertEquals("", GetMCPConfiguration.readOptString(j, "key"));
  }

  // --- getConfig tests ---

  @Test
  public void testGetConfigStandardMode() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent1", "Agent One");
    req.setOptions(false, false, "mytoken", "http://localhost:5006", null, false);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    assertNotNull(config);

    String key = config.keys().next().toString();
    assertEquals("agent-one", key);

    JSONObject inner = config.getJSONObject(key);
    assertEquals("Agent One", inner.getString("name"));
    assertEquals("http://localhost:5006/agent1/mcp", inner.getString("url"));
    assertEquals("http", inner.getString("type"));
    assertTrue(inner.has("headers"));
    assertEquals("Bearer mytoken", inner.getJSONObject("headers").getString("Authorization"));
  }

  @Test
  public void testGetConfigDirectMode() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent2", "Agent Two");
    req.setOptions(true, false, "tok2", "http://example.com", null, false);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    JSONObject inner = config.getJSONObject(key);

    assertEquals("http://example.com/agent2/direct/mcp", inner.getString("url"));
  }

  @Test
  public void testGetConfigRemoteMode() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent3", "Agent Three");
    req.setOptions(false, true, "tok3", "http://example.com", null, false);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    JSONObject inner = config.getJSONObject(key);

    assertEquals("npx", inner.getString("command"));
    JSONArray args = inner.getJSONArray("args");
    assertEquals("mcp-remote", args.getString(0));
    assertEquals("http://example.com/agent3/mcp", args.getString(1));
    assertEquals("--header", args.getString(2));
    assertEquals("Authorization: Bearer tok3", args.getString(3));
  }

  @Test
  public void testGetConfigCustomNameWithoutPrefix() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent4", "Agent Four");
    req.setOptions(false, false, "tok4", "http://example.com", "CustomName", false);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    assertEquals("customname", key);

    JSONObject inner = config.getJSONObject(key);
    assertEquals("CustomName", inner.getString("name"));
  }

  @Test
  public void testGetConfigCustomNameWithPrefix() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent5", "Agent Five");
    req.setOptions(false, false, "tok5", "http://example.com", "Prefix", true);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    assertEquals("prefix-agent-five", key);

    JSONObject inner = config.getJSONObject(key);
    assertEquals("Prefix-Agent Five", inner.getString("name"));
  }

  @Test
  public void testGetConfigRemoteDirectMode() throws Exception {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent6", "Agent Six");
    req.setOptions(true, true, "tok6", "http://example.com", null, false);

    JSONObject config = GetMCPConfiguration.getConfig(req);
    String key = config.keys().next().toString();
    JSONObject inner = config.getJSONObject(key);

    JSONArray args = inner.getJSONArray("args");
    assertEquals("http://example.com/agent6/direct/mcp", args.getString(1));
  }

  // --- getServerUrlFromConfig tests ---

  @Test
  public void testGetServerUrlFromConfigStandard() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("url", "https://server.com/api");
    cfg.put("mykey", inner);

    assertEquals("https://server.com/api", g.getServerUrlFromConfig(cfg));
  }

  @Test
  public void testGetServerUrlFromConfigRemoteArgs() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    JSONArray args = new JSONArray();
    args.put("mcp-remote");
    args.put("https://remote.com");
    inner.put("args", args);
    cfg.put("mykey", inner);

    assertEquals("https://remote.com", g.getServerUrlFromConfig(cfg));
  }

  @Test
  public void testGetServerUrlFromConfigEmpty() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    assertNull(g.getServerUrlFromConfig(cfg));
  }

  @Test
  public void testGetServerUrlFromConfigNoUrlNoArgs() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("name", "test");
    cfg.put("mykey", inner);

    assertNull(g.getServerUrlFromConfig(cfg));
  }

  @Test
  public void testGetServerUrlFromConfigArgsWithOneElement() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    JSONArray args = new JSONArray();
    args.put("mcp-remote");
    inner.put("args", args);
    cfg.put("mykey", inner);

    assertNull(g.getServerUrlFromConfig(cfg));
  }

  // --- detectLocalhostFromConfigs tests ---

  @Test
  public void testDetectLocalhostFromConfigsTrue() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("url", "http://localhost:5006/agent/mcp");
    cfg.put("k", inner);

    assertTrue(g.detectLocalhostFromConfigs(List.of(cfg)));
  }

  @Test
  public void testDetectLocalhostFromConfigsFalse() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("url", "https://example.com/agent/mcp");
    cfg.put("k", inner);

    assertFalse(g.detectLocalhostFromConfigs(List.of(cfg)));
  }

  @Test
  public void testDetectLocalhostFromConfigsEmpty() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertFalse(g.detectLocalhostFromConfigs(Collections.emptyList()));
  }

  // --- getContextUrlMCP tests ---

  @Test
  public void testGetContextUrlMCPCustomUrl() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("https://custom.com", g.getContextUrlMCP("https://custom.com"));
  }

  @Test
  public void testGetContextUrlMCPFromProperty() {
    props.setProperty("context.url.copilot.mcp", "https://prop.com");
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("https://prop.com", g.getContextUrlMCP(null));
  }

  @Test
  public void testGetContextUrlMCPFallbackToLocalhostWithPort() {
    props.setProperty("copilot.port.mcp", "9999");
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("http://localhost:9999", g.getContextUrlMCP(null));
  }

  @Test
  public void testGetContextUrlMCPFallbackDefaultPort() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("http://localhost:5006", g.getContextUrlMCP(null));
  }

  @Test
  public void testGetContextUrlMCPBlankCustomUrl() {
    props.setProperty("copilot.port.mcp", "5006");
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("http://localhost:5006", g.getContextUrlMCP(""));
  }

  // --- buildMessage tests ---

  @Test
  public void testBuildMessage() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildMessage("test content");
    assertTrue(result.contains("test content"));
    assertTrue(result.contains("<span"));
    assertTrue(result.contains("</span>"));
  }

  // --- buildLocalhostWarning tests ---

  @Test
  public void testBuildLocalhostWarningWithLocalhostUrl() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildLocalhostWarning("http://localhost:5006");
    assertFalse(result.isEmpty());
    assertTrue(result.contains("ETCOP_ContextURLMCP_Warning"));
  }

  @Test
  public void testBuildLocalhostWarningWithRemoteUrl() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildLocalhostWarning("https://example.com");
    assertEquals("", result);
  }

  @Test
  public void testBuildLocalhostWarningWithNull() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals("", g.buildLocalhostWarning(null));
  }

  // --- buildInstallBadge tests ---

  @Test
  public void testBuildInstallBadge() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildInstallBadge("vscode:mcp/install?test");
    assertTrue(result.contains("vscode:mcp/install?test"));
    assertTrue(result.contains("<a href="));
    assertTrue(result.contains("Install%20in%20-VSCode"));
  }

  // --- buildFixedMessage tests ---

  @Test
  public void testBuildFixedMessage() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String result = g.buildFixedMessage();
    assertTrue(result.contains("ETCOP_MCPInstallation"));
  }

  // --- buildCodeBlock tests ---

  @Test
  public void testBuildCodeBlock() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    String json = "{ \"test\": \"value\" }";
    String result = g.buildCodeBlock(json, "testSuffix", "VSCode");
    assertTrue(result.contains("VSCode"));
    assertTrue(result.contains("copilotMCP_btnCopiar_testSuffix"));
    assertTrue(result.contains("copilotMCP_json_testSuffix"));
    assertTrue(result.contains("Copy"));
  }

  // --- buildConfigFragment tests ---

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
    inner.put("headers", headers);
    cfg.put("test-agent", inner);

    String result = g.buildConfigFragment(cfg);
    assertNotNull(result);
    assertTrue(result.contains("vscode:mcp/install"));
    assertTrue(result.contains("VSCode"));
    assertTrue(result.contains("Other MCP clients"));
  }

  // --- buildConfigsFromAgents tests ---

  @Test
  public void testBuildConfigsFromAgentsSingle() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp agent = createMockAgent("AG1", "Agent One");
    List<JSONObject> configs = g.buildConfigsFromAgents(
        List.of(agent), false, false, "tok", "http://example.com", null, false);

    assertEquals(1, configs.size());
    JSONObject cfg = configs.get(0);
    assertTrue(cfg.has("agent-one"));
  }

  @Test
  public void testBuildConfigsFromAgentsMultiple() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a1 = createMockAgent("AG1", "Agent One");
    CopilotApp a2 = createMockAgent("AG2", "Agent Two");
    List<JSONObject> configs = g.buildConfigsFromAgents(
        List.of(a1, a2), true, false, "tok", "http://example.com", null, false);

    assertEquals(2, configs.size());
  }

  @Test
  public void testBuildConfigsFromAgentsEmpty() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    List<JSONObject> configs = g.buildConfigsFromAgents(
        Collections.emptyList(), false, false, "tok", "http://example.com", null, false);

    assertTrue(configs.isEmpty());
  }

  // --- getHTMLConfigurations tests ---

  @Test
  public void testGetHTMLConfigurationsStandard() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = createMockAgent("A1", "TestAgent");

    JSONObject params = new JSONObject();
    params.put("Direct", false);
    params.put("mcp_remote_mode", false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertNotNull(html);
    assertTrue(html.contains("A1"));
    assertTrue(html.contains("/mcp"));
  }

  @Test
  public void testGetHTMLConfigurationsDirectRemote() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = createMockAgent("A2", "TestAgent2");

    JSONObject params = new JSONObject();
    params.put("Direct", true);
    params.put("mcp_remote_mode", true);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("npx"));
    assertTrue(html.contains("/direct/mcp"));
  }

  @Test
  public void testGetHTMLConfigurationsWithCustomUrl() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = createMockAgent("A3", "Agent3");

    JSONObject params = new JSONObject();
    params.put("Direct", false);
    params.put("mcp_remote_mode", false);
    params.put("custom_url", "https://myserver.com");

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("https://myserver.com"));
    assertFalse(html.contains("ETCOP_ContextURLMCP_Warning"));
  }

  // --- ConfigRequest tests ---

  @Test
  public void testConfigRequestConstruction() {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("id1", "name1");
    assertEquals("id1", req.agentId);
    assertEquals("name1", req.agentName);
  }

  @Test
  public void testConfigRequestSetOptions() {
    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("id", "name");
    req.setOptions(true, true, "tok", "url", "custom", true);
    assertTrue(req.direct);
    assertTrue(req.mcpRemoteCompatibilityMode);
    assertEquals("tok", req.token);
    assertEquals("url", req.contextUrlMcp);
    assertEquals("custom", req.customName);
    assertTrue(req.prefixMode);
  }

  // --- getInputClass test ---

  @Test
  public void testGetInputClass() {
    GetMCPConfiguration g = new GetMCPConfiguration();
    assertEquals(com.etendoerp.webhookevents.data.DefinedwebhookToken.class, g.getInputClass());
  }

  // --- buildConfigFragment with JetBrains adaptation ---

  @Test
  public void testBuildConfigFragmentAdaptsHeadersForJetBrains() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();

    GetMCPConfiguration.ConfigRequest req = new GetMCPConfiguration.ConfigRequest("agent7", "Agent Seven");
    req.setOptions(false, false, "tok7", "http://example.com", null, false);
    JSONObject cfg = GetMCPConfiguration.getConfig(req);

    String result = g.buildConfigFragment(cfg);
    // The JetBrains section should have requestInit containing headers
    assertTrue(result.contains("requestInit"));
    assertTrue(result.contains("Other MCP clients"));
  }
}
