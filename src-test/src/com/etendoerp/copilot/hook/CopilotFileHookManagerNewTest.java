package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.etendoerp.copilot.data.CopilotFile;

/**
 * Tests for CopilotFileHookManager.
 */
@RunWith(MockitoJUnitRunner.class)
public class CopilotFileHookManagerNewTest {

  private CopilotFileHookManager manager;

  @Before
  public void setUp() {
    manager = new CopilotFileHookManager();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityEmpty() {
    Instance<CopilotFileHook> emptyInstance = mock(Instance.class);
    Iterator<CopilotFileHook> emptyIter = mock(Iterator.class);
    when(emptyIter.hasNext()).thenReturn(false);
    when(emptyInstance.iterator()).thenReturn(emptyIter);

    List<Object> result = manager.sortHooksByPriority(emptyInstance);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPrioritySortsCorrectly() {
    CopilotFileHook hook1 = new CopilotFileHook() {
      @Override
      public void exec(CopilotFile hookObject) { }
      @Override
      public boolean typeCheck(String type) { return true; }
      @Override
      public int getPriority() { return 50; }
    };

    CopilotFileHook hook2 = new CopilotFileHook() {
      @Override
      public void exec(CopilotFile hookObject) { }
      @Override
      public boolean typeCheck(String type) { return true; }
      @Override
      public int getPriority() { return 10; }
    };

    CopilotFileHook hook3 = new CopilotFileHook() {
      @Override
      public void exec(CopilotFile hookObject) { }
      @Override
      public boolean typeCheck(String type) { return true; }
      @Override
      public int getPriority() { return 100; }
    };

    Instance<CopilotFileHook> instance = mock(Instance.class);
    Iterator<CopilotFileHook> iter = Arrays.asList(hook1, hook2, hook3).iterator();
    when(instance.iterator()).thenReturn(iter);

    List<Object> sorted = manager.sortHooksByPriority(instance);
    assertEquals(3, sorted.size());
    // Should be sorted: priority 10, 50, 100
    assertEquals(10, ((CopilotFileHook) sorted.get(0)).getPriority());
    assertEquals(50, ((CopilotFileHook) sorted.get(1)).getPriority());
    assertEquals(100, ((CopilotFileHook) sorted.get(2)).getPriority());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityFiltersNonHooks() {
    // Only CopilotFileHook instances should be added
    CopilotFileHook hook1 = new CopilotFileHook() {
      @Override
      public void exec(CopilotFile hookObject) { }
      @Override
      public boolean typeCheck(String type) { return true; }
    };

    Instance<Object> instance = mock(Instance.class);
    Object nonHook = new Object();
    Iterator<Object> iter = Arrays.asList((Object) hook1, nonHook).iterator();
    when(instance.iterator()).thenReturn(iter);

    List<Object> result = manager.sortHooksByPriority(instance);
    assertEquals(1, result.size());
    assertTrue(result.get(0) instanceof CopilotFileHook);
  }
}
