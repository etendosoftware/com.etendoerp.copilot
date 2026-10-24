/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License (the "License").
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L. All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.copilot.engine.tool.ServerTool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;

/** Parses per-question FLOW documents into immutable, operation-backed tool instances. */
public final class OpenApiToolGenerator {
  private static final Logger LOG = LogManager.getLogger(OpenApiToolGenerator.class);
  private static final String RX_PREFIX = "/sws/com.etendoerp.etendorx.datasource";
  private static final Set<String> METHODS = Set.of("get", "post", "put", "delete");
  private static final Pattern SAFE_TOOL_NAME = Pattern.compile("[^a-zA-Z0-9_-]+");

  private final OpenApiDataService dataService;

  public OpenApiToolGenerator() {
    this(new CoreOpenApiDataService());
  }

  public OpenApiToolGenerator(OpenApiDataService dataService) {
    this.dataService = dataService;
  }

  /** Creates all supported OpenAPI operation tools from request specs. */
  public List<ServerTool> tools(JSONArray specs) {
    if (specs == null) return Collections.emptyList();
    List<ServerTool> result = new ArrayList<>();
    for (int i = 0; i < specs.length(); i++) {
      JSONObject item = specs.optJSONObject(i);
      if (item == null) continue;
      if (!"FLOW".equalsIgnoreCase(item.optString("type", ""))) continue;
      JSONObject spec;
      try {
        Object raw = item.opt("spec");
        spec = raw instanceof JSONObject ? (JSONObject) raw : new JSONObject(String.valueOf(raw));
      } catch (JSONException | RuntimeException e) {
        LOG.warn("Ignoring invalid FLOW OpenAPI document '{}'", item.optString("name"));
        continue;
      }
      for (OpenApiOperation operation : parse(spec)) {
        result.add(new OpenApiOperationTool(operation, dataService));
      }
    }
    return result;
  }

  /** Parses GET/POST/PUT/DELETE operations, matching the Python generator's supported methods. */
  public List<OpenApiOperation> parse(JSONObject spec) {
    List<OpenApiOperation> result = new ArrayList<>();
    JSONObject paths = spec == null ? null : spec.optJSONObject("paths");
    if (paths == null) return result;
    JSONObject servers = null;
    JSONArray serverList = spec.optJSONArray("servers");
    if (serverList != null && serverList.length() > 0) servers = serverList.optJSONObject(0);
    String server = servers == null ? "unknown_url" : servers.optString("url", "unknown_url");
    JSONArray pathNames = paths.names();
    for (int p = 0; pathNames != null && p < pathNames.length(); p++) {
      String path = pathNames.optString(p);
      JSONObject pathItem = paths.optJSONObject(path);
      if (pathItem == null) continue;
      JSONArray methodNames = pathItem.names();
      for (int m = 0; methodNames != null && m < methodNames.length(); m++) {
        String method = methodNames.optString(m).toLowerCase();
        if (!METHODS.contains(method)) continue;
        JSONObject operation = pathItem.optJSONObject(method);
        if (operation != null) result.add(parseOperation(method, server, path, operation));
      }
    }
    return result;
  }

