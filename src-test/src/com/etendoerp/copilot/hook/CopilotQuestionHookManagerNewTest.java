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
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;

/**
 * Tests for CopilotQuestionHookManager.
 */
@RunWith(MockitoJUnitRunner.class)
public class CopilotQuestionHookManagerNewTest {

  private CopilotQuestionHookManager manager;

  /** Set up. */
  @Before
  public void setUp() {
    manager = new CopilotQuestionHookManager();
  }

  /** Test sort hooks by priority empty. */
  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityEmpty() {
    Instance<CopilotQuestionHook> emptyInstance = mock(Instance.class);
    Iterator<CopilotQuestionHook> emptyIter = mock(Iterator.class);
    when(emptyIter.hasNext()).thenReturn(false);
    when(emptyInstance.iterator()).thenReturn(emptyIter);

    List<Object> result = manager.sortHooksByPriority(emptyInstance);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  /** Test sort hooks by priority sorts correctly. */
  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPrioritySortsCorrectly() {
    CopilotQuestionHook hook1 = createHookWithPriority(75);
    CopilotQuestionHook hook2 = createHookWithPriority(25);
    CopilotQuestionHook hook3 = createHookWithPriority(50);

    Instance<CopilotQuestionHook> instance = mockInstanceWith(hook1, hook2, hook3);

    List<Object> sorted = manager.sortHooksByPriority(instance);
    assertEquals(3, sorted.size());
    assertEquals(25, ((CopilotQuestionHook) sorted.get(0)).getPriority());
    assertEquals(50, ((CopilotQuestionHook) sorted.get(1)).getPriority());
    assertEquals(75, ((CopilotQuestionHook) sorted.get(2)).getPriority());
  }

  /** Test sort hooks by priority filters non hooks. */
  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityFiltersNonHooks() {
    CopilotQuestionHook hook = new CopilotQuestionHook() {
      @Override
      public void exec(CopilotApp app, JSONObject json) { /* No-op: test stub */ }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
    };

    Instance<Object> instance = mock(Instance.class);
    Object nonHook = new Object();
    Iterator<Object> iter = Arrays.asList((Object) hook, nonHook).iterator();
    when(instance.iterator()).thenReturn(iter);

    List<Object> result = manager.sortHooksByPriority(instance);
    assertEquals(1, result.size());
    assertTrue(result.get(0) instanceof CopilotQuestionHook);
  }

  /** Test sort hooks by priority with equal priorities. */
  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityWithEqualPriorities() {
    CopilotQuestionHook hook1 = createHookWithPriority(100);
    CopilotQuestionHook hook2 = createHookWithPriority(100);

    Instance<CopilotQuestionHook> instance = mockInstanceWith(hook1, hook2);

    List<Object> sorted = manager.sortHooksByPriority(instance);
    assertEquals(2, sorted.size());
  }

  /**
   * Creates a {@link CopilotQuestionHook} stub with the given priority.
   *
   * @param priority the priority value the hook should return
   * @return a new hook stub
   */
  private CopilotQuestionHook createHookWithPriority(int priority) {
    return new CopilotQuestionHook() {
      @Override
      public void exec(CopilotApp app, JSONObject json) { /* No-op: test stub */ }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
      @Override
      public int getPriority() { return priority; }
    };
  }

  /**
   * Creates a mock {@link Instance} that iterates over the given hooks.
   *
   * @param hooks the hooks to include in the mock instance
   * @return a mocked Instance wrapping the provided hooks
   */
  @SuppressWarnings("unchecked")
  private Instance<CopilotQuestionHook> mockInstanceWith(CopilotQuestionHook... hooks) {
    Instance<CopilotQuestionHook> instance = mock(Instance.class);
    Iterator<CopilotQuestionHook> iter = Arrays.asList(hooks).iterator();
    when(instance.iterator()).thenReturn(iter);
    return instance;
  }
}
