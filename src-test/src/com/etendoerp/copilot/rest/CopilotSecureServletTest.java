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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Test;

/**
 * Unit tests for {@link CopilotSecureServlet}.
 */
public class CopilotSecureServletTest {

  /**
   * Restores the static servlet instance after each test execution.
   */
  @After
  public void tearDown() {
    setStaticInstance(null);
  }

  /**
   * Verifies the servlet lazily creates and reuses the same RestService instance.
   */
  @Test
  public void testGetInstanceCreatesAndReusesInstance() {
    setStaticInstance(null);

    RestService first = CopilotSecureServlet.getInstance();
    RestService second = CopilotSecureServlet.getInstance();

    assertNotNull(first);
    assertSame(first, second);
  }

  /**
   * Verifies GET requests are delegated to the internal RestService.
   *
   * @throws Exception if request delegation setup fails
   */
  @Test
  public void testDoGetDelegatesToRestService() throws Exception {
    RestService restService = mock(RestService.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    setStaticInstance(restService);
    doNothing().when(restService).doGet(request, response);

    new CopilotSecureServlet().doGet(request, response);

    verify(restService).doGet(request, response);
  }

  /**
   * Verifies POST requests are delegated to the internal RestService.
   *
   * @throws Exception if request delegation setup fails
   */
  @Test
  public void testDoPostDelegatesToRestService() throws Exception {
    RestService restService = mock(RestService.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    setStaticInstance(restService);
    doNothing().when(restService).doPost(request, response);

    new CopilotSecureServlet().doPost(request, response);

    verify(restService).doPost(request, response);
  }

  private void setStaticInstance(RestService value) {
    try {
      Field field = CopilotSecureServlet.class.getDeclaredField("instance");
      field.setAccessible(true);
      field.set(null, value);
    } catch (ReflectiveOperationException e) {
      throw new TestReflectionException("Unable to update CopilotSecureServlet instance", e);
    }
  }

  private static final class TestReflectionException extends RuntimeException {
    private TestReflectionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
