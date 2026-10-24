/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License").
 *************************************************************************
 */
package com.etendoerp.copilot.engine.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;

/** Opens request-scoped LangChain4j MCP clients and adapts their tools to the manual engine loop. */
public final class McpClientToolLoader {
  public static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(45);
  private static final Logger LOG = LogManager.getLogger(McpClientToolLoader.class);
  private final ClientFactory clientFactory;

  public McpClientToolLoader() {
    this(null);
  }

  McpClientToolLoader(ClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  public LoadedTools load(JSONArray rawServers, Set<String> reservedNames) {
    if (rawServers == null || rawServers.length() == 0) {
      return LoadedTools.empty();
    }
    List<McpClient> clients = new ArrayList<>();
    List<RemoteTool> tools = new ArrayList<>();
    try {
      List<ServerConfig> configs = normalize(rawServers);
      long deadline = System.nanoTime() + DISCOVERY_TIMEOUT.toNanos();
      for (ServerConfig config : configs) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
          throw new TimeoutException("MCP tool discovery exceeded 45 seconds.");
        }
        Duration configuredTimeout = serverTimeout(config);
        Duration timeout = Duration.ofNanos(Math.min(configuredTimeout.toNanos(), remainingNanos));
        McpClient client = clientFactory == null ? createClient(config, timeout)
            : clientFactory.create(config.name, config.raw, config.transport, timeout);
        clients.add(client);
        List<ToolSpecification> listed = listTools(client, deadline - System.nanoTime());
        for (ToolSpecification spec : listed) {
          tools.add(new RemoteTool(config.name, spec.name(), spec, client));
        }
      }
      Set<String> names = new HashSet<>(reservedNames == null ? Collections.emptySet() : reservedNames);
      Map<String, RemoteTool> byName = new HashMap<>();
      List<ToolSpecification> specifications = new ArrayList<>();
      for (RemoteTool remote : tools) {
        String publicName = remote.remoteName;
        if (names.contains(publicName)) {
          publicName = remote.serverName + "__" + remote.remoteName;
        }
        if (!names.add(publicName)) {
          closeAll(clients);
          return LoadedTools.empty();
        }
        remote.publicName = publicName;
        byName.put(publicName, remote);
        specifications.add(remote.specification.toBuilder().name(publicName).build());
      }
      return new LoadedTools(clients, specifications, byName);
    } catch (Exception e) {
      closeAll(clients);
      LOG.warn("MCP tool discovery failed; no tools from this request are available ({})",
          e.getClass().getSimpleName());
      return LoadedTools.empty();
    }
  }

  private List<ToolSpecification> listTools(McpClient client, long timeoutNanos) throws Exception {
    if (timeoutNanos <= 0) {
      throw new TimeoutException("MCP tool discovery exceeded 45 seconds.");
    }
    FutureTask<List<ToolSpecification>> task = new FutureTask<>(client::listTools);
    Thread worker = new Thread(task, "copilot-mcp-discovery");
    worker.setDaemon(true);
    worker.start();
    try {
      return task.get(timeoutNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      task.cancel(true);
      throw e;
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw new IllegalStateException("MCP tool discovery failed.", cause);
    }
  }

  private List<ServerConfig> normalize(JSONArray raw) throws JSONException {
    List<ServerConfig> servers = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (int i = 0; i < raw.length(); i++) {
      JSONObject item = raw.optJSONObject(i);
      if (item == null || item.optBoolean("disabled", false)) {
        continue;
      }
      String name = item.optString("name", "server_" + servers.size());
      if (StringUtils.isBlank(name) || !names.add(name)) {
        throw new IllegalArgumentException("MCP server names must be non-empty and unique.");
      }
      String transport = item.optString("transport", "stdio").toLowerCase();
      if ("stdio".equals(transport) && StringUtils.isNotBlank(item.optString("cwd", null))) {
        throw new IllegalArgumentException("MCP stdio working directories are unsupported.");
      }
      if (item.has("auth") && !item.isNull("auth")) {
        throw new IllegalArgumentException("MCP auth objects are unsupported; use explicit HTTP headers.");
      }
      servers.add(new ServerConfig(name, transport, item));
    }
    return servers;
  }

  private McpClient createClient(ServerConfig config, Duration timeout) throws JSONException {
    return DefaultMcpClient.builder().key(config.name)
        .transport(transport(config, timeout)).initializationTimeout(timeout)
        .protocolDetectionTimeout(timeout).toolExecutionTimeout(timeout).build();
  }

  interface ClientFactory {
    McpClient create(String name, JSONObject config, String transport, Duration timeout);
  }

  private Duration serverTimeout(ServerConfig config) {
    int timeoutMs = config.raw.optInt("timeoutMs", -1);
    if (timeoutMs < 0 && config.raw.has("timeout")) {
      timeoutMs = config.raw.optInt("timeout", 45) * 1000;
    }
    return timeoutMs > 0 ? Duration.ofMillis(timeoutMs) : DISCOVERY_TIMEOUT;
  }

  private McpTransport transport(ServerConfig config, Duration timeout) throws JSONException {
    JSONObject json = config.raw;
    if ("stdio".equals(config.transport)) {
      String command = json.optString("command", null);
      JSONArray argsJson = json.optJSONArray("args");
      if (StringUtils.isBlank(command)) {
        throw new IllegalArgumentException("MCP stdio server requires a command.");
      }
      List<String> commandLine = new ArrayList<>();
      commandLine.add(command);
      if (argsJson != null) {
        for (int i = 0; i < argsJson.length(); i++) {
          commandLine.add(argsJson.getString(i));
        }
      }
      StdioMcpTransport.Builder builder = StdioMcpTransport.builder().command(commandLine);
      JSONObject envJson = json.optJSONObject("env");
      if (envJson != null) {
        Map<String, String> env = new HashMap<>();
        Iterator<String> keys = envJson.keys();
        while (keys.hasNext()) {
          String key = keys.next();
          env.put(key, envJson.getString(key));
        }
        builder.environment(env);
      }
      return builder.build();
    }
    if ("http".equals(config.transport) || "streamable_http".equals(config.transport)) {
      String url = json.optString("url", null);
      if (StringUtils.isBlank(url)) {
        throw new IllegalArgumentException("MCP HTTP server requires a URL.");
      }
      StreamableHttpMcpTransport.Builder builder = StreamableHttpMcpTransport.builder()
          .url(url).timeout(timeout);
      JSONObject headerJson = json.optJSONObject("headers");
      if (headerJson != null) {
        Map<String, String> headers = new HashMap<>();
        Iterator<String> keys = headerJson.keys();
        while (keys.hasNext()) {
          String key = keys.next();
          headers.put(key, headerJson.getString(key));
        }
        builder.customHeaders(headers);
      }
      return builder.build();
    }
    throw new IllegalArgumentException("Unsupported MCP transport: " + config.transport);
  }

  private static void closeAll(List<McpClient> clients) {
    for (McpClient client : clients) {
      try {
        client.close();
      } catch (Exception e) {
        LOG.debug("Unable to close MCP client ({})", e.getClass().getSimpleName());
      }
    }
  }

  private static final class ServerConfig {
    private final String name;
    private final String transport;
    private final JSONObject raw;

    private ServerConfig(String name, String transport, JSONObject raw) {
      this.name = name;
      this.transport = transport;
      this.raw = raw;
    }
  }

  private static final class RemoteTool {
    private final String serverName;
    private final String remoteName;
    private final ToolSpecification specification;
    private final McpClient client;
    private String publicName;

    private RemoteTool(String serverName, String remoteName, ToolSpecification specification,
        McpClient client) {
      this.serverName = serverName;
      this.remoteName = remoteName;
      this.specification = specification;
      this.client = client;
    }
  }

  /** Tools and clients exist only for the lifetime of the current question. */
  public static final class LoadedTools implements AutoCloseable {
    private final List<McpClient> clients;
    private final List<ToolSpecification> specifications;
    private final Map<String, RemoteTool> tools;

    private LoadedTools(List<McpClient> clients, List<ToolSpecification> specifications,
        Map<String, RemoteTool> tools) {
      this.clients = clients;
      this.specifications = Collections.unmodifiableList(specifications);
      this.tools = Collections.unmodifiableMap(tools);
    }

    private static LoadedTools empty() {
      return new LoadedTools(Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    public boolean hasTools() {
      return !tools.isEmpty();
    }

    public List<ToolSpecification> specifications() {
      return specifications;
    }

    public boolean hasTool(String name) {
      return tools.containsKey(name);
    }

    public String execute(String name, String arguments) {
      RemoteTool tool = tools.get(name);
      if (tool == null) {
        return "MCP tool '" + name + "' is not available.";
      }
      try {
        ToolExecutionRequest request = ToolExecutionRequest.builder().name(tool.remoteName)
            .arguments(arguments).build();
        var result = tool.client.executeTool(request);
        return result.resultText();
      } catch (Exception e) {
        return "MCP tool '" + name + "' failed during execution.";
      }
    }

    @Override
    public void close() {
      closeAll(clients);
    }
  }
}
