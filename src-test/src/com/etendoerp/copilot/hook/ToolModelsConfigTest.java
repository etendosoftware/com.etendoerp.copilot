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
package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.weld.test.WeldBaseTest;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.data.TeamMember;

/**
 * Test class for ToolModelsConfig hook.
 *
 * <p>This test suite validates the functionality of the ToolModelsConfig hook
 * which is responsible for adding tool model configurations to the JSON response
 * for copilot apps and their team members.</p>
 */
public class ToolModelsConfigTest extends WeldBaseTest {

  private ToolModelsConfig toolModelsConfig;
  private AutoCloseable mocks;

  @Mock
  private CopilotApp mockApp;

  @Mock
  private CopilotAppTool mockAppTool1;

  @Mock
  private CopilotAppTool mockAppTool2;

  @Mock
  private CopilotTool mockTool1;

  @Mock
  private CopilotTool mockTool2;

  @Mock
  private TeamMember mockTeamMember;

  @Mock
  private CopilotApp mockTeamMemberApp;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    toolModelsConfig = new ToolModelsConfig();
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  /**
   * Test that typeCheck always returns true for any app.
   */
  @Test
  public void testTypeCheckAlwaysReturnsTrue() {
    assertTrue("typeCheck should always return true", toolModelsConfig.typeCheck(mockApp));
    assertTrue("typeCheck should always return true for null", toolModelsConfig.typeCheck(null));
  }

  /**
   * Test exec with a single app that has tools with model configuration.
   */
  @Test
  public void testExecWithSingleAppAndTools() throws Exception {
    // Setup app
    when(mockApp.getId()).thenReturn("app-123");
    when(mockApp.getName()).thenReturn("Test App");
    when(mockApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());

    // Setup tool 1 with provider/model format
    when(mockTool1.getId()).thenReturn("tool-1");
    when(mockAppTool1.getCopilotTool()).thenReturn(mockTool1);
    when(mockAppTool1.getModel()).thenReturn("openai/gpt-4-turbo");

    // Setup tool 2 with provider/model format
    when(mockTool2.getId()).thenReturn("tool-2");
    when(mockAppTool2.getCopilotTool()).thenReturn(mockTool2);
    when(mockAppTool2.getModel()).thenReturn("gemini/gemini-2.5-pro");

    List<CopilotAppTool> appTools = new ArrayList<>();
    appTools.add(mockAppTool1);
    appTools.add(mockAppTool2);
    when(mockApp.getETCOPAppToolList()).thenReturn(appTools);

    // Create JSON object
    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    // Execute
    toolModelsConfig.exec(mockApp, json);

    // Verify
    JSONObject extraInfo = json.getJSONObject("extra_info");
    assertNotNull("extra_info should exist", extraInfo);
    assertTrue("tool_config should exist", extraInfo.has("tool_config"));

    JSONObject toolConfig = extraInfo.getJSONObject("tool_config");
    assertTrue("App config should exist", toolConfig.has("app-123"));

    JSONObject appConfig = toolConfig.getJSONObject("app-123");
    assertTrue("Tool 1 config should exist", appConfig.has("tool-1"));
    assertTrue("Tool 2 config should exist", appConfig.has("tool-2"));

    // Verify tool 1
    JSONObject tool1Config = appConfig.getJSONObject("tool-1");
    assertEquals("gpt-4-turbo", tool1Config.getString("model"));
    assertEquals("openai", tool1Config.getString("provider"));

    // Verify tool 2
    JSONObject tool2Config = appConfig.getJSONObject("tool-2");
    assertEquals("gemini-2.5-pro", tool2Config.getString("model"));
    assertEquals("gemini", tool2Config.getString("provider"));
  }

  /**
   * Test exec with model string without provider (should default to openai).
   */
  @Test
  public void testExecWithModelWithoutProvider() throws Exception {
    // Setup app
    when(mockApp.getId()).thenReturn("app-456");
    when(mockApp.getName()).thenReturn("Test App");
    when(mockApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());

    // Setup tool with model only (no provider)
    when(mockTool1.getId()).thenReturn("tool-1");
    when(mockAppTool1.getCopilotTool()).thenReturn(mockTool1);
    when(mockAppTool1.getModel()).thenReturn("gpt-4o-mini");

    List<CopilotAppTool> appTools = new ArrayList<>();
    appTools.add(mockAppTool1);
    when(mockApp.getETCOPAppToolList()).thenReturn(appTools);

    // Create JSON object
    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    // Execute
    toolModelsConfig.exec(mockApp, json);

    // Verify
    JSONObject appConfig = json.getJSONObject("extra_info")
        .getJSONObject("tool_config")
        .getJSONObject("app-456");
    JSONObject tool1Config = appConfig.getJSONObject("tool-1");

    assertEquals("gpt-4o-mini", tool1Config.getString("model"));
    assertEquals("openai", tool1Config.getString("provider"));
  }

