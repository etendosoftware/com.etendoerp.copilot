package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.data.TeamMember;

/**
 * Tests for ToolModelsConfig hook.
 */
@RunWith(MockitoJUnitRunner.class)
public class ToolModelsConfigNewTest {

  private ToolModelsConfig hook;

  @Before
  public void setUp() {
    hook = new ToolModelsConfig();
  }

  // --- typeCheck tests ---

  @Test
  public void testTypeCheckAlwaysTrue() {
    assertTrue(hook.typeCheck(null));
    assertTrue(hook.typeCheck(mock(CopilotApp.class)));
  }

  // --- exec tests ---

  @Test
  public void testExecWithToolHavingProviderModel() throws Exception {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("APP1");
    when(app.getName()).thenReturn("TestApp");
    when(app.getETCOPTeamMemberList()).thenReturn(Collections.emptyList());

    CopilotTool tool = mock(CopilotTool.class);
    when(tool.getId()).thenReturn("TOOL1");

    CopilotAppTool appTool = mock(CopilotAppTool.class);
    when(appTool.getModel()).thenReturn("openai/gpt-4-turbo");
    when(appTool.getCopilotTool()).thenReturn(tool);

    List<CopilotAppTool> toolList = new ArrayList<>();
    toolList.add(appTool);
    when(app.getETCOPAppToolList()).thenReturn(toolList);

    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    hook.exec(app, json);

    JSONObject toolConfig = json.getJSONObject("extra_info").getJSONObject("tool_config");
    assertNotNull(toolConfig);
    assertTrue(toolConfig.has("APP1"));

    JSONObject appConfig = toolConfig.getJSONObject("APP1");
    assertTrue(appConfig.has("TOOL1"));

    JSONObject toolCfg = appConfig.getJSONObject("TOOL1");
    assertEquals("openai", toolCfg.getString("provider"));
    assertEquals("gpt-4-turbo", toolCfg.getString("model"));
  }

  @Test
  public void testExecWithToolWithoutProvider() throws Exception {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("APP2");
    when(app.getName()).thenReturn("TestApp2");
    when(app.getETCOPTeamMemberList()).thenReturn(Collections.emptyList());

    CopilotTool tool = mock(CopilotTool.class);
    when(tool.getId()).thenReturn("TOOL2");

    CopilotAppTool appTool = mock(CopilotAppTool.class);
    when(appTool.getModel()).thenReturn("gpt-4");
    when(appTool.getCopilotTool()).thenReturn(tool);

    List<CopilotAppTool> toolList = new ArrayList<>();
    toolList.add(appTool);
    when(app.getETCOPAppToolList()).thenReturn(toolList);

    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    hook.exec(app, json);

    JSONObject toolCfg = json.getJSONObject("extra_info")
        .getJSONObject("tool_config")
        .getJSONObject("APP2")
        .getJSONObject("TOOL2");
    assertEquals("openai", toolCfg.getString("provider"));
    assertEquals("gpt-4", toolCfg.getString("model"));
  }

  @Test
  public void testExecWithNullModel() throws Exception {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("APP3");
    when(app.getName()).thenReturn("TestApp3");
    when(app.getETCOPTeamMemberList()).thenReturn(Collections.emptyList());

    CopilotAppTool appTool = mock(CopilotAppTool.class);
    when(appTool.getModel()).thenReturn(null);

    List<CopilotAppTool> toolList = new ArrayList<>();
    toolList.add(appTool);
    when(app.getETCOPAppToolList()).thenReturn(toolList);

    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    hook.exec(app, json);

    JSONObject appConfig = json.getJSONObject("extra_info")
        .getJSONObject("tool_config")
        .getJSONObject("APP3");
    assertEquals(0, appConfig.length());
  }

  @Test
  public void testExecWithBlankModel() throws Exception {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("APP4");
    when(app.getName()).thenReturn("TestApp4");
    when(app.getETCOPTeamMemberList()).thenReturn(Collections.emptyList());

    CopilotAppTool appTool = mock(CopilotAppTool.class);
    when(appTool.getModel()).thenReturn("  ");
    when(appTool.getCopilotTool()).thenReturn(mock(CopilotTool.class));

    List<CopilotAppTool> toolList = new ArrayList<>();
    toolList.add(appTool);
    when(app.getETCOPAppToolList()).thenReturn(toolList);

    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    hook.exec(app, json);

    JSONObject appConfig = json.getJSONObject("extra_info")
        .getJSONObject("tool_config")
        .getJSONObject("APP4");
    assertEquals(0, appConfig.length());
  }

