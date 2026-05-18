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
package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.objenesis.ObjenesisStd;

/**
 * Tests for CheckHostsButton constants and utility methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class CheckHostsButtonNewTest {

  private CheckHostsButton handler;

  /** Set up. */
  @Before
  public void setUp() {
    ObjenesisStd objenesis = new ObjenesisStd();
    handler = objenesis.newInstance(CheckHostsButton.class);
  }

  /** Test content type constant. */
  @Test
  public void testContentTypeConstant() {
    assertEquals("application/json", CheckHostsButton.CONTENT_TYPE);
  }

  /** Test success constant. */
  @Test
  public void testSuccessConstant() {
    assertEquals("success", CheckHostsButton.SUCCESS);
  }

  /** Test successfully verified constant. */
  @Test
  public void testSuccessfullyVerifiedConstant() {
    assertEquals(" sucessfully verified. ", CheckHostsButton.SUCESSFULLY_VERIFIED);
  }

  /** Test verification failed constant. */
  @Test
  public void testVerificationFailedConstant() {
    assertEquals(" verification failed. ", CheckHostsButton.VERIFICATION_FAILED);
  }

  /** Test etcop host check constant. */
  @Test
  public void testEtcopHostCheckConstant() {
    assertEquals("ETCOP_HOST_CHECK", CheckHostsButton.ETCOP_HOST_CHECK);
  }

  /** Test instance creation. */
  @Test
  public void testInstanceCreation() {
    assertNotNull(handler);
  }
}
