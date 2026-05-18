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
package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.objenesis.ObjenesisStd;

import com.etendoerp.copilot.data.CopilotApp;

/**
 * Tests for AgentMemoryHook focusing on testable methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class AgentMemoryHookNewTest {

  private AgentMemoryHook hook;

  /** Set up. */
  @Before
  public void setUp() {
    ObjenesisStd objenesis = new ObjenesisStd();
    hook = objenesis.newInstance(AgentMemoryHook.class);
  }

  // --- typeCheck tests ---

  /** Test type check always true. */
  @Test
  public void testTypeCheckAlwaysTrue() {
    assertTrue(hook.typeCheck(null));
  }

  /** Test type check with mocked app. */
  @Test
  public void testTypeCheckWithMockedApp() {
    CopilotApp app = mock(CopilotApp.class);
    assertTrue(hook.typeCheck(app));
  }

  // --- getRolesByInheritance with null (via reflection) ---

  /**
   * Test get roles by inheritance null.
   * @throws Exception if an error occurs
   */
  @Test
  public void testGetRolesByInheritanceNull() throws Exception {
    Method method = AgentMemoryHook.class.getDeclaredMethod("getRolesByInheritance",
        org.openbravo.model.ad.access.Role.class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    Set<org.openbravo.model.ad.access.Role> result =
        (Set<org.openbravo.model.ad.access.Role>) method.invoke(hook, (Object) null);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  // --- implements OpenAIPromptHook ---

  /** Test implements open a i prompt hook. */
  @Test
  public void testImplementsOpenAIPromptHook() {
    assertTrue(hook instanceof OpenAIPromptHook);
  }

  // --- getPriority test ---

  /** Test get priority default. */
  @Test
  public void testGetPriorityDefault() {
    assertEquals(100, hook.getPriority());
  }
}
