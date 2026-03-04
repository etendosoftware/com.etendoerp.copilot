package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

/**
 * Unit tests for {@link ExtractedResponse}.
 */
public class ExtractedResponseTest {

  @Test
  public void testConstructorWithAllFields() throws Exception {
    JSONObject meta = new JSONObject();
    meta.put("key", "value");

    ExtractedResponse resp = new ExtractedResponse("hello", "conv-123", meta);

    assertEquals("hello", resp.getResponse());
    assertEquals("conv-123", resp.getConversationId());
    assertEquals(meta, resp.getMetadata());
  }

  @Test
  public void testConstructorWithNullMetadataCreatesEmptyJsonObject() {
    ExtractedResponse resp = new ExtractedResponse("response", "conv-1", null);

    assertNotNull(resp.getMetadata());
    assertEquals(0, resp.getMetadata().length());
  }

  @Test
  public void testConstructorWithNullResponseAndConversationId() {
    ExtractedResponse resp = new ExtractedResponse(null, null, null);

    assertEquals(null, resp.getResponse());
    assertEquals(null, resp.getConversationId());
    assertNotNull(resp.getMetadata());
  }

  @Test
  public void testGetResponseReturnsExactValue() {
    ExtractedResponse resp = new ExtractedResponse("test response", "id", new JSONObject());
    assertEquals("test response", resp.getResponse());
  }

  @Test
  public void testGetConversationIdReturnsExactValue() {
    ExtractedResponse resp = new ExtractedResponse("r", "my-conversation", new JSONObject());
    assertEquals("my-conversation", resp.getConversationId());
  }

  @Test
  public void testGetMetadataReturnsProvidedObject() throws Exception {
    JSONObject meta = new JSONObject();
    meta.put("foo", "bar");
    ExtractedResponse resp = new ExtractedResponse("r", "c", meta);
    assertEquals("bar", resp.getMetadata().getString("foo"));
  }
}
