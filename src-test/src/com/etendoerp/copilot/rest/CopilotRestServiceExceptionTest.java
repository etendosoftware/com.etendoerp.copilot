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
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Unit tests for {@link CopilotRestServiceException}.
 */
public class CopilotRestServiceExceptionTest {

  private static final String TEST_MESSAGE = "message";

  /**
   * Verifies the message-only constructor keeps the default error code.
   */
  @Test
  public void testMessageOnlyConstructorUsesDefaultCode() {
    CopilotRestServiceException exception = new CopilotRestServiceException(TEST_MESSAGE);

    assertEquals(TEST_MESSAGE, exception.getMessage());
    assertEquals(-1, exception.getCode());
  }

  /**
   * Verifies the message-and-cause constructor preserves the original cause.
   */
  @Test
  public void testMessageAndCauseConstructorKeepsCause() {
    Throwable cause = new IllegalStateException("cause");
    CopilotRestServiceException exception = new CopilotRestServiceException(TEST_MESSAGE, cause);

    assertEquals(TEST_MESSAGE, exception.getMessage());
    assertSame(cause, exception.getCause());
    assertEquals(-1, exception.getCode());
  }

  /**
   * Verifies the message-and-code constructor stores the provided HTTP code.
   */
  @Test
  public void testMessageAndCodeConstructorStoresCode() {
    CopilotRestServiceException exception = new CopilotRestServiceException(TEST_MESSAGE, 422);

    assertEquals(TEST_MESSAGE, exception.getMessage());
    assertEquals(422, exception.getCode());
  }
}
