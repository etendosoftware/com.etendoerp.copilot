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

  @Before
  public void setUp() {
    manager = new CopilotQuestionHookManager();
  }

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

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPrioritySortsCorrectly() {
    CopilotQuestionHook hook1 = new CopilotQuestionHook() {
      @Override
      public void exec(CopilotApp app, JSONObject json) { }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
      @Override
      public int getPriority() { return 75; }
    };

    CopilotQuestionHook hook2 = new CopilotQuestionHook() {
      @Override
      public void exec(CopilotApp app, JSONObject json) { }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
      @Override
      public int getPriority() { return 25; }
    };

    CopilotQuestionHook hook3 = new CopilotQuestionHook() {
      @Override
      public void exec(CopilotApp app, JSONObject json) { }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
      @Override
      public int getPriority() { return 50; }
    };

    Instance<CopilotQuestionHook> instance = mock(Instance.class);
    Iterator<CopilotQuestionHook> iter = Arrays.asList(hook1, hook2, hook3).iterator();
    when(instance.iterator()).thenReturn(iter);

    List<Object> sorted = manager.sortHooksByPriority(instance);
    assertEquals(3, sorted.size());
    assertEquals(25, ((CopilotQuestionHook) sorted.get(0)).getPriority());
    assertEquals(50, ((CopilotQuestionHook) sorted.get(1)).getPriority());
    assertEquals(75, ((CopilotQuestionHook) sorted.get(2)).getPriority());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityFiltersNonHooks() {
    CopilotQuestionHook hook = new CopilotQuestionHook() {
      @Override
      public void exec(CopilotApp app, JSONObject json) { }
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

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityWithEqualPriorities() {
    CopilotQuestionHook hook1 = new CopilotQuestionHook() {
      @Override
      public void exec(CopilotApp app, JSONObject json) { }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
      @Override
      public int getPriority() { return 100; }
    };

    CopilotQuestionHook hook2 = new CopilotQuestionHook() {
      @Override
      public void exec(CopilotApp app, JSONObject json) { }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
      @Override
      public int getPriority() { return 100; }
    };

    Instance<CopilotQuestionHook> instance = mock(Instance.class);
    Iterator<CopilotQuestionHook> iter = Arrays.asList(hook1, hook2).iterator();
    when(instance.iterator()).thenReturn(iter);

    List<Object> sorted = manager.sortHooksByPriority(instance);
    assertEquals(2, sorted.size());
  }
}
