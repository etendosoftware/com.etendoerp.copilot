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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.test.WeldBaseTest;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.data.CopilotModel;
import com.etendoerp.copilot.data.TeamMember;

/**
 * Test class for ToolModelsConfig hook.
 *
 * <p>This test suite validates the functionality of the ToolModelsConfig hook
 * which is responsible for adding tool model configurations to the JSON response
 * for copilot apps and their team members.</p>
 */
public class ToolModelsConfigTest extends WeldBaseTest {

  // Test constants
  private static final String APP_123 = "app-123";
  private static final String TEST_APP = "Test App";
  private static final String TOOL_1 = "tool-1";
  private static final String TOOL_2 = "tool-2";
  private static final String EXTRA_INFO = "extra_info";
  private static final String TOOL_CONFIG = "tool_config";
  private static final String MODEL = "model";
  private static final String PROVIDER = "provider";
  private static final String OPENAI = "openai";
  private static final String TEAM_APP = "team-app";
  private static final String EMPTY_APP = "empty-app";

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

  /**
   * Initializes test fixtures before each test method.
   *
   * <p>Creates mock objects and initializes the ToolModelsConfig instance
   * that will be tested.</p>
   *
   * @throws Exception if there's an error during initialization
   */
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    toolModelsConfig = new ToolModelsConfig();
  }

  /**
   * Cleanup after each test method.
   *
   * <p>Closes all mock resources to prevent memory leaks.</p>
   *
   * @throws Exception if there's an error during cleanup
   */
  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  /**
   * Helper method to setup a basic app with id, name and empty team members list.
   *
   * <p>This method configures the mock app with the provided ID and name,
   * and sets up an empty list for team members. This is a common setup
   * pattern used across multiple test cases.</p>
   *
   * @param appId The unique identifier for the copilot app
   * @param appName The display name for the copilot app
   */
  private void setupBasicApp(String appId, String appName) {
    when(mockApp.getId()).thenReturn(appId);
    when(mockApp.getName()).thenReturn(appName);
    when(mockApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());
  }

  /**
   * Helper method to setup a tool with its configuration.
   *
   * <p>Configures the mock tool and app tool objects with the specified
   * tool ID and model string. This encapsulates the common pattern of
   * setting up tool mocks used in multiple test cases.</p>
   *
   * @param mockTool The mock CopilotTool object to configure
   * @param mockAppTool The mock CopilotAppTool object to configure
   * @param toolId The unique identifier for the tool
   * @param model The model string (e.g., "openai/gpt-4" or "gpt-4"), can be null
   */
  private void setupTool(CopilotTool mockTool, CopilotAppTool mockAppTool, String toolId, String model) {
    when(mockTool.getId()).thenReturn(toolId);
    when(mockAppTool.getCopilotTool()).thenReturn(mockTool);
    if (model == null) {
      when(mockAppTool.getModel()).thenReturn(null);
      return;
    }
    System.out.println("Setting up tool with model: " + model);
    // model string can be "provider/modelName" or just "modelName" or contain multiple slashes
    String provider = null;
    String name = model;
    int idx = model.indexOf('/');
    if (idx > -1) {
      provider = model.substring(0, idx);
      name = model.substring(idx + 1);
    }
    CopilotModel mockModel = org.mockito.Mockito.mock(CopilotModel.class);
    when(mockModel.getSearchkey()).thenReturn(name);
    when(mockModel.getProvider()).thenReturn(provider);
    when(mockAppTool.getModel()).thenReturn(mockModel);
  }

  /**
   * Helper method to create and return a JSON object with extra_info.
   *
   * <p>Creates a new JSONObject with an empty "extra_info" object nested inside.
   * This is the standard structure expected by the ToolModelsConfig hook.</p>
   *
   * @return A JSONObject containing an "extra_info" key with an empty JSONObject value
   * @throws Exception if there's an error creating the JSON structure
   */
  private JSONObject createJsonWithExtraInfo() throws Exception {
    JSONObject json = new JSONObject();
    json.put(EXTRA_INFO, new JSONObject());
    return json;
  }

  /**
   * Helper method to setup app tools list.
   *
   * <p>Configures the mock app's tool list with the provided tools.
   * Accepts a variable number of tools, making it flexible for tests
   * with different numbers of tools.</p>
   *
   * @param tools Variable number of CopilotAppTool objects to add to the app
   */
  private void setupAppToolsList(CopilotAppTool... tools) {
    List<CopilotAppTool> appTools = new ArrayList<>();
    for (CopilotAppTool tool : tools) {
      appTools.add(tool);
    }
    when(mockApp.getETCOPAppToolList()).thenReturn(appTools);
  }

  /**
   * Helper method to get tool config from JSON response.
   *
   * <p>Navigates through the nested JSON structure to retrieve the tool
   * configuration for a specific app and tool. This simplifies test assertions
   * by encapsulating the path traversal logic.</p>
   *
   * @param json The root JSON object containing the response
   * @param appId The ID of the app containing the tool
   * @param toolId The ID of the tool whose configuration to retrieve
   * @return JSONObject containing the tool's configuration (model and provider)
   * @throws Exception if the path doesn't exist or there's a JSON error
   */
  private JSONObject getToolConfigFromJson(JSONObject json, String appId, String toolId) throws Exception {
    return json.getJSONObject(EXTRA_INFO)
        .getJSONObject(TOOL_CONFIG)
        .getJSONObject(appId)
        .getJSONObject(toolId);
  }

  /**
   * Helper method to verify model and provider in tool config.
   *
   * <p>Asserts that the provided tool configuration contains the expected
   * model and provider values. This encapsulates the common verification
   * pattern used across multiple test cases.</p>
   *
   * @param toolConfig The JSONObject containing the tool configuration
   * @param expectedModel The expected model name (e.g., "gpt-4-turbo")
   * @param expectedProvider The expected provider name (e.g., "openai")
   * @throws Exception if there's an error accessing the JSON properties
   */
  private void verifyToolModelAndProvider(JSONObject toolConfig, String expectedModel, String expectedProvider) throws Exception {
    assertEquals(expectedModel, toolConfig.getString(MODEL));
    assertEquals(expectedProvider, toolConfig.getString(PROVIDER));
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
    setupBasicApp(APP_123, TEST_APP);

    // Setup tool 1 with provider/model format
    setupTool(mockTool1, mockAppTool1, TOOL_1, "openai/gpt-4-turbo");

    // Setup tool 2 with provider/model format
    setupTool(mockTool2, mockAppTool2, TOOL_2, "gemini/gemini-2.5-pro");

    setupAppToolsList(mockAppTool1, mockAppTool2);

    // Create JSON object and execute
    JSONObject json = createJsonWithExtraInfo();
    toolModelsConfig.exec(mockApp, json);

    // Verify
    JSONObject extraInfo = json.getJSONObject(EXTRA_INFO);
    assertNotNull("extra_info should exist", extraInfo);
    assertTrue("tool_config should exist", extraInfo.has(TOOL_CONFIG));

    JSONObject toolConfig = extraInfo.getJSONObject(TOOL_CONFIG);
    assertTrue("App config should exist", toolConfig.has(APP_123));

    JSONObject appConfig = toolConfig.getJSONObject(APP_123);
    assertTrue("Tool 1 config should exist", appConfig.has(TOOL_1));
    assertTrue("Tool 2 config should exist", appConfig.has(TOOL_2));

    // Verify tool 1
    JSONObject tool1Config = appConfig.getJSONObject(TOOL_1);
    verifyToolModelAndProvider(tool1Config, "gpt-4-turbo", OPENAI);

    // Verify tool 2
    JSONObject tool2Config = appConfig.getJSONObject(TOOL_2);
    verifyToolModelAndProvider(tool2Config, "gemini-2.5-pro", "gemini");
  }

  /**
   * Test exec with model string without provider (should default to openai).
   */
  @Test
  public void testExecWithModelWithoutProvider() throws Exception {
    // Setup app
    setupBasicApp("app-456", TEST_APP);

    // Setup tool with model only (no provider)
    setupTool(mockTool1, mockAppTool1, TOOL_1, "gpt-4o-mini");

    setupAppToolsList(mockAppTool1);

    // Create JSON object and execute
    JSONObject json = createJsonWithExtraInfo();
    toolModelsConfig.exec(mockApp, json);

    // Verify
    JSONObject tool1Config = getToolConfigFromJson(json, "app-456", TOOL_1);
    verifyToolModelAndProvider(tool1Config, "gpt-4o-mini", OPENAI);
  }

  /**
   * Test exec with app that has tools without model (should be skipped).
   */
  @Test
  public void testExecWithToolsWithoutModel() throws Exception {
    // Setup app
    setupBasicApp("app-789", TEST_APP);

    // Setup tool without model
    setupTool(mockTool1, mockAppTool1, TOOL_1, null);

    setupAppToolsList(mockAppTool1);

    // Create JSON object and execute
    JSONObject json = createJsonWithExtraInfo();
    toolModelsConfig.exec(mockApp, json);

    // Verify - app should exist but no tool config
    JSONObject appConfig = json.getJSONObject(EXTRA_INFO)
        .getJSONObject(TOOL_CONFIG)
        .getJSONObject("app-789");
    assertFalse("Tool without model should not be included", appConfig.has(TOOL_1));
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
    when(mockTeamMemberApp.getId()).thenReturn(TEAM_APP);
    when(mockTeamMemberApp.getName()).thenReturn("Team App");

    // Setup tool for team member app
    setupTool(mockTool1, mockAppTool1, TOOL_1, "anthropic/claude-3-5-sonnet");

    List<CopilotAppTool> teamAppTools = new ArrayList<>();
    teamAppTools.add(mockAppTool1);
    when(mockTeamMemberApp.getETCOPAppToolList()).thenReturn(teamAppTools);

    // Setup team member
    when(mockTeamMember.getMember()).thenReturn(mockTeamMemberApp);
    List<TeamMember> teamMembers = new ArrayList<>();
    teamMembers.add(mockTeamMember);
    when(mockApp.getETCOPTeamMemberList()).thenReturn(teamMembers);

    // Create JSON object and execute
    JSONObject json = createJsonWithExtraInfo();
    toolModelsConfig.exec(mockApp, json);

    // Verify
    JSONObject toolConfig = json.getJSONObject(EXTRA_INFO).getJSONObject(TOOL_CONFIG);
    assertTrue("Main app should exist", toolConfig.has("main-app"));
    assertTrue("Team member app should exist", toolConfig.has(TEAM_APP));

    JSONObject teamAppConfig = toolConfig.getJSONObject(TEAM_APP);
    assertTrue("Team app tool should exist", teamAppConfig.has(TOOL_1));

    JSONObject tool1Config = teamAppConfig.getJSONObject(TOOL_1);
    verifyToolModelAndProvider(tool1Config, "claude-3-5-sonnet", "anthropic");
  }

  /**
   * Test exec with empty tool list - should create empty app config.
   */
  @Test
  public void testExecWithEmptyToolList() throws Exception {
    // Setup app with no tools
    when(mockApp.getId()).thenReturn(EMPTY_APP);
    when(mockApp.getName()).thenReturn("Empty App");
    when(mockApp.getETCOPTeamMemberList()).thenReturn(new ArrayList<>());
    when(mockApp.getETCOPAppToolList()).thenReturn(new ArrayList<>());

    // Create JSON object and execute
    JSONObject json = createJsonWithExtraInfo();
    toolModelsConfig.exec(mockApp, json);

    // Verify - app should exist but be empty
    JSONObject toolConfig = json.getJSONObject(EXTRA_INFO).getJSONObject(TOOL_CONFIG);
    assertTrue("App should exist", toolConfig.has(EMPTY_APP));
    JSONObject appConfig = toolConfig.getJSONObject(EMPTY_APP);
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

    // Create JSON object and execute
    JSONObject json = createJsonWithExtraInfo();

    // Verify that OBException is thrown when an exception occurs
    assertThrows(OBException.class, () -> toolModelsConfig.exec(mockApp, json));
  }

  /**
   * Test exec with model string containing multiple slashes (edge case).
   */
  @Test
  public void testExecWithModelStringMultipleSlashes() throws Exception {
    // Setup app
    setupBasicApp("app-multi-slash", TEST_APP);

    // Setup tool with multiple slashes (should split on first slash only)
    setupTool(mockTool1, mockAppTool1, TOOL_1, "openai/gpt-4/turbo");

    setupAppToolsList(mockAppTool1);

    // Create JSON object and execute
    JSONObject json = createJsonWithExtraInfo();
    toolModelsConfig.exec(mockApp, json);

    // Verify - should split on first slash, keeping rest as model name
    JSONObject tool1Config = getToolConfigFromJson(json, "app-multi-slash", TOOL_1);
    verifyToolModelAndProvider(tool1Config, "gpt-4/turbo", OPENAI);
  }
}
