/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;

public class McpClientToolLoaderTest {
  @Test
  public void loadsEnabledServersMapsCollidingNamesAndClosesClients() throws Exception {
    McpClient client = mock(McpClient.class);
    when(client.listTools()).thenReturn(List.of(ToolSpecification.builder().name("search")
        .description("Find records").build()));
    McpClientToolLoader loader = new McpClientToolLoader((name, config, transport, timeout) -> client);
    JSONArray servers = new JSONArray()
        .put(new JSONObject().put("name", "external").put("transport", "http").put("url", "https://mcp"));

    McpClientToolLoader.LoadedTools tools = loader.load(servers, Collections.singleton("search"));
    assertTrue(tools.hasTool("external__search"));
    assertEquals("external__search", tools.specifications().get(0).name());
    tools.close();
    verify(client).close();
  }

  @Test
  public void ignoresDisabledEntriesAndKeepsUniqueToolName() throws Exception {
    McpClient client = mock(McpClient.class);
    when(client.listTools()).thenReturn(List.of(ToolSpecification.builder().name("weather").build()));
    McpClientToolLoader loader = new McpClientToolLoader((name, config, transport, timeout) -> client);
    JSONArray servers = new JSONArray()
        .put(new JSONObject().put("name", "disabled").put("disabled", true))
        .put(new JSONObject().put("name", "weather-server").put("transport", "http").put("url", "https://mcp"));

    McpClientToolLoader.LoadedTools tools = loader.load(servers, Collections.emptySet());
    assertTrue(tools.hasTool("weather"));
    assertFalse(tools.hasTool("weather-server__weather"));
    tools.close();
    verify(client).close();
  }

  @Test
  public void returnsNoToolsWhenEveryServerIsDisabled() throws Exception {
    McpClientToolLoader.ClientFactory factory = mock(McpClientToolLoader.ClientFactory.class);
    McpClientToolLoader loader = new McpClientToolLoader(factory);
    JSONArray servers = new JSONArray().put(new JSONObject().put("name", "disabled").put("disabled", true));

    assertFalse(loader.load(servers, Collections.emptySet()).hasTools());
    verify(factory, never()).create(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(JSONObject.class), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any());
  }
  @Test
  public void rejectsStdioWorkingDirectoryInsteadOfIgnoringIt() throws Exception {
    McpClientToolLoader.ClientFactory factory = mock(McpClientToolLoader.ClientFactory.class);
    McpClientToolLoader loader = new McpClientToolLoader(factory);
    JSONArray servers = new JSONArray().put(new JSONObject().put("name", "stdio")
        .put("command", "node").put("cwd", "/tmp"));

    assertFalse(loader.load(servers, Collections.emptySet()).hasTools());
    verify(factory, never()).create(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(JSONObject.class), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any());
  }

}
