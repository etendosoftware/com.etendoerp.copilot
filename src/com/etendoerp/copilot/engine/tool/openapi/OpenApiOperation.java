/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License (the "License").
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L. All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool.openapi;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable descriptor for one operation in a request-supplied FLOW OpenAPI document. */
public final class OpenApiOperation {
  public enum Route { IN_PROCESS, LOOPBACK }

  private final String name;
  private final String description;
  private final String method;
  private final String server;
  private final String path;
  private final String entityName;
  private final String reason;
  private final Map<String, Parameter> parameters;
  private final Map<String, Object> argumentSchema;
  private final Set<String> allowedFields;
  OpenApiOperation(String name, String description, String method, String server, String path,
      String entityName, String reason, Map<String, Parameter> parameters,
      Map<String, Object> argumentSchema, Set<String> allowedFields) {
    this.name = name;
    this.description = description;
    this.method = method;
    this.server = server;
    this.path = path;
    this.entityName = entityName;
    this.reason = reason;
    this.parameters = immutableMap(parameters);
    this.argumentSchema = immutableMap(argumentSchema);
    this.allowedFields = Collections.unmodifiableSet(new LinkedHashSet<>(allowedFields));
  }

  public String name() { return name; }
  public String description() { return description; }
  public String method() { return method; }
  public String server() { return server; }
  public String path() { return path; }
  public String entityName() { return entityName; }
  public String reason() { return reason; }
  public Map<String, Parameter> parameters() { return parameters; }
  public Set<String> allowedFields() { return allowedFields; }
  public Map<String, Object> argumentSchema() { return argumentSchema; }
  public Route route() { return entityName == null ? Route.LOOPBACK : Route.IN_PROCESS; }

  private static <T> Map<String, T> immutableMap(Map<String, ? extends T> source) {
    Map<String, T> result = new LinkedHashMap<>();
    for (Map.Entry<String, ? extends T> entry : source.entrySet()) {
      @SuppressWarnings("unchecked")
      T value = (T) freeze(entry.getValue());
      result.put(entry.getKey(), value);
    }
    return Collections.unmodifiableMap(result);
  }

  private static Object freeze(Object value) {
    if (value instanceof Map<?, ?>) {
      Map<String, Object> nested = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        nested.put(String.valueOf(entry.getKey()), freeze(entry.getValue()));
      }
      return Collections.unmodifiableMap(nested);
    }
    if (value instanceof java.util.List<?>) {
      java.util.List<Object> nested = new java.util.ArrayList<>();
      for (Object item : (java.util.List<?>) value) nested.add(freeze(item));
      return Collections.unmodifiableList(nested);
    }
    return value;
  }

  public static final class Parameter {
    private final String location;
    private final Map<String, Object> schema;
    private final boolean required;

    Parameter(String location, Map<String, Object> schema, boolean required) {
      this.location = location;
      this.schema = immutableMap(schema);
      this.required = required;
    }

    public String location() { return location; }
    public Map<String, Object> schema() { return schema; }
    public boolean required() { return required; }
  }
}
