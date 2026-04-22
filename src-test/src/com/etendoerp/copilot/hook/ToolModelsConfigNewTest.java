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
package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotModel;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.data.TeamMember;

/**
 * Tests for ToolModelsConfig hook.
 */
@RunWith(MockitoJUnitRunner.class)
public class ToolModelsConfigNewTest {
  private static final String EXTRA_INFO = "extra_info";
  private static final String MEMBER_APP = "MEMBER_APP";
  private static final String MODEL = "model";
  private static final String OPENAI = "openai";
  private static final String PROVIDER = "provider";
  private static final String TOOL1 = "TOOL1";
  private static final String TOOL_CONFIG = "tool_config";


  private ToolModelsConfig hook;

  /** Set up. */
  @Before
  public void setUp() {
    hook = new ToolModelsConfig();
  }


  private CopilotModel createMockModel(String provider, String searchkey) {
    CopilotModel model = mock(CopilotModel.class);
    doReturn(provider).when(model).getProvider();
    doReturn(searchkey).when(model).getSearchkey();
    return model;
  }

  private CopilotApp createMockApp(String id, String name) {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn(id);
    when(app.getName()).thenReturn(name);
    when(app.getETCOPTeamMemberList()).thenReturn(Collections.emptyList());
    return app;
  }

  private CopilotAppTool createMockAppTool(String toolId, String provider, String searchKey) {
    CopilotTool tool = mock(CopilotTool.class);
    when(tool.getId()).thenReturn(toolId);

    CopilotModel model = createMockModel(provider, searchKey);
    CopilotAppTool appTool = mock(CopilotAppTool.class);
    doReturn(model).when(appTool).getModel();
    doReturn(tool).when(appTool).getCopilotTool();
    return appTool;
  }

  private void addToolsToApp(CopilotApp app, CopilotAppTool... appTools) {
    List<CopilotAppTool> toolList = new ArrayList<>();
    Collections.addAll(toolList, appTools);
    when(app.getETCOPAppToolList()).thenReturn(toolList);
  }

  private JSONObject createJsonWithExtraInfo() throws Exception {
    JSONObject json = new JSONObject();
    json.put(EXTRA_INFO, new JSONObject());
    return json;
  }

  // --- typeCheck tests ---

  /** Test type check always true. */
  @Test
  public void testTypeCheckAlwaysTrue() {
    assertTrue(hook.typeCheck(null));
    assertTrue(hook.typeCheck(mock(CopilotApp.class)));
  }

  // --- exec tests ---

