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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.base.session.OBPropertiesProvider;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.CopilotUtils;
import org.openbravo.erpCommon.utility.OBMessageUtils;

/**
 * Unit tests for {@link GetMCPConfiguration}.
 */
public class GetMCPConfigurationTest extends WeldBaseTest {

  public static final String LOCALHOST_WARNING = "LOCALHOST_WARNING";
  public static final String DIRECT = "Direct";
  public static final String REMOTE_MODE = "mcp_remote_mode";
  private static final String BEARER_TOKEN = "Bearer TOKEN";
  private static final String OTHER_MCP_CLIENTS = "Other MCP clients";
  private static final String MCP_REMOTE = "mcp-remote";
  private static final String HEADER_FLAG = "--header";
  private static final String AUTH_TYPE_TOKEN_URL = "token_url";
  private static final String AUTH_HEADER = "Authorization";
  private static final String AUTH_TYPE_KEY = "auth_type";
  private static final String TOKEN_QUERY = "?token=";
  private AutoCloseable mocks;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<OBPropertiesProvider> mockedOBPropertiesProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Before
  @Override
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedOBPropertiesProvider = mockStatic(OBPropertiesProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    // default token
    mockedCopilotUtils.when(CopilotUtils::generateEtendoToken).thenReturn("TOKEN");

    // default messageBD
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(any(String.class))).thenAnswer(inv -> {
      String key = inv.getArgument(0);
      if ("ETCOP_ContextURLMCP_Warning".equals(key)) {
        return LOCALHOST_WARNING;
      }
      if ("ETCOP_MCPInstallation".equals(key)) {
        return "INSTALLATION_MSG";
      }
      if ("ETCOP_Multiple_Agents_Not_Allowed".equals(key)) {
        return "MULTIPLE_AGENTS_NOT_ALLOWED";
      }
      return key;
    });

