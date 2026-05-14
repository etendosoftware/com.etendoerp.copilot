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
package com.etendoerp.copilot.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import jakarta.servlet.http.HttpServletRequest;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import com.etendoerp.copilot.util.CopilotConstants;

/**
 * Unit tests for {@link RequestUtils}.
 */
public class RequestUtilsTest {

  /**
   * Verifies that request parameters are copied into the generated JSON object.
   *
   * @throws Exception if the mocked request or JSON assertions fail
   */
  @Test
  public void testRetrieveParametersAsJsonIncludesKnownParameters() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(CopilotConstants.PROP_QUESTION)).thenReturn("Question");
    when(request.getParameter(CopilotConstants.PROP_APP_ID)).thenReturn("app-id");
    when(request.getParameter(CopilotConstants.PROP_CONVERSATION_ID)).thenReturn("conv-id");
    when(request.getParameterValues(CopilotConstants.PROP_FILE)).thenReturn(new String[] {"a.txt", "b.txt"});

    JSONObject result = RequestUtils.retrieveParametersAsJson(request);

    assertEquals("Question", result.getString(CopilotConstants.PROP_QUESTION));
    assertEquals("app-id", result.getString(CopilotConstants.PROP_APP_ID));
    assertEquals("conv-id", result.getString(CopilotConstants.PROP_CONVERSATION_ID));
    JSONArray files = result.getJSONArray(CopilotConstants.PROP_FILE);
    assertEquals(2, files.length());
    assertEquals("a.txt", files.getString(0));
    assertEquals("b.txt", files.getString(1));
  }

  /**
   * Verifies POST detection only succeeds when the request exposes a reader.
   *
   * @throws Exception if request stubbing fails
   */
  @Test
  public void testIsPostRequestReturnsTrueOnlyWhenReaderExists() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("POST");
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

    assertTrue(RequestUtils.isPostRequest(request));

    when(request.getMethod()).thenReturn("GET");
    assertFalse(RequestUtils.isPostRequest(request));
  }

  /**
   * Verifies a valid JSON request body is parsed successfully.
   *
   * @throws Exception if the mocked request cannot be prepared or parsed
   */
  @Test
  public void testParseJsonFromRequestReturnsParsedJson() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"key\":\"value\"}")));

    JSONObject result = RequestUtils.parseJsonFromRequest(request);

    assertEquals("value", result.getString("key"));
  }

  /**
   * Verifies invalid JSON input is converted into an empty object.
   *
   * @throws Exception if the mocked request cannot be prepared
   */
  @Test
  public void testParseJsonFromRequestReturnsEmptyJsonOnInvalidBody() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("not-json")));

    JSONObject result = RequestUtils.parseJsonFromRequest(request);

    assertEquals(0, result.length());
  }

  /**
   * Verifies I/O errors while reading the body produce an empty JSON object.
   *
   * @throws Exception if the mocked request cannot be prepared
   */
  @Test
  public void testParseJsonFromRequestReturnsEmptyJsonOnIOException() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getReader()).thenThrow(new IOException("boom"));

    JSONObject result = RequestUtils.parseJsonFromRequest(request);

    assertEquals(0, result.length());
  }

  /**
   * Verifies request body extraction prefers a valid POST JSON payload.
   *
   * @throws Exception if the mocked request cannot be prepared or parsed
   */
  @Test
  public void testExtractRequestBodyReturnsJsonBodyForPost() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("POST");
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"payload\":1}")));

    JSONObject result = RequestUtils.extractRequestBody(request);

    assertEquals(1, result.getInt("payload"));
  }

  /**
   * Verifies parameter extraction is used when the POST body is empty.
   *
   * @throws Exception if the mocked request cannot be prepared or parsed
   */
  @Test
  public void testExtractRequestBodyFallsBackToParametersWhenBodyIsEmpty() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("POST");
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));
    when(request.getParameter(CopilotConstants.PROP_QUESTION)).thenReturn("Fallback question");
    when(request.getParameter(CopilotConstants.PROP_APP_ID)).thenReturn("fallback-app");

    JSONObject result = RequestUtils.extractRequestBody(request);

    assertEquals("Fallback question", result.getString(CopilotConstants.PROP_QUESTION));
    assertEquals("fallback-app", result.getString(CopilotConstants.PROP_APP_ID));
  }

  /**
   * Verifies array conversion handles both null and populated inputs.
   *
   * @throws Exception if JSON array assertions fail
   */
  @Test
  public void testStringArrayToJsonArrayHandlesNullAndValues() throws Exception {
    assertEquals(0, RequestUtils.stringArrayToJsonArray(null).length());

    JSONArray result = RequestUtils.stringArrayToJsonArray(new String[] {"x", "y"});

    assertEquals(2, result.length());
    assertEquals("x", result.getString(0));
    assertEquals("y", result.getString(1));
  }
}
