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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;

/** Pure registry tests with an in-memory, test-only tool. */
public class ServerToolRegistryTest {

  @Test
  public void jsonStructureOverridesTheToolSchemaAndUnknownNamesAreReported() throws Exception {
    ServerTool tool = testTool();
    ServerToolRegistry registry = new ServerToolRegistry(name -> "TestRegistryTool".equals(name) ? tool : null);
    JSONObject schema = new JSONObject().put("type", "function").put("function",
        new JSONObject().put("name", "TestRegistryTool").put("description", "Database description")
            .put("parameters", new JSONObject().put("type", "object")
                .put("properties", new JSONObject().put("replacement", new JSONObject().put("type", "integer")))
                .put("required", new JSONArray().put("replacement"))));
    JSONObject request = new JSONObject().put("tools", new JSONArray().put(schema)
        .put(new JSONObject().put("type", "function")
            .put("function", new JSONObject().put("name", "MissingRegistryTool"))));

    ServerToolRegistry.ResolvedTools resolved = registry.resolve(request);

    assertEquals(1, resolved.specifications().size());
    assertEquals("TestRegistryTool", resolved.specifications().get(0).name());
    assertEquals("Database description", resolved.specifications().get(0).description());
    assertTrue(resolved.specifications().get(0).parameters().properties().containsKey("replacement"));
    assertFalse(resolved.specifications().get(0).parameters().properties().containsKey("original"));
    assertTrue(resolved.specifications().get(0).parameters().required().contains("replacement"));
    assertEquals(1, resolved.missingNames().size());
    assertEquals("MissingRegistryTool", resolved.missingNames().get(0));
  }

  @Test
  public void shortToolDescriptorUsesItsOwnSpecification() throws Exception {
    ServerTool tool = testTool();
    ServerToolRegistry registry = new ServerToolRegistry(name -> tool);
    JSONObject request = new JSONObject().put("tools", new JSONArray().put(new JSONObject()
        .put("type", "function").put("function", new JSONObject().put("name", "TestRegistryTool"))));

    ServerToolRegistry.ResolvedTools resolved = registry.resolve(request);

    assertTrue(resolved.missingNames().isEmpty());
    assertTrue(resolved.specifications().get(0).parameters().properties().containsKey("original"));
  }

  @Test
  public void addsKnowledgeBaseSearchOnlyWhenRequested() {
    ServerTool kb = new ServerTool() {
      @Override public String name() { return "KnowledgeBaseSearch"; }
      @Override public ToolSpecification specification() {
        return ToolSpecification.builder().name(name()).parameters(JsonObjectSchema.builder().build()).build();
      }
      @Override public String execute(JSONObject arguments, ToolExecutionContext context) { return ""; }
    };
    ServerToolRegistry registry = new ServerToolRegistry(name -> "KnowledgeBaseSearch".equals(name) ? kb : null);
    JSONObject request = new JSONObject();
    assertFalse(registry.resolve(request).hasTools());
    ServerToolRegistry.ResolvedTools withKb = registry.resolve(request, true);
    assertTrue(withKb.hasTools());
    assertEquals("KnowledgeBaseSearch", withKb.specifications().get(0).name());
  }

  private ServerTool testTool() {
    JsonObjectSchema parameters = JsonObjectSchema.builder()
        .addProperty("original", JsonRawSchema.from("{\"type\":\"string\"}"))
        .required(Collections.singletonList("original"))
        .build();
    ToolSpecification specification = ToolSpecification.builder().name("TestRegistryTool")
        .description("Java description").parameters(parameters).build();
    return new ServerTool() {
      @Override
      public String name() {
        return "TestRegistryTool";
      }

      @Override
      public ToolSpecification specification() {
        return specification;
      }

      @Override
      public String execute(JSONObject arguments, ToolExecutionContext context) {
        return "ok";
      }
    };
  }
}
