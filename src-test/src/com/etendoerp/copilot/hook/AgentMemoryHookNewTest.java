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

  @Before
  public void setUp() {
    ObjenesisStd objenesis = new ObjenesisStd();
    hook = objenesis.newInstance(AgentMemoryHook.class);
  }

  // --- typeCheck tests ---

  @Test
  public void testTypeCheckAlwaysTrue() {
    assertTrue(hook.typeCheck(null));
  }

  @Test
  public void testTypeCheckWithMockedApp() {
    CopilotApp app = mock(CopilotApp.class);
    assertTrue(hook.typeCheck(app));
  }

  // --- getRolesByInheritance with null (via reflection) ---

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

  @Test
  public void testImplementsOpenAIPromptHook() {
    assertTrue(hook instanceof OpenAIPromptHook);
  }

  // --- getPriority test ---

  @Test
  public void testGetPriorityDefault() {
    assertEquals(100, hook.getPriority());
  }
}
