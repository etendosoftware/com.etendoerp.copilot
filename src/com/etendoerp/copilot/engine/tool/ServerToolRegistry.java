/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"). You may not use this file except in compliance with
 * the License.
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;

/** Resolves configured tool descriptors to CDI-managed Java implementations and model schemas. */
public final class ServerToolRegistry {

  private static final String PROP_TOOLS = "tools";
  private static final String PROP_FUNCTION = "function";
  private static final String PROP_NAME = "name";
  private final Function<String, ServerTool> lookup;

  public ServerToolRegistry(Function<String, ServerTool> lookup) {
    this.lookup = lookup;
  }

  /** Use CDI's named-bean registry at runtime. */
  public ServerToolRegistry() {
    this(ServerToolRegistry::lookupCdiBean);
  }

  /**
   * Resolve the {@code tools} field of an engine request.
   *
   * @param request the request assembled by {@code ToolsUtil.getToolSet}
   * @return immutable resolved tools, specifications and missing names
   */
  public ResolvedTools resolve(JSONObject request) {
    return resolve(request == null ? null : request.optJSONArray(PROP_TOOLS));
  }

  /** @return whether CDI can resolve an implementation with this exact tool name */
  public boolean hasImplementation(String name) {
    if (StringUtils.isBlank(name)) {
      return false;
    }
    ServerTool tool = lookup.apply(name);
    return tool != null && name.equals(tool.name());
  }

  private ResolvedTools resolve(JSONArray descriptors) {
    Map<String, ServerTool> tools = new LinkedHashMap<>();
    List<ToolSpecification> specifications = new ArrayList<>();
    Set<String> missing = new LinkedHashSet<>();
    for (int i = 0; descriptors != null && i < descriptors.length(); i++) {
      JSONObject descriptor = descriptors.optJSONObject(i);
      String name = functionName(descriptor);
      if (StringUtils.isBlank(name)) {
        missing.add("tool descriptor at position " + i + " (missing function.name)");
        continue;
      }
      if (tools.containsKey(name) || missing.contains(name)) {
        continue;
      }
      ServerTool tool = lookup.apply(name);
      if (tool == null || !name.equals(tool.name())) {
        missing.add(name);
        continue;
      }
      ToolSpecification ownSpecification;
      try {
        ownSpecification = tool.specification();
      } catch (RuntimeException e) {
        missing.add(name);
        continue;
      }
      if (ownSpecification == null) {
        missing.add(name);
        continue;
      }
      tools.put(name, tool);
      specifications.add(specification(descriptor, tool, ownSpecification));
    }
    return new ResolvedTools(tools, specifications, new ArrayList<>(missing));
  }

  private static String functionName(JSONObject descriptor) {
    if (descriptor == null) {
      return null;
    }
    JSONObject function = descriptor.optJSONObject(PROP_FUNCTION);
    return function == null ? null : function.optString(PROP_NAME, null);
  }

  private static ToolSpecification specification(JSONObject descriptor, ServerTool tool,
      ToolSpecification original) {
    JSONObject function = descriptor == null ? null : descriptor.optJSONObject(PROP_FUNCTION);
    // ToolsUtil emits only {type:function,function:{name}} when JSON_STRUCTURE is empty.
    // Retain the implementation's schema for that short descriptor.
    if (function == null || (function.length() == 1 && function.has(PROP_NAME))) {
      return original;
    }

    ToolSpecification.Builder builder = ToolSpecification.builder().name(tool.name());
    String description = function.optString("description", null);
    if (StringUtils.isBlank(description)) {
      description = original.description();
    }
    if (StringUtils.isNotBlank(description)) {
      builder.description(description);
    }
    JSONObject parameters = function.optJSONObject("parameters");
    if (parameters != null) {
      builder.parameters(toObjectSchema(parameters));
    }
    return builder.build();
  }

  private static JsonObjectSchema toObjectSchema(JSONObject schema) {
    JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
    String description = schema.optString("description", null);
    if (StringUtils.isNotBlank(description)) {
      builder.description(description);
    }
    JSONObject properties = schema.optJSONObject("properties");
    JSONArray names = properties == null ? null : properties.names();
    for (int i = 0; names != null && i < names.length(); i++) {
      String propertyName = names.optString(i);
      Object property = properties.opt(propertyName);
      if (property instanceof JSONObject) {
        builder.addProperty(propertyName, JsonRawSchema.from(property.toString()));
      } else if (property instanceof String) {
        builder.addProperty(propertyName, JsonRawSchema.from((String) property));
      }
    }
    JSONArray required = schema.optJSONArray("required");
    List<String> requiredNames = new ArrayList<>();
    for (int i = 0; required != null && i < required.length(); i++) {
      requiredNames.add(required.optString(i));
    }
    if (!requiredNames.isEmpty()) {
      builder.required(requiredNames);
    }
    return builder.build();
  }

  private static ServerTool lookupCdiBean(String name) {
    try {
      BeanManager manager = CDI.current().getBeanManager();
      Set<Bean<?>> beans = manager.getBeans(name);
      Bean<?> bean = manager.resolve(beans);
      if (bean == null) {
        return null;
      }
      Object instance = manager.getReference(bean, ServerTool.class, manager.createCreationalContext(bean));
      return instance instanceof ServerTool ? (ServerTool) instance : null;
    } catch (RuntimeException e) {
      // A broken or ambiguous bean is unavailable just like a missing implementation.
      return null;
    }
  }

  /** Immutable result of resolving the configured tool descriptors. */
  public static final class ResolvedTools {
    private final Map<String, ServerTool> tools;
    private final List<ToolSpecification> specifications;
    private final List<String> missingNames;

    private ResolvedTools(Map<String, ServerTool> tools, List<ToolSpecification> specifications,
        List<String> missingNames) {
      this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
      this.specifications = Collections.unmodifiableList(new ArrayList<>(specifications));
      this.missingNames = Collections.unmodifiableList(new ArrayList<>(missingNames));
    }

    public Map<String, ServerTool> tools() {
      return tools;
    }

    public ServerTool tool(String name) {
      return tools.get(name);
    }

    public List<ToolSpecification> specifications() {
      return specifications;
    }

    public List<String> missingNames() {
      return missingNames;
    }

    public boolean hasTools() {
      return !tools.isEmpty();
    }
  }
}