    // default OBPropertiesProvider instance returning empty properties
    OBPropertiesProvider mockProv = mock(OBPropertiesProvider.class);
    Properties p = new Properties();
    mockedOBPropertiesProvider.when(OBPropertiesProvider::getInstance).thenReturn(mockProv);
    org.mockito.Mockito.when(mockProv.getOpenbravoProperties()).thenReturn(p);
  }

  @After
  public void tearDown() throws Exception {
    if (mockedCopilotUtils != null) mockedCopilotUtils.close();
    if (mockedOBPropertiesProvider != null) mockedOBPropertiesProvider.close();
    if (mockedOBMessageUtils != null) mockedOBMessageUtils.close();
    if (mocks != null) mocks.close();
  }

  private CopilotApp mockAgent(String id, String name) {
    CopilotApp a = mock(CopilotApp.class);
    org.mockito.Mockito.when(a.getId()).thenReturn(id);
    org.mockito.Mockito.when(a.getName()).thenReturn(name);
    return a;
  }

  @Test
  public void testGetHTMLConfigurations_direct_standard_singleAgent() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT1", "Agent One");

    JSONObject params = new JSONObject();
    params.put(DIRECT, true);
    params.put(REMOTE_MODE, false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertNotNull(html);
    assertTrue(html.contains("/AGENT1/direct/mcp"));
    assertTrue(html.contains(BEARER_TOKEN));
  }

  @Test
  public void testGetHTMLConfigurations_standard_not_direct() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT2", "Agent Two");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("/AGENT2/mcp"));
    assertFalse(html.contains("/AGENT2/direct/mcp"));
  }

  @Test
  public void testGetHTMLConfigurations_remoteCompatibilityMode() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT3", "Agent Three");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, true);

    String html = g.getHTMLConfigurations(params, List.of(a));
    // remote example should include npx and mcp-remote
    assertTrue(html.contains("npx"));
    assertTrue(html.contains(MCP_REMOTE));
  }

  @Test
  public void testCustomNameAndPrefixMode() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT4", "Agent Four");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);
    params.put("custom_name", "My Custom");
    params.put("prefixMode", true);

    String html = g.getHTMLConfigurations(params, List.of(a));
    // display name should be 'My Custom-Agent Four'
    assertTrue(html.contains("My Custom-Agent Four"));
    // key should be normalized, check presence of part of normalized key
    assertTrue(html.contains("my-custom-agent-four") || html.contains("my-custom"));
  }

  @Test
  public void testLocalhostWarningIncluded() throws Exception {
    // configure properties so context.url.copilot.mcp absent -> uses localhost
    OBPropertiesProvider prov = OBPropertiesProvider.getInstance();
    prov.getOpenbravoProperties().setProperty("copilot.port.mcp", "5006");

    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT5", "Agent Five");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains(LOCALHOST_WARNING));
  }

  @Test
  public void testRemoteUrlNoWarning() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT6", "Agent Six");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);
    params.put("custom_url", "https://example.com:1234");

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertFalse(html.contains(LOCALHOST_WARNING));
    assertTrue(html.contains("https://example.com:1234/AGENT6/mcp"));
  }

  @Test
  public void testActionMultipleAgentsError() throws Exception {
    GetMCPConfiguration spy = org.mockito.Mockito.spy(new GetMCPConfiguration());
    // stub parseAgents to return two agents
    List<CopilotApp> agents = new ArrayList<>();
    agents.add(mockAgent("A1", "A1"));
    agents.add(mockAgent("A2", "A2"));
    doReturn(agents).when(spy).parseAgents(any(JSONObject.class));

    JSONObject params = new JSONObject();
    params.put("recordIds", new JSONArray().put("A1").put("A2"));

    var res = spy.action(params, null);
    assertEquals(com.smf.jobs.Result.Type.ERROR, res.getType());
    assertEquals("MULTIPLE_AGENTS_NOT_ALLOWED", res.getMessage());
  }

  @Test
  public void testActionMalformedJsonHandled() throws Exception {
    GetMCPConfiguration spy = org.mockito.Mockito.spy(new GetMCPConfiguration());
    List<CopilotApp> agents = new ArrayList<>();
    agents.add(mockAgent("A3", "A3"));
    doReturn(agents).when(spy).parseAgents(any(JSONObject.class));

    // missing Direct and mcp_remote_mode -> getHTMLConfigurations will throw JSONException
    JSONObject params = new JSONObject();
    params.put("recordIds", new JSONArray().put("A3"));

    var res = spy.action(params, null);
    assertEquals(com.smf.jobs.Result.Type.ERROR, res.getType());
  }

  @Test
  public void testReadOptStringAndNormalizeAndGetServerUrl() throws Exception {
    // readOptString: literal "null" -> null
    JSONObject j = new JSONObject();
    j.put("x", "null");
    assertEquals(null, GetMCPConfiguration.readOptString(j, "x"));

    // missing property -> null
    assertEquals(null, GetMCPConfiguration.readOptString(new JSONObject(), "nope"));

    // normalize
    String norm = GetMCPConfiguration.normalize("My Name! Test");
    assertEquals("my-name--test", norm);

    // getServerUrlFromConfig standard
    JSONObject cfg = new JSONObject();
    JSONObject inner = new JSONObject();
    inner.put("url", "https://server.example/api");
    cfg.put("k", inner);
    assertEquals("https://server.example/api", new GetMCPConfiguration().getServerUrlFromConfig(cfg));

    // getServerUrlFromConfig remote args
    JSONObject cfg2 = new JSONObject();
    JSONObject inner2 = new JSONObject();
    JSONArray arr = new JSONArray();
    arr.put(MCP_REMOTE);
    arr.put("https://remote.example");
    inner2.put("args", arr);
    cfg2.put("k2", inner2);
    assertEquals("https://remote.example", new GetMCPConfiguration().getServerUrlFromConfig(cfg2));
  }

  @Test
  public void testCustomNameWithoutPrefixMode() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT10", "Agent Ten");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);
    params.put("custom_name", "SoloCustom");

    String html = g.getHTMLConfigurations(params, List.of(a));
    // Display name should be exactly the custom name
    assertTrue(html.contains("SoloCustom"));
    // Key should be normalized to lowercase 'solocustom'
    assertTrue(html.contains("solocustom") || html.contains("solo-custom") || html.contains("solo_custom"));
  }

  // --- auth_type tests ---

  @Test
  public void testAuthType_oauth_standard() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT_OA", "Agent OAuth");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);
    params.put(AUTH_TYPE_KEY, "oauth");

    String html = g.getHTMLConfigurations(params, List.of(a));
    // Clean URL, no token, no headers
    assertTrue(html.contains("/AGENT_OA/mcp"));
    assertFalse(html.contains(TOKEN_QUERY));
    assertFalse(html.contains(AUTH_HEADER));
    assertFalse(html.contains(BEARER_TOKEN));
    // No OTHER_MCP_CLIENTS block (no headers)
    assertFalse(html.contains(OTHER_MCP_CLIENTS));
    // OAuth mode shows a separate URL copy field
    assertTrue(html.contains("MCP Endpoint URL"));
    assertTrue(html.contains("Copy URL"));
  }

  @Test
  public void testAuthType_tokenHeader_standard() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT_TH", "Agent TokenHeader");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);
    params.put(AUTH_TYPE_KEY, "token_header");

    String html = g.getHTMLConfigurations(params, List.of(a));
    // URL without token query param
    assertFalse(html.contains("?token=TOKEN"));
    // Should contain Authorization header
    assertTrue(html.contains(BEARER_TOKEN));
    // Should show OTHER_MCP_CLIENTS block (has headers)
    assertTrue(html.contains(OTHER_MCP_CLIENTS));
  }

  @Test
  public void testAuthType_tokenUrl_standard() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT_TU", "Agent TokenUrl");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);
    params.put(AUTH_TYPE_KEY, AUTH_TYPE_TOKEN_URL);

    String html = g.getHTMLConfigurations(params, List.of(a));
    // URL should contain the token as query parameter
    assertTrue(html.contains("/AGENT_TU/mcp?token=TOKEN"));
    // Should NOT contain Authorization header
    assertFalse(html.contains(AUTH_HEADER));
    assertFalse(html.contains(BEARER_TOKEN));
    // No OTHER_MCP_CLIENTS block (no headers)
    assertFalse(html.contains(OTHER_MCP_CLIENTS));
  }

  @Test
  public void testAuthType_tokenUrl_direct() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT_TU2", "Agent TokenUrl Direct");

    JSONObject params = new JSONObject();
    params.put(DIRECT, true);
    params.put(REMOTE_MODE, false);
    params.put(AUTH_TYPE_KEY, AUTH_TYPE_TOKEN_URL);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("/AGENT_TU2/direct/mcp?token=TOKEN"));
    assertFalse(html.contains(AUTH_HEADER));
  }

  @Test
  public void testAuthType_oauth_remoteMode() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT_OAR", "Agent OAuth Remote");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, true);
    params.put(AUTH_TYPE_KEY, "oauth");

    String html = g.getHTMLConfigurations(params, List.of(a));
    // Should contain npx/mcp-remote with clean URL
    assertTrue(html.contains("npx"));
    assertTrue(html.contains(MCP_REMOTE));
    assertTrue(html.contains("/AGENT_OAR/mcp"));
    // No --header, no token in URL
    assertFalse(html.contains(HEADER_FLAG));
    assertFalse(html.contains(TOKEN_QUERY));
    assertFalse(html.contains(AUTH_HEADER));
  }

  @Test
  public void testAuthType_tokenHeader_remoteMode() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT_THR", "Agent TokenHeader Remote");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, true);
    params.put(AUTH_TYPE_KEY, "token_header");

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("npx"));
    assertTrue(html.contains(MCP_REMOTE));
    assertTrue(html.contains(HEADER_FLAG));
    assertTrue(html.contains("Authorization: Bearer TOKEN"));
    assertFalse(html.contains(TOKEN_QUERY));
  }

  @Test
  public void testAuthType_tokenUrl_remoteMode() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT_TUR", "Agent TokenUrl Remote");

    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, true);
    params.put(AUTH_TYPE_KEY, AUTH_TYPE_TOKEN_URL);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("/AGENT_TUR/mcp?token=TOKEN"));
    assertTrue(html.contains("npx"));
    assertTrue(html.contains(MCP_REMOTE));
    assertFalse(html.contains(HEADER_FLAG));
    assertFalse(html.contains("Authorization: Bearer TOKEN"));
  }

  @Test
  public void testAuthType_defaultsToTokenHeader() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT_DEF", "Agent Default");

    // No auth_type -> should default to token_header
    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertFalse(html.contains(TOKEN_QUERY));
    assertTrue(html.contains(BEARER_TOKEN));
  }

  @Test
  public void testEmptyAgentsListProducesOnlyFixedMessage() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject params = new JSONObject();
    params.put(DIRECT, false);
    params.put(REMOTE_MODE, false);

    String html = g.getHTMLConfigurations(params, List.of());
    // should at least contain the installation fixed message and not contain install badges
    assertTrue(html.contains("INSTALLATION_MSG"));
    assertFalse(html.contains("vscode:mcp/install"));
  }
}
