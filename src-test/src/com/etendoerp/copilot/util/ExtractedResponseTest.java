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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.util;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExtractedResponse#getResponseAsJSON()}.
 */
class ExtractedResponseTest {

  private static final String LIT_CONV1 = "conv1";

  @Test
  void testGetResponseAsJsonValidJson() throws JSONException {
    String jsonStr = "{\"invoice_number\":\"123\",\"amount\":100}";
    ExtractedResponse resp = new ExtractedResponse(jsonStr, LIT_CONV1, null);
    JSONObject result = resp.getResponseAsJSON();
    Assertions.assertNotNull(result);
    Assertions.assertEquals("123", result.getString("invoice_number"));
    Assertions.assertEquals(100, result.getInt("amount"));
  }

  @Test
  void testGetResponseAsJsonNullResponse() {
    ExtractedResponse resp = new ExtractedResponse(null, LIT_CONV1, null);
    Assertions.assertNull(resp.getResponseAsJSON());
  }

  @Test
  void testGetResponseAsJsonEmptyResponse() {
    ExtractedResponse resp = new ExtractedResponse("", LIT_CONV1, null);
    Assertions.assertNull(resp.getResponseAsJSON());
  }

  @Test
  void testGetResponseAsJsonInvalidJson() {
    ExtractedResponse resp = new ExtractedResponse("This is not JSON", LIT_CONV1, null);
    Assertions.assertNull(resp.getResponseAsJSON());
  }

  @Test
  void testGetResponseAsJsonNestedJson() throws JSONException {
    String jsonStr = "{\"items\":[{\"name\":\"A\",\"qty\":1},{\"name\":\"B\",\"qty\":2}]}";
    ExtractedResponse resp = new ExtractedResponse(jsonStr, LIT_CONV1, null);
    JSONObject result = resp.getResponseAsJSON();
    Assertions.assertNotNull(result);
    Assertions.assertEquals(2, result.getJSONArray("items").length());
  }
}
