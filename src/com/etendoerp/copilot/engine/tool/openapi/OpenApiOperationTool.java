/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License (the "License").
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L. All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.JsonConstants;

import com.etendoerp.copilot.engine.tool.ServerTool;
import com.etendoerp.copilot.engine.tool.ToolExecutionContext;
import com.etendoerp.copilot.engine.tool.http.EtendoHttp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;

/** A generic operation tool bound to one immutable request-scoped OpenAPI descriptor. */
final class OpenApiOperationTool implements ServerTool {
  private static final Logger LOG = LogManager.getLogger(OpenApiOperationTool.class);
  private static final Pattern BASE64_FILE = Pattern.compile("@BASE64_([^@]+)@");
  private static final Set<String> READ_ONLY = Set.of("id", "select", "selectedProperties", "startRow", "endRow", "sortBy", "count", "_operationType");
  private final OpenApiOperation operation;
  private final OpenApiDataService dataService;
  private final ToolSpecification specification;

  OpenApiOperationTool(OpenApiOperation operation, OpenApiDataService dataService) {
    this.operation = operation;
    this.dataService = dataService;
    this.specification = buildSpecification(operation);
  }

  @Override public String name() { return operation.name(); }
  @Override public ToolSpecification specification() { return specification; }
  @Override public boolean needsWebService() { return operation.route() == OpenApiOperation.Route.LOOPBACK; }
  @Override public Duration timeout() { return Duration.ofMinutes(2); }

  @Override public String execute(JSONObject arguments, ToolExecutionContext context) throws Exception {
    JSONObject args = arguments == null ? new JSONObject() : arguments;
    try {
      validate(args);
      return operation.route() == OpenApiOperation.Route.IN_PROCESS
          ? inProcess(args) : loopback(args, context);
    } catch (Exception e) {
      int status = e instanceof org.openbravo.base.exception.OBSecurityException ? 403
          : e instanceof IllegalArgumentException ? 400 : 500;
      String message = status == 500 ? "The operation could not be completed." : e.getMessage();
      return new JSONObject().put("status_code", status)
          .put("content", new JSONObject().put("error", message).toString()).toString();
    }
  }

