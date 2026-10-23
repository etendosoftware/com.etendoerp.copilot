package com.etendoerp.copilot.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

public class SimpleOcrToolTest {
  @Test
  public void specificationDeclaresThePythonArguments() throws Exception {
    var specification = new SimpleOcrTool().specification();
    assertEquals("SimpleOcrTool", specification.name());
    assertTrue(specification.description().contains("JPEG, PNG, WebP, GIF, and PDF"));
    String schema = specification.parameters().toString();
    assertTrue(schema.contains("structured_output"));
    assertTrue(schema.contains("structured_output_schema"));
  }

  @Test
  public void namedTestDocumentSchemaContainsNestedPropertiesAndRequiredFields() throws Exception {
    JSONObject schema = SimpleOcrTool.namedSchema("Testdocument");
    assertNotNull(schema);
    assertEquals("object", schema.getString("type"));
    assertTrue(schema.getJSONObject("properties").has("line_items"));
    assertTrue(schema.getJSONObject("properties").getJSONObject("owner").getJSONObject("properties").has("email"));
    assertTrue(schema.getJSONArray("required").toString().contains("document_number"));
    assertNull(SimpleOcrTool.namedSchema("unknown"));
  }

  @Test
  public void buildsImagePromptWithInlineJsonSchema() throws Exception {
    JSONObject schema = new JSONObject().put("type", "object").put("required", new org.codehaus.jettison.json.JSONArray().put("invoice_no"));
    List<dev.langchain4j.data.message.ChatMessage> messages = SimpleOcrTool.buildMessages(new byte[] { 1, 2 }, "image/png", "Read invoice number", schema);
    assertEquals(2, messages.size());
    assertTrue(((SystemMessage) messages.get(0)).text().contains(schema.toString()));
    UserMessage user = (UserMessage) messages.get(1);
    assertEquals("Read invoice number", ((TextContent) user.contents().get(1)).text());
  }

  @Test
  public void detectsSupportedDocumentSignaturesAndRejectsUnknownBytes() {
    assertEquals("application/pdf", SimpleOcrTool.detectMime("%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    assertEquals("image/png", SimpleOcrTool.detectMime(new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 }));
    assertNull(SimpleOcrTool.detectMime(new byte[] { 0, 1, 2 }));
  }
}