  @Test
  public void testExecWithTeamMembers() throws Exception {
    CopilotApp mainApp = mock(CopilotApp.class);
    when(mainApp.getId()).thenReturn("MAIN_APP");
    when(mainApp.getName()).thenReturn("MainApp");
    when(mainApp.getETCOPAppToolList()).thenReturn(Collections.emptyList());

    CopilotApp memberApp = mock(CopilotApp.class);
    when(memberApp.getId()).thenReturn("MEMBER_APP");
    when(memberApp.getName()).thenReturn("MemberApp");

    CopilotTool memberTool = mock(CopilotTool.class);
    when(memberTool.getId()).thenReturn("MEMBER_TOOL");

    CopilotAppTool memberAppTool = mock(CopilotAppTool.class);
    when(memberAppTool.getModel()).thenReturn("gemini/gemini-2.5-pro");
    when(memberAppTool.getCopilotTool()).thenReturn(memberTool);

    List<CopilotAppTool> memberToolList = new ArrayList<>();
    memberToolList.add(memberAppTool);
    when(memberApp.getETCOPAppToolList()).thenReturn(memberToolList);

    TeamMember teamMember = mock(TeamMember.class);
    when(teamMember.getCopilotApp()).thenReturn(memberApp);

    List<TeamMember> teamMembers = new ArrayList<>();
    teamMembers.add(teamMember);
    when(mainApp.getETCOPTeamMemberList()).thenReturn(teamMembers);

    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    hook.exec(mainApp, json);

    JSONObject toolConfig = json.getJSONObject("extra_info").getJSONObject("tool_config");
    assertTrue(toolConfig.has("MAIN_APP"));
    assertTrue(toolConfig.has("MEMBER_APP"));

    JSONObject memberToolCfg = toolConfig.getJSONObject("MEMBER_APP").getJSONObject("MEMBER_TOOL");
    assertEquals("gemini", memberToolCfg.getString("provider"));
    assertEquals("gemini-2.5-pro", memberToolCfg.getString("model"));
  }

  @Test
  public void testExecWithMultipleSlashesInModel() throws Exception {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("APP5");
    when(app.getName()).thenReturn("TestApp5");
    when(app.getETCOPTeamMemberList()).thenReturn(Collections.emptyList());

    CopilotTool tool = mock(CopilotTool.class);
    when(tool.getId()).thenReturn("TOOL5");

    CopilotAppTool appTool = mock(CopilotAppTool.class);
    when(appTool.getModel()).thenReturn("openai/gpt-4/turbo");
    when(appTool.getCopilotTool()).thenReturn(tool);

    List<CopilotAppTool> toolList = new ArrayList<>();
    toolList.add(appTool);
    when(app.getETCOPAppToolList()).thenReturn(toolList);

    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    hook.exec(app, json);

    JSONObject toolCfg = json.getJSONObject("extra_info")
        .getJSONObject("tool_config")
        .getJSONObject("APP5")
        .getJSONObject("TOOL5");
    assertEquals("openai", toolCfg.getString("provider"));
    assertEquals("gpt-4/turbo", toolCfg.getString("model"));
  }

  @Test
  public void testExecWithNoTools() throws Exception {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getId()).thenReturn("APP6");
    when(app.getName()).thenReturn("TestApp6");
    when(app.getETCOPTeamMemberList()).thenReturn(Collections.emptyList());
    when(app.getETCOPAppToolList()).thenReturn(Collections.emptyList());

    JSONObject json = new JSONObject();
    json.put("extra_info", new JSONObject());

    hook.exec(app, json);

    JSONObject appConfig = json.getJSONObject("extra_info")
        .getJSONObject("tool_config")
        .getJSONObject("APP6");
    assertEquals(0, appConfig.length());
  }

  // --- getPriority test ---

  @Test
  public void testGetPriorityDefault() {
    assertEquals(100, hook.getPriority());
  }
}
