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

  private AutoCloseable mocks;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<OBPropertiesProvider> mockedOBPropertiesProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedOBPropertiesProvider = mockStatic(OBPropertiesProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    // default token
    mockedCopilotUtils.when(() -> CopilotUtils.generateEtendoToken()).thenReturn("TOKEN");

    // default messageBD
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(any(String.class))).thenAnswer(inv -> {
      String key = inv.getArgument(0);
      if ("ETCOP_ContextURLMCP_Warning".equals(key)) {
        return "LOCALHOST_WARNING";
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
    params.put("Direct", true);
    params.put("mcp_remote_mode", false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertNotNull(html);
    assertTrue(html.contains("/AGENT1/direct/mcp"));
    assertTrue(html.contains("Bearer TOKEN"));
  }

  @Test
  public void testGetHTMLConfigurations_standard_not_direct() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT2", "Agent Two");

    JSONObject params = new JSONObject();
    params.put("Direct", false);
    params.put("mcp_remote_mode", false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("/AGENT2/mcp"));
    assertFalse(html.contains("/AGENT2/direct/mcp"));
  }

  @Test
  public void testGetHTMLConfigurations_remoteCompatibilityMode() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT3", "Agent Three");

    JSONObject params = new JSONObject();
    params.put("Direct", false);
    params.put("mcp_remote_mode", true);

    String html = g.getHTMLConfigurations(params, List.of(a));
    // remote example should include npx and mcp-remote
    assertTrue(html.contains("npx"));
    assertTrue(html.contains("mcp-remote"));
  }

  @Test
  public void testCustomNameAndPrefixMode() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT4", "Agent Four");

    JSONObject params = new JSONObject();
    params.put("Direct", false);
    params.put("mcp_remote_mode", false);
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
    params.put("Direct", false);
    params.put("mcp_remote_mode", false);

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertTrue(html.contains("LOCALHOST_WARNING"));
  }

  @Test
  public void testRemoteUrlNoWarning() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    CopilotApp a = mockAgent("AGENT6", "Agent Six");

    JSONObject params = new JSONObject();
    params.put("Direct", false);
    params.put("mcp_remote_mode", false);
    params.put("custom_url", "https://example.com:1234");

    String html = g.getHTMLConfigurations(params, List.of(a));
    assertFalse(html.contains("LOCALHOST_WARNING"));
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
    arr.put("mcp-remote");
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
    params.put("Direct", false);
    params.put("mcp_remote_mode", false);
    params.put("custom_name", "SoloCustom");

    String html = g.getHTMLConfigurations(params, List.of(a));
    // Display name should be exactly the custom name
    assertTrue(html.contains("SoloCustom"));
    // Key should be normalized to lowercase 'solocustom'
    assertTrue(html.contains("solocustom") || html.contains("solo-custom") || html.contains("solo_custom"));
  }

  @Test
  public void testEmptyAgentsListProducesOnlyFixedMessage() throws Exception {
    GetMCPConfiguration g = new GetMCPConfiguration();
    JSONObject params = new JSONObject();
    params.put("Direct", false);
    params.put("mcp_remote_mode", false);

    String html = g.getHTMLConfigurations(params, List.of());
    // should at least contain the installation fixed message and not contain install badges
    assertTrue(html.contains("INSTALLATION_MSG"));
    assertFalse(html.contains("vscode:mcp/install"));
  }
}