  private OpenApiOperation parseOperation(String method, String server, String path, JSONObject op) {
    String baseName = op.optString("operationId", path.replace(RX_PREFIX, "").replace('/', '_')
        .replace('-', '_').replaceAll("^_+|_+$", ""));
    if (baseName.isEmpty()) baseName = "api_call";
    String toolName = sanitize(method.toUpperCase() + title(baseName));
    String summary = op.optString("summary", "");
    String description = op.optString("description", "");
    String toolDescription = summary.isEmpty() ? description
        : description.isEmpty() ? summary : summary + "\n" + description;
    Map<String, OpenApiOperation.Parameter> params = new LinkedHashMap<>();
    Map<String, Object> props = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    JSONArray parameters = op.optJSONArray("parameters");
    boolean rxGet = method.equals("get") && path.startsWith(RX_PREFIX);
    for (int i = 0; parameters != null && i < parameters.length(); i++) {
      JSONObject param = parameters.optJSONObject(i);
      if (param == null) continue;
      String originalName = param.optString("name", "");
      String location = param.optString("in", "query");
      String name = originalName;
      JSONObject schema = param.optJSONObject("schema");
      Map<String, Object> schemaMap = map(schema);
      if (rxGet && originalName.equals("_startRow")) name = "startRow";
      if (rxGet && originalName.equals("_endRow")) name = "endRow";
      if (originalName.startsWith("_") && !(rxGet && (originalName.equals("_startRow")
          || originalName.equals("_endRow")))) continue;
      if (props.containsKey(name)) continue;
      Map<String, Object> prop = new LinkedHashMap<>(schemaMap);
      prop.put("description", param.optString("description", ""));
      if (rxGet && originalName.equals("q")) {
        prop.put("description", String.valueOf(prop.get("description"))
            + "\nUse RSQL operators: ==, !=, =c=, =ic=, >, <, >=, <=, =is=null, =isnot=null, "
            + "=sw=, =ew=, =ins=, =nis=; combine conditions with AND (; or and) or OR (, or or).");
      }
      if (rxGet && originalName.equals("_startRow")) prop.putIfAbsent("description", "Starting row number for pagination (e.g., '0')");
      if (rxGet && originalName.equals("_endRow")) prop.putIfAbsent("description", "Ending row number for pagination (e.g., '10')");
      props.put(name, prop);
      boolean isRequired = param.optBoolean("required", false) || location.equals("path");
      if (isRequired) required.add(name);
      params.put(name, new OpenApiOperation.Parameter(location, schemaMap, isRequired));
    }
    JSONObject body = op.optJSONObject("requestBody");
    JSONObject content = body == null ? null : body.optJSONObject("content");
    JSONObject media = content == null ? null : content.optJSONObject("application/json");
    JSONObject bodySchema = media == null ? null : media.optJSONObject("schema");
    if ((method.equals("post") || method.equals("put")) && bodySchema != null
        && (bodySchema.has("properties") || bodySchema.has("oneOf") || bodySchema.optString("type").equals("array"))) {
      Map<String, Object> bodyProp = map(bodySchema);
      bodyProp.put("description", body.optString("description", "Request body for the tool."));
      props.put("body", bodyProp);
      params.put("body", new OpenApiOperation.Parameter("body", map(bodySchema), body.optBoolean("required", false)));
      if (body.optBoolean("required", false)) required.add("body");
    }
    if (!isEtendoServer(server)) {
      props.put("token", Map.of("type", "string", "description", "Authentication token for API access"));
    }
    Map<String, Object> argSchema = new LinkedHashMap<>();
    argSchema.put("type", "object");
    argSchema.put("properties", props);
    if (!required.isEmpty()) argSchema.put("required", required);
    Set<String> allowedFields = extensionFields(op.opt("x-etendo-fields"));
    String entity = classifyFixedEntity(method, server, path, op, params, allowedFields);
    String reason = entity == null ? loopbackReason(server, path, method)
        : "fixed entity " + entity + " and allowed fields are declared by x-etendo-entity/x-etendo-fields";
    LOG.info("OpenAPI operation {} {} {} classified {}: {}", method.toUpperCase(), path, toolName,
        entity == null ? "EtendoHttp loopback" : "in-process", reason);
    return new OpenApiOperation(toolName, toolDescription, method.toUpperCase(), server, path, entity,
        reason, params, argSchema, allowedFields);
  }
  private static String classifyFixedEntity(String method, String server, String path, JSONObject op,
      Map<String, OpenApiOperation.Parameter> parameters, Set<String> allowedFields) {
    if (!isEtendoServer(server) || !path.startsWith(RX_PREFIX) || path.contains("{")
        || path.contains("}") || allowedFields.isEmpty() || !method.equalsIgnoreCase("GET")) return null;
    String entity = op.optString("x-etendo-entity", "");
    if (entity.isEmpty()) return null;
    for (Map.Entry<String, OpenApiOperation.Parameter> entry : parameters.entrySet()) {
      OpenApiOperation.Parameter parameter = entry.getValue();
      if (parameter.location().equals("query") && !Set.of("id", "startRow", "endRow", "sortBy",
          "select", "selectedProperties", "criteria").contains(entry.getKey())) return null;
    }
    return entity;
  }

  private static Set<String> extensionFields(Object value) {
    Set<String> fields = new java.util.LinkedHashSet<>();
    if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) if (!array.optString(i).isEmpty()) fields.add(array.optString(i));
    } else if (value instanceof String) {
      for (String field : ((String) value).split(",")) if (!field.trim().isEmpty()) fields.add(field.trim());
    }
    return fields;
  }

  private static String loopbackReason(String server, String path, String method) {
    if (!path.startsWith(RX_PREFIX)) return "EtendoHttp loopback: non-datasource, external, workflow, or binary operation requires its original endpoint";
    if (path.contains("{") || path.contains("}")) return "EtendoHttp loopback: dynamic datasource/entity path is not trusted as a model-supplied entity identifier";
    if (method.equalsIgnoreCase("GET")) return "EtendoHttp loopback: RX datasource request/session, RSQL, selector, tab, or projection semantics are not proven equivalent";
    return "EtendoHttp loopback: core JSON add/update enters cross-org-reference admin mode and remove commits internally; caller-context transaction-safe writes are not available";
  }

  private static boolean isEtendoServer(String server) {
    if (server == null || server.equals("unknown_url")) return false;
    if (server.startsWith("/") || server.contains("localhost") || server.contains("127.0.0.1")) return true;
    try {
      return normalizeHost(server).equals(normalizeHost(
          com.etendoerp.copilot.util.CopilotUtils.getEtendoHostDocker()));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static String normalizeHost(String value) {
    return value == null ? "" : value.replaceAll("/+$", "").toLowerCase(java.util.Locale.ROOT);
  }

  private static String sanitize(String value) {
    String normalized = SAFE_TOOL_NAME.matcher(value).replaceAll("_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
    return normalized.isEmpty() ? "generated_tool" : normalized;
  }

  private static String title(String value) {
    StringBuilder out = new StringBuilder(value.length());
    boolean atWordStart = true;
    for (char c : value.toCharArray()) {
      if (!Character.isLetterOrDigit(c)) {
        out.append(c);
        atWordStart = true;
      } else {
        out.append(atWordStart ? Character.toUpperCase(c) : Character.toLowerCase(c));
        atWordStart = false;
      }
    }
    return out.toString();
  }

  private static Map<String, Object> map(JSONObject object) {
    if (object == null) return new LinkedHashMap<>();
    Map<String, Object> result = new LinkedHashMap<>();
    JSONArray names = object.names();
    for (int i = 0; names != null && i < names.length(); i++) {
      String key = names.optString(i);
      Object value = object.opt(key);
      if (value instanceof JSONObject) value = map((JSONObject) value);
      else if (value instanceof JSONArray) value = list((JSONArray) value);
      result.put(key, value);
    }
    return result;
  }
  private static List<Object> list(JSONArray array) {
    List<Object> values = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      Object value = array.opt(i);
      if (value instanceof JSONObject) values.add(map((JSONObject) value));
      else if (value instanceof JSONArray) values.add(list((JSONArray) value));
      else values.add(value);
    }
    return values;
  }
}