  private String inProcess(JSONObject args) throws Exception {
    // Entity and field authority are descriptor-derived; no model argument can select an entity.
    Entity entity = ModelProvider.getInstance().getEntity(operation.entityName());
    if (entity == null) throw new IllegalArgumentException("The descriptor entity is not available");
    OBContext.getOBContext().getEntityAccessChecker().checkReadableAccess(entity);
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put(JsonConstants.ENTITYNAME, operation.entityName());
    for (String key : keys(args)) {
      if (key.equals("body") || key.equals("token")) continue;
      OpenApiOperation.Parameter parameter = operation.parameters().get(key);
      if (parameter == null) continue;
      String original = key.equals("startRow") ? "startRow" : key.equals("endRow") ? "endRow" : key;
      if (parameter.location().equals("path")) {
        if (key.equals("id")) parameters.put(JsonConstants.ID, args.optString(key));
      } else if (parameter.location().equals("query")) {
        if (args.isNull(key)) continue;
        if (original.equals("select") || original.equals("selectedProperties")) {
          String selected = args.optString(key);
          checkFields(entity, selected, operation.allowedFields(), false);
          parameters.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, selected);
        } else if (original.equals("sortBy")) {
          checkSort(entity, args.optString(key), operation.allowedFields());
          parameters.put(JsonConstants.SORTBY_PARAMETER, args.optString(key));
        } else if (original.equals("startRow")) {
          parameters.put(JsonConstants.STARTROW_PARAMETER, args.optString(key));
        } else if (original.equals("endRow")) {
          parameters.put(JsonConstants.ENDROW_PARAMETER, args.optString(key));
        } else if (original.equals("criteria")) {
          String criteria = args.optString(key);
          validateCriteria(criteria, entity, operation.allowedFields());
          parameters.put("criteria", criteria);
        } else if (READ_ONLY.contains(original)) {
        } else {
          throw new IllegalArgumentException("Unsupported in-process filter parameter");
        }
      }
    }
    String content = dataService.fetch(parameters);
    return new JSONObject().put("status_code", 200).put("content", content).toString();
  }

  private String loopback(JSONObject args, ToolExecutionContext context) throws Exception {
    String path = operation.path();
    Map<String, String> query = new LinkedHashMap<>();
    Object body = args.opt("body");
    for (Map.Entry<String, OpenApiOperation.Parameter> entry : operation.parameters().entrySet()) {
      String name = entry.getKey();
      OpenApiOperation.Parameter param = entry.getValue();
      if (!args.has(name) || args.isNull(name)) continue;
      String value = args.optString(name);
      if (param.location().equals("path")) path = path.replace("{" + name + "}", encode(value));
      if (param.location().equals("query")) {
        String actualName = name.equals("startRow") ? "_startRow" : name.equals("endRow") ? "_endRow" : name;
        query.put(actualName, value);
      }
    }
    StringBuilder url = new StringBuilder(operation.server()).append(path);
    boolean first = true;
    for (Map.Entry<String, String> entry : query.entrySet()) {
      url.append(first ? '?' : '&').append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
      first = false;
    }
    Map<String, String> headers = new LinkedHashMap<>();
    String token = args.optString("token", "");
    if (!token.isEmpty() && !token.equals("ETENDO_TOKEN")) {
      headers.put("Authorization", token.regionMatches(true, 0, "Bearer ", 0, 7)
          ? token : "Bearer " + token);
    }
    String bodyText = body == null ? null : replaceBase64(body.toString());
    if (bodyText != null) headers.put("Content-Type", "application/json; charset=utf-8");
    EtendoHttp.HttpResult result = EtendoHttp.call(context, operation.method(), url.toString(), headers, bodyText, timeout());
    return new JSONObject().put("status_code", result.status()).put("content", result.body()).toString();
  }

  private void validate(JSONObject args) {
    for (Map.Entry<String, OpenApiOperation.Parameter> entry : operation.parameters().entrySet()) {
      String key = entry.getKey();
      if (entry.getValue().required() && !args.has(key)) throw new IllegalArgumentException("Missing required argument: " + key);
      if (args.has(key) && !args.isNull(key) && !key.equals("body") && !key.equals("token")) {
        Object value = args.opt(key);
        Object expected = entry.getValue().schema().get("type");
        if (expected instanceof String && !matchesType((String) expected, value)) {
          throw new IllegalArgumentException("Invalid value type for argument: " + key);
        }
      }
    }
    Object required = operation.argumentSchema().get("required");
    if (required instanceof List<?>) {
      for (Object value : (List<?>) required) {
        String name = String.valueOf(value);
        if (!args.has(name) || args.isNull(name)) throw new IllegalArgumentException("Missing required argument: " + name);
      }
    }
    JSONArray names = args.names();
    for (int i = 0; names != null && i < names.length(); i++) {
      String name = names.optString(i);
      if (!operation.argumentSchema().containsKey("properties")
          || !((Map<?, ?>) operation.argumentSchema().get("properties")).containsKey(name)) {
        throw new IllegalArgumentException("Unexpected argument: " + name);
      }
    }
    if (args.has("body") && !args.isNull("body")) validateBody(args.opt("body"));
  }
  private void validateBody(Object value) {
    OpenApiOperation.Parameter parameter = operation.parameters().get("body");
    if (parameter == null) throw new IllegalArgumentException("Unexpected body argument");
    Object schemaType = parameter.schema().get("type");
    if (schemaType instanceof String && !matchesType((String) schemaType, value)) {
      throw new IllegalArgumentException("Invalid body type");
    }
    if (!(value instanceof JSONObject)) return;
    Map<?, ?> properties = parameter.schema().get("properties") instanceof Map<?, ?>
        ? (Map<?, ?>) parameter.schema().get("properties") : java.util.Collections.emptyMap();
    JSONObject object = (JSONObject) value;
    for (String key : keys(object)) {
      Object propertySchema = properties.get(key);
      if (!(propertySchema instanceof Map<?, ?>)) throw new IllegalArgumentException("Unexpected body field");
      Object type = ((Map<?, ?>) propertySchema).get("type");
      if (type instanceof String && !matchesType((String) type, object.opt(key))) {
        throw new IllegalArgumentException("Invalid body field type");
      }
    }
    Object requiredFields = parameter.schema().get("required");
    if (requiredFields instanceof List<?>) {
      for (Object field : (List<?>) requiredFields) {
        if (!object.has(String.valueOf(field)) || object.isNull(String.valueOf(field))) {
          throw new IllegalArgumentException("Missing required body field");
        }
      }
    }
  }

  private static List<String> keys(JSONObject object) {
    List<String> result = new ArrayList<>();
    JSONArray names = object == null ? null : object.names();
    for (int i = 0; names != null && i < names.length(); i++) result.add(names.optString(i));
    return result;
  }

  private static List<String> bodyFields(JSONObject body) {
    return keys(body);
  }
  private boolean declaredBodyField(String field) {
    OpenApiOperation.Parameter body = operation.parameters().get("body");
    Object properties = body == null ? null : body.schema().get("properties");
    return properties instanceof Map<?, ?> && ((Map<?, ?>) properties).containsKey(field);
  }

  private static boolean matchesType(String type, Object value) {
    switch (type) {
      case "string": return value instanceof String;
      case "integer": return value instanceof Number;
      case "number": return value instanceof Number;
      case "boolean": return value instanceof Boolean;
      case "object": return value instanceof JSONObject;
      case "array": return value instanceof JSONArray;
      default: return true;
    }
  }

  private static void validateCriteria(String source, Entity entity, Set<String> allowedFields) {
    try {
      Object parsed = source.trim().startsWith("[") ? new JSONArray(source) : new JSONObject(source);
      validateCriteriaNode(parsed, entity, allowedFields);
    } catch (JSONException e) {
      throw new IllegalArgumentException("Invalid criteria argument");
    }
  }

  private static void validateCriteriaNode(Object node, Entity entity, Set<String> allowedFields)
      throws JSONException {
    if (node instanceof JSONArray) {
      JSONArray array = (JSONArray) node;
      for (int i = 0; i < array.length(); i++) validateCriteriaNode(array.get(i), entity, allowedFields);
      return;
    }
    if (!(node instanceof JSONObject)) throw new IllegalArgumentException("Invalid criteria node");
    JSONObject object = (JSONObject) node;
    if (object.has("fieldName")) {
      String field = object.getString("fieldName");
      if (!allowedFields.contains(field) || entity.getProperty(field) == null) {
        throw new IllegalArgumentException("Criteria field is not declared by the operation");
      }
      return;
    }
    if (object.has("criteria")) {
      validateCriteriaNode(object.get("criteria"), entity, allowedFields);
      return;
    }
    throw new IllegalArgumentException("Invalid criteria node");
  }

  private static void checkFields(Entity entity, String fields, Set<String> allowedFields, boolean write) {
    for (String field : fields.split(",")) {
      String name = field.trim();
      if (name.isEmpty()) continue;
      if (!allowedFields.contains(name) || entity.getProperty(name) == null) {
        throw new IllegalArgumentException("Unknown or undeclared entity field");
      }
      if (write) entity.getProperty(name).checkIsWritable();
    }
  }

  private static void checkSort(Entity entity, String sort, Set<String> allowedFields) {
    for (String part : sort.split(",")) {
      String field = part.trim().replaceFirst("(?i)\\s+(asc|desc)$", "");
      if (!allowedFields.contains(field) || entity.getProperty(field) == null) {
        throw new IllegalArgumentException("Unknown or undeclared sort field");
      }
    }
  }

  private static String replaceBase64(String value) throws IOException {
    Matcher matcher = BASE64_FILE.matcher(value);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String encoded = java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(matcher.group(1))));
      matcher.appendReplacement(result, Matcher.quoteReplacement(encoded));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static String encode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static ToolSpecification buildSpecification(OpenApiOperation operation) {
    JsonObjectSchema.Builder schema = JsonObjectSchema.builder();
    Map<?, ?> properties = (Map<?, ?>) operation.argumentSchema().get("properties");
    for (Map.Entry<?, ?> entry : properties.entrySet()) {
      schema.addProperty(String.valueOf(entry.getKey()), JsonRawSchema.from(json(entry.getValue())));
    }
    Object required = operation.argumentSchema().get("required");
    if (required instanceof List<?> && !((List<?>) required).isEmpty()) {
      List<String> names = new ArrayList<>();
      for (Object value : (List<?>) required) names.add(String.valueOf(value));
      schema.required(names);
    }
    ToolSpecification.Builder builder = ToolSpecification.builder().name(operation.name());
    if (!operation.description().isEmpty()) builder.description(operation.description());
    return builder.parameters(schema.build()).build();
  }

  private static String json(Object value) {
    if (value instanceof Map<?, ?>) {
      JSONObject object = new JSONObject();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        try { object.put(String.valueOf(entry.getKey()), entry.getValue()); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
      }
      return object.toString();
    }
    return String.valueOf(value);
  }
}
