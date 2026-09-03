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
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;

/**
 * Unit tests for {@link CopilotEngineSelector#unsupportedReason(JSONObject, CopilotApp)}.
 * <p>
 * The rule under test is the guard that keeps the Java engine from silently dropping a
 * capability it does not implement yet: whenever the payload declares tools, specs, MCP servers,
 * a knowledge base or an agent type that only the Python runtime understands, the request must be
 * reported as unsupported so the selector falls back to Python.
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
   * Team graphs only exist in the Python runtime.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void langgraphAgentIsNotSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGGRAPH);

    String reason = CopilotEngineSelector.unsupportedReason(request, app);

    assertNotNull(reason);
    assertTrue(reason.contains(CopilotConstants.APP_TYPE_LANGGRAPH));
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
   * An agent that declares tools must not be served by an engine that ignores them.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void agentWithToolsIsNotSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put(RestServiceUtil.PROP_TOOLS, new JSONArray().put(new JSONObject().put("type", "function")));

    String reason = CopilotEngineSelector.unsupportedReason(request, app);

    assertNotNull(reason);
    assertTrue(reason.contains("tools"));
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

  /**
   * MCP servers need a client the Java engine does not wire yet.
   *
   * @throws JSONException
   *     when the payload cannot be built
   */
  @Test
  public void agentWithMcpServersIsNotSupported() throws JSONException {
    JSONObject request = baseRequest(CopilotConstants.APP_TYPE_LANGCHAIN);
    request.put("mcp_servers", new JSONArray().put(new JSONObject().put("name", "files")));

    String reason = CopilotEngineSelector.unsupportedReason(request, app);

    assertNotNull(reason);
    assertTrue(reason.contains("MCP"));
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
}
