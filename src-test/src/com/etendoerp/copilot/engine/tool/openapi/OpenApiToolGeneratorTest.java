/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License (the "License").
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L. All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.tool.openapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

/** Pure tests for request-scoped FLOW descriptor parsing and classification. */
public class OpenApiToolGeneratorTest {
  private static final String SPEC = "{\"servers\":[{\"url\":\"http://localhost\"}],\"paths\":{"
      + "\"/sws/com.etendoerp.etendorx.datasource/customer\":{\"get\":{"
      + "\"operationId\":\"findCustomer\",\"summary\":\"Find customers\","
      + "\"x-etendo-entity\":\"BusinessPartner\",\"x-etendo-fields\":[\"id\",\"name\"],"
      + "\"parameters\":[{\"name\":\"startRow\",\"in\":\"query\",\"schema\":{\"type\":\"integer\"}},"
      + "{\"name\":\"endRow\",\"in\":\"query\",\"schema\":{\"type\":\"integer\"}},"
      + "{\"name\":\"select\",\"in\":\"query\",\"schema\":{\"type\":\"string\"}}]}}}}";

  @Test
  public void parsesFlowOperationSchemaAndPythonCompatibleName() throws Exception {
    JSONObject spec = new JSONObject(SPEC);
    OpenApiOperation operation = new OpenApiToolGenerator().parse(spec).get(0);

    assertEquals("GETFindcustomer", operation.name());
    assertEquals("GET", operation.method());
    assertEquals("BusinessPartner", operation.entityName());
    assertEquals(OpenApiOperation.Route.IN_PROCESS, operation.route());
    assertTrue(operation.argumentSchema().toString().contains("startRow"));
    assertTrue(operation.allowedFields().contains("name"));
  }

  @Test
  public void untrustedDynamicEntityAndRsqlStayOnLoopback() throws Exception {
    JSONObject spec = new JSONObject(SPEC);
    JSONObject path = spec.getJSONObject("paths");
    JSONObject fixed = path.getJSONObject("/sws/com.etendoerp.etendorx.datasource/customer")
        .getJSONObject("get");
    fixed.remove("x-etendo-entity");
    fixed.remove("x-etendo-fields");
    fixed.put("parameters", new JSONArray().put(new JSONObject().put("name", "q")
        .put("in", "query").put("schema", new JSONObject().put("type", "string"))));
    fixed.put("x-etendo-entity", "BusinessPartner");
    fixed.put("x-etendo-fields", new JSONArray().put("name"));

    OpenApiOperation operation = new OpenApiToolGenerator().parse(spec).get(0);

    assertEquals(OpenApiOperation.Route.LOOPBACK, operation.route());
    assertTrue(operation.reason().contains("EtendoHttp"));
  }
  @Test
  public void rejectsArgumentsOutsideTheDeclaredOperationSchema() throws Exception {
    org.codehaus.jettison.json.JSONArray specs = new org.codehaus.jettison.json.JSONArray()
        .put(new JSONObject().put("type", "FLOW").put("spec", SPEC));
    org.codehaus.jettison.json.JSONObject args = new org.codehaus.jettison.json.JSONObject()
        .put("unexpected", "model-controlled entity");

    String result = new OpenApiToolGenerator(new OpenApiDataService() {
      @Override public String fetch(java.util.Map<String, String> parameters) { return "{}"; }
    }).tools(specs).get(0).execute(args, null);

    assertEquals(400, new JSONObject(result).getInt("status_code"));
  }

  @Test
  public void supplementarySpecToolsAreRequestScopedAndExposeTheirOwnSchema() throws Exception {
    JSONArray specs = new JSONArray().put(new JSONObject().put("name", "sample")
        .put("type", "FLOW").put("spec", SPEC));
    OpenApiToolGenerator generator = new OpenApiToolGenerator(new OpenApiDataService() {
      @Override public String fetch(java.util.Map<String, String> parameters) { return "{}"; }
    });

    assertEquals(1, generator.tools(specs).size());
    assertFalse(generator.tools(specs).get(0).specification().parameters().properties().isEmpty());
  }
}
