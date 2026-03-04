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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;

/**
 * Tests for OpenAIPromptHookManager.
 */
@RunWith(MockitoJUnitRunner.class)
public class OpenAIPromptHookManagerNewTest {

  private OpenAIPromptHookManager manager;

  @Before
  public void setUp() {
    manager = new OpenAIPromptHookManager();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityEmpty() {
    Instance<OpenAIPromptHook> emptyInstance = mock(Instance.class);
    Iterator<OpenAIPromptHook> emptyIter = mock(Iterator.class);
    when(emptyIter.hasNext()).thenReturn(false);
    when(emptyInstance.iterator()).thenReturn(emptyIter);

    List<Object> result = manager.sortHooksByPriority(emptyInstance);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPrioritySortsCorrectly() {
    OpenAIPromptHook hook1 = new OpenAIPromptHook() {
      @Override
      public String exec(CopilotApp app) { return "hook1"; }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
      @Override
      public int getPriority() { return 200; }
    };

    OpenAIPromptHook hook2 = new OpenAIPromptHook() {
      @Override
      public String exec(CopilotApp app) { return "hook2"; }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
      @Override
      public int getPriority() { return 5; }
    };

    Instance<OpenAIPromptHook> instance = mock(Instance.class);
    Iterator<OpenAIPromptHook> iter = Arrays.asList(hook1, hook2).iterator();
    when(instance.iterator()).thenReturn(iter);

    List<Object> sorted = manager.sortHooksByPriority(instance);
    assertEquals(2, sorted.size());
    assertEquals(5, ((OpenAIPromptHook) sorted.get(0)).getPriority());
    assertEquals(200, ((OpenAIPromptHook) sorted.get(1)).getPriority());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSortHooksByPriorityFiltersNonHooks() {
    OpenAIPromptHook hook = new OpenAIPromptHook() {
      @Override
      public String exec(CopilotApp app) { return "result"; }
      @Override
      public boolean typeCheck(CopilotApp app) { return true; }
    };

    Instance<Object> instance = mock(Instance.class);
    Object nonHook = new Object();
    Iterator<Object> iter = Arrays.asList((Object) hook, nonHook).iterator();
    when(instance.iterator()).thenReturn(iter);

    List<Object> result = manager.sortHooksByPriority(instance);
    assertEquals(1, result.size());
    assertTrue(result.get(0) instanceof OpenAIPromptHook);
  }
}