  /**
   * Test exec with tool having provider model.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecWithToolHavingProviderModel() throws Exception {
    CopilotApp app = createMockApp("APP1", "TestApp");
    CopilotAppTool appTool = createMockAppTool(TOOL1, OPENAI, "gpt-4-turbo");
    addToolsToApp(app, appTool);

    JSONObject json = createJsonWithExtraInfo();

    hook.exec(app, json);

    JSONObject toolConfig = json.getJSONObject(EXTRA_INFO).getJSONObject(TOOL_CONFIG);
    assertNotNull(toolConfig);
    assertTrue(toolConfig.has("APP1"));

    JSONObject appConfig = toolConfig.getJSONObject("APP1");
    assertTrue(appConfig.has(TOOL1));

    JSONObject toolCfg = appConfig.getJSONObject(TOOL1);
    assertEquals(OPENAI, toolCfg.getString(PROVIDER));
    assertEquals("gpt-4-turbo", toolCfg.getString(MODEL));
  }

  /**
   * Test exec with tool without provider.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecWithToolWithoutProvider() throws Exception {
    CopilotApp app = createMockApp("APP2", "TestApp2");
    CopilotAppTool appTool = createMockAppTool("TOOL2", OPENAI, "gpt-4");
    addToolsToApp(app, appTool);

    JSONObject json = createJsonWithExtraInfo();

    hook.exec(app, json);

    JSONObject toolCfg = json.getJSONObject(EXTRA_INFO)
        .getJSONObject(TOOL_CONFIG)
        .getJSONObject("APP2")
        .getJSONObject("TOOL2");
    assertEquals(OPENAI, toolCfg.getString(PROVIDER));
    assertEquals("gpt-4", toolCfg.getString(MODEL));
  }

  /**
   * Test exec with null model.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecWithNullModel() throws Exception {
    CopilotApp app = createMockApp("APP3", "TestApp3");

    CopilotAppTool appTool = mock(CopilotAppTool.class);
    doReturn(null).when(appTool).getModel();
    addToolsToApp(app, appTool);

    JSONObject json = createJsonWithExtraInfo();

    hook.exec(app, json);

    JSONObject appConfig = json.getJSONObject(EXTRA_INFO)
        .getJSONObject(TOOL_CONFIG)
        .getJSONObject("APP3");
    assertEquals(0, appConfig.length());
  }

  /**
   * Test exec with blank model.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecWithBlankModel() throws Exception {
    CopilotApp app = createMockApp("APP4", "TestApp4");

    CopilotModel blankModel = createMockModel(null, null);
    CopilotAppTool appTool = mock(CopilotAppTool.class);
    doReturn(blankModel).when(appTool).getModel();
    doReturn(mock(CopilotTool.class)).when(appTool).getCopilotTool();
    addToolsToApp(app, appTool);

    JSONObject json = createJsonWithExtraInfo();

    hook.exec(app, json);

    JSONObject appConfig = json.getJSONObject(EXTRA_INFO)
        .getJSONObject(TOOL_CONFIG)
        .getJSONObject("APP4");
    assertEquals(0, appConfig.length());
  }

  /**
   * Test exec with team members.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecWithTeamMembers() throws Exception {
    CopilotApp mainApp = createMockApp("MAIN_APP", "MainApp");
    when(mainApp.getETCOPAppToolList()).thenReturn(Collections.emptyList());

    CopilotApp memberApp = mock(CopilotApp.class);
    when(memberApp.getId()).thenReturn(MEMBER_APP);
    when(memberApp.getName()).thenReturn("MemberApp");

    CopilotAppTool memberAppTool = createMockAppTool("MEMBER_TOOL", "gemini", "gemini-2.5-pro");
    addToolsToApp(memberApp, memberAppTool);

    TeamMember teamMember = mock(TeamMember.class);
    when(teamMember.getMember()).thenReturn(memberApp);

    List<TeamMember> teamMembers = new ArrayList<>();
    teamMembers.add(teamMember);
    when(mainApp.getETCOPTeamMemberList()).thenReturn(teamMembers);

    JSONObject json = createJsonWithExtraInfo();

    hook.exec(mainApp, json);

    JSONObject toolConfig = json.getJSONObject(EXTRA_INFO).getJSONObject(TOOL_CONFIG);
    assertTrue(toolConfig.has("MAIN_APP"));
    assertTrue(toolConfig.has(MEMBER_APP));

    JSONObject memberToolCfg = toolConfig.getJSONObject(MEMBER_APP).getJSONObject("MEMBER_TOOL");
    assertEquals("gemini", memberToolCfg.getString(PROVIDER));
    assertEquals("gemini-2.5-pro", memberToolCfg.getString(MODEL));
  }

  /**
   * Test exec with multiple slashes in model.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecWithMultipleSlashesInModel() throws Exception {
    CopilotApp app = createMockApp("APP5", "TestApp5");
    CopilotAppTool appTool = createMockAppTool("TOOL5", OPENAI, "gpt-4/turbo");
    addToolsToApp(app, appTool);

    JSONObject json = createJsonWithExtraInfo();

    hook.exec(app, json);

    JSONObject toolCfg = json.getJSONObject(EXTRA_INFO)
        .getJSONObject(TOOL_CONFIG)
        .getJSONObject("APP5")
        .getJSONObject("TOOL5");
    assertEquals(OPENAI, toolCfg.getString(PROVIDER));
    assertEquals("gpt-4/turbo", toolCfg.getString(MODEL));
  }

  /**
   * Test exec with no tools.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecWithNoTools() throws Exception {
    CopilotApp app = createMockApp("APP6", "TestApp6");
    when(app.getETCOPAppToolList()).thenReturn(Collections.emptyList());

    JSONObject json = createJsonWithExtraInfo();

    hook.exec(app, json);

    JSONObject appConfig = json.getJSONObject(EXTRA_INFO)
        .getJSONObject(TOOL_CONFIG)
        .getJSONObject("APP6");
    assertEquals(0, appConfig.length());
  }

  // --- getPriority test ---

  /** Test get priority default. */
  @Test
  public void testGetPriorityDefault() {
    assertEquals(100, hook.getPriority());
  }
}
