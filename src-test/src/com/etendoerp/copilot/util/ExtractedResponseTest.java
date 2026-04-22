/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

/**
 * Unit tests for {@link ExtractedResponse}.
 */
public class ExtractedResponseTest {

  /**
   * Test constructor with all fields.
   * @throws Exception if an error occurs
   */
  @Test
  public void testConstructorWithAllFields() throws Exception {
    JSONObject meta = new JSONObject();
    meta.put("key", "value");

    ExtractedResponse resp = new ExtractedResponse("hello", "conv-123", meta);

    assertEquals("hello", resp.getResponse());
    assertEquals("conv-123", resp.getConversationId());
    assertEquals(meta, resp.getMetadata());
  }

  /** Test constructor with null metadata creates empty json object. */
  @Test
  public void testConstructorWithNullMetadataCreatesEmptyJsonObject() {
    ExtractedResponse resp = new ExtractedResponse("response", "conv-1", null);

    assertNotNull(resp.getMetadata());
    assertEquals(0, resp.getMetadata().length());
  }

  /** Test constructor with null response and conversation id. */
  @Test
  public void testConstructorWithNullResponseAndConversationId() {
    ExtractedResponse resp = new ExtractedResponse(null, null, null);

    assertEquals(null, resp.getResponse());
    assertEquals(null, resp.getConversationId());
    assertNotNull(resp.getMetadata());
  }

  /** Test get response returns exact value. */
  @Test
  public void testGetResponseReturnsExactValue() {
    ExtractedResponse resp = new ExtractedResponse("test response", "id", new JSONObject());
    assertEquals("test response", resp.getResponse());
  }

  /** Test get conversation id returns exact value. */
  @Test
  public void testGetConversationIdReturnsExactValue() {
    ExtractedResponse resp = new ExtractedResponse("r", "my-conversation", new JSONObject());
    assertEquals("my-conversation", resp.getConversationId());
  }

  /**
   * Test get metadata returns provided object.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetMetadataReturnsProvidedObject() throws Exception {
    JSONObject meta = new JSONObject();
    meta.put("foo", "bar");
    ExtractedResponse resp = new ExtractedResponse("r", "c", meta);
    assertEquals("bar", resp.getMetadata().getString("foo"));
  }
}
