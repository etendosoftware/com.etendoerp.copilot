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
package com.etendoerp.copilot.engine;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ServerToolRegistry;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;

/**
 * Unit tests for {@link CopilotEngineSelector#unsupportedReason(JSONObject, CopilotApp)}.
 * <p>
 * The Java engine must report capabilities that it cannot run so the selector falls back to
 * Python. Those capabilities include declared tools without Java implementations, CodeAct, and
 * agent types only the Python runtime understands; FLOW specs are generated per request in Java.
 */
public class CopilotEngineSelectorTest {

  private CopilotApp app;

  @Before
  public void setUp() {
    app = mock(CopilotApp.class);
    when(app.getETCOPAppSourceList()).thenReturn(Collections.emptyList());
  }

  /**
   * A plain langchain agent with nothing but a prompt is the case the Java engine exists for.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void plainLangchainAgentIsSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);

    assertNull(CopilotEngineSelector.unsupportedReason(request, app));
  }

  /**
   * Multimodel agents share the langchain contract, so they are supported too.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void multimodelAgentIsSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_MULTIMODEL);

    assertNull(CopilotEngineSelector.unsupportedReason(request, app));
  }

  /**
   * Langgraph teams are supported when their members use Java-backed model agents.
   */
  @Test
  public void langgraphTeamWithModelMemberIsSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGGRAPH);
    request.put("assistants", new JSONArray().put(new JSONObject()
        .put("name", "Researcher").put("type", CopilotConstants.APP_TYPE_LANGCHAIN)));

    assertNull(CopilotEngineSelector.unsupportedReason(request, app));
  }
  @Test
  public void flowSpecsAreSupportedByRequestScopedTools() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put("specs", new JSONArray().put(new JSONObject().put("name", "sample")
        .put("type", "FLOW").put("spec", "{\"paths\":{}}")));

    assertNull(CopilotEngineSelector.unsupportedReason(request, app));
  }

  @Test
  public void openaiAssistantTeamMemberRemainsUnsupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGGRAPH);
    request.put("assistants", new JSONArray().put(new JSONObject()
        .put("name", "Remote").put("type", CopilotConstants.APP_TYPE_OPENAI)));

    assertTrue(CopilotEngineSelector.unsupportedReason(request, app).contains("openai-assistant"));
  }

  /**
   * The OpenAI Assistants flow is not implemented in Java.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void openaiAssistantIsNotSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_OPENAI);

    assertNotNull(CopilotEngineSelector.unsupportedReason(request, app));
  }

  /**
   * A malformed tool descriptor cannot be resolved to a Java implementation.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void malformedToolDescriptorIsNotSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put(RestServiceUtil.PROP_TOOLS, new JSONArray().put(new JSONObject().put("type", "function")));

    String reason = CopilotEngineSelector.unsupportedReason(request, app);

    assertNotNull(reason);
    assertTrue(reason.contains("tools"));
  }

  @Test
  public void missingJavaToolIsNamedInTheFallbackReason() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put(RestServiceUtil.PROP_TOOLS, new JSONArray().put(toolDescriptor("MissingJavaTool")));

    String reason = CopilotEngineSelector.unsupportedReason(request, app, new ServerToolRegistry(name -> null));

    assertNotNull(reason);
    assertTrue(reason.contains("MissingJavaTool"));
  }

  @Test
  public void registeredJavaToolDoesNotBlockTheJavaEngine() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put(RestServiceUtil.PROP_TOOLS, new JSONArray().put(toolDescriptor("AvailableJavaTool")));
    ServerTool implementation = new ServerTool() {
      @Override
      public String name() {
        return "AvailableJavaTool";
      }

      @Override
      public dev.langchain4j.agent.tool.ToolSpecification specification() {
        return dev.langchain4j.agent.tool.ToolSpecification.builder().name(name()).build();
      }

      @Override
      public String execute(JSONObject arguments, com.etendoerp.copilot.engine.tool.ToolExecutionContext context) {
        return "ok";
      }
    };

    assertNull(CopilotEngineSelector.unsupportedReason(request, app,
        new ServerToolRegistry(name -> implementation)));
  }


  /**
   * An empty tools array is what a toolless agent actually sends, and must not block the engine.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void emptyToolsArrayIsSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put(RestServiceUtil.PROP_TOOLS, new JSONArray());

    assertNull(CopilotEngineSelector.unsupportedReason(request, app));
  }

  /** MCP servers are request-scoped Java client tools. */
  @Test
  public void agentWithMcpServersIsSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put("mcp_servers", new JSONArray().put(new JSONObject().put("name", "files")));

    assertNull(CopilotEngineSelector.unsupportedReason(request, app));
  }

  @Test
  public void teamAndMemberMcpServersAreSupported() throws JSONException {
    JSONObject member = new JSONObject().put("name", "researcher")
        .put("type", CopilotConstants.APP_TYPE_LANGCHAIN)
        .put("mcp_servers", new JSONArray().put(new JSONObject().put("name", "member-files")));
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGGRAPH)
        .put("assistants", new JSONArray().put(member))
        .put("mcp_servers", new JSONArray().put(new JSONObject().put("name", "supervisor-files")));

    assertNull(CopilotEngineSelector.unsupportedReason(request, app));
  }

  /**
   * CodeAct agents run generated code in the Python runtime only, so the Java engine must refuse them.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void agentWithCodeExecutionIsNotSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put(RestServiceUtil.PROP_CODE_EXECUTION, true);

    String reason = CopilotEngineSelector.unsupportedReason(request, app);

    assertNotNull(reason);
    assertTrue(reason.contains("CodeAct"));
  }

  /**
   * An agent with code execution switched off is a plain agent and must stay on the Java engine.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void agentWithCodeExecutionDisabledIsSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put(RestServiceUtil.PROP_CODE_EXECUTION, false);

    assertNull(CopilotEngineSelector.unsupportedReason(request, app));
  }

  /**
   * Build the minimal payload {@code buildRequestJson} would produce for a chat-only agent.
   *
   * @param appType
   *     the agent type to declare
   * @return the payload
   * @throws JSONException
   *     when the payload cannot be built
   */
  private JSONObject baseRequest(String appType) throws JSONException {
    JSONObject request = new JSONObject();
    request.put(RestServiceUtil.PROP_TYPE, appType);
    request.put(RestServiceUtil.PROP_QUESTION, "Hello");
    return request;
  }
  private JSONObject toolDescriptor(String name) throws JSONException {
    return new JSONObject().put("type", "function")
        .put("function", new JSONObject().put("name", name));
  }

}