  /**
   * Test exec with app that has tools without model (should be skipped).
   */
  @Test
  public void testExecWithToolsWithoutModel() throws Exception {
    // Setup app
    when(mockApp.getId()).thenReturn("app-789");
    when(mockApp.getName()).thenReturn("Test App");
    when(mockApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());

    // Setup tool without model
    when(mockTool1.getId()).thenReturn("tool-1");
    when(mockAppTool1.getCopilotTool()).thenReturn(mockTool1);
    when(mockAppTool1.getModel()).thenReturn(null);

    List<CopilotAppTool> appTools = new ArrayList<>();
    appTools.add(mockAppTool1);
    when(mockApp.getETCOPAppToolList()).thenReturn(appTools);

    // Create JSON object
    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    // Execute
    toolModelsConfig.exec(mockApp, json);

    // Verify - app should exist but no tool config
    JSONObject appConfig = json.getJSONObject("extra_info")
        .getJSONObject("tool_config")
        .getJSONObject("app-789");
    assertFalse("Tool without model should not be included", appConfig.has("tool-1"));
  }

  /**
   * Test exec with team members - should include team member apps.
   */
  @Test
  public void testExecWithTeamMembers() throws Exception {
    // Setup main app
    when(mockApp.getId()).thenReturn("main-app");
    when(mockApp.getName()).thenReturn("Main App");
    when(mockApp.getETCOPAppToolList()).thenReturn(new ArrayList<>());

    // Setup team member app
    when(mockTeamMemberApp.getId()).thenReturn("team-app");
    when(mockTeamMemberApp.getName()).thenReturn("Team App");

    // Setup tool for team member app
    when(mockTool1.getId()).thenReturn("tool-1");
    when(mockAppTool1.getCopilotTool()).thenReturn(mockTool1);
    when(mockAppTool1.getModel()).thenReturn("anthropic/claude-3-5-sonnet");

    List<CopilotAppTool> teamAppTools = new ArrayList<>();
    teamAppTools.add(mockAppTool1);
    when(mockTeamMemberApp.getETCOPAppToolList()).thenReturn(teamAppTools);

    // Setup team member
    when(mockTeamMember.getCopilotApp()).thenReturn(mockTeamMemberApp);
    List<TeamMember> teamMembers = new ArrayList<>();
    teamMembers.add(mockTeamMember);
    when(mockApp.getETCOPTeamMemberList()).thenReturn(teamMembers);

    // Create JSON object
    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    // Execute
    toolModelsConfig.exec(mockApp, json);

    // Verify
    JSONObject toolConfig = json.getJSONObject("extra_info").getJSONObject("tool_config");
    assertTrue("Main app should exist", toolConfig.has("main-app"));
    assertTrue("Team member app should exist", toolConfig.has("team-app"));

    JSONObject teamAppConfig = toolConfig.getJSONObject("team-app");
    assertTrue("Team app tool should exist", teamAppConfig.has("tool-1"));

    JSONObject tool1Config = teamAppConfig.getJSONObject("tool-1");
    assertEquals("claude-3-5-sonnet", tool1Config.getString("model"));
    assertEquals("anthropic", tool1Config.getString("provider"));
  }

  /**
   * Test exec with empty tool list - should create empty app config.
   */
  @Test
  public void testExecWithEmptyToolList() throws Exception {
    // Setup app with no tools
    when(mockApp.getId()).thenReturn("empty-app");
    when(mockApp.getName()).thenReturn("Empty App");
    when(mockApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());
    when(mockApp.getETCOPAppToolList()).thenReturn(new ArrayList<>());

    // Create JSON object
    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    // Execute
    toolModelsConfig.exec(mockApp, json);

    // Verify - app should exist but be empty
    JSONObject toolConfig = json.getJSONObject("extra_info").getJSONObject("tool_config");
    assertTrue("App should exist", toolConfig.has("empty-app"));
    JSONObject appConfig = toolConfig.getJSONObject("empty-app");
    assertEquals("App config should be empty", 0, appConfig.length());
  }

  /**
   * Test exec handles exceptions gracefully.
   */
  @Test
  public void testExecHandlesExceptionsGracefully() throws Exception {
    // Setup app that will throw exception
    when(mockApp.getId()).thenReturn("error-app");
    when(mockApp.getName()).thenReturn("Error App");
    when(mockApp.getETCOPTeamMemberList()).thenThrow(new RuntimeException("Test exception"));

    // Create JSON object
    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    // Execute - should not throw exception
    toolModelsConfig.exec(mockApp, json);

    // Verify - tool_config might not exist or be partial due to exception
    // The important thing is that no exception is thrown
    assertTrue("Should complete without throwing exception", true);
  }

  /**
   * Test exec with model string containing multiple slashes (edge case).
   */
  @Test
  public void testExecWithModelStringMultipleSlashes() throws Exception {
    // Setup app
    when(mockApp.getId()).thenReturn("app-multi-slash");
    when(mockApp.getName()).thenReturn("Test App");
    when(mockApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());

    // Setup tool with multiple slashes (should split on first slash only)
    when(mockTool1.getId()).thenReturn("tool-1");
    when(mockAppTool1.getCopilotTool()).thenReturn(mockTool1);
    when(mockAppTool1.getModel()).thenReturn("openai/gpt-4/turbo");

    List<CopilotAppTool> appTools = new ArrayList<>();
    appTools.add(mockAppTool1);
    when(mockApp.getETCOPAppToolList()).thenReturn(appTools);

    // Create JSON object
    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    // Execute
    toolModelsConfig.exec(mockApp, json);

    // Verify - should split on first slash, keeping rest as model name
    JSONObject tool1Config = json.getJSONObject("extra_info")
        .getJSONObject("tool_config")
        .getJSONObject("app-multi-slash")
        .getJSONObject("tool-1");

    assertEquals("gpt-4/turbo", tool1Config.getString("model"));
    assertEquals("openai", tool1Config.getString("provider"));
  }
}
