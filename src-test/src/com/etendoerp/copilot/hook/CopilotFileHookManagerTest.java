package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.test.WeldBaseTest;

import com.etendoerp.copilot.data.CopilotFile;

public class CopilotFileHookManagerTest extends WeldBaseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private Instance<CopilotFileHook> mockCopFileHooks;

    @Mock
    private CopilotFile mockCopilotFile;

    private CopilotFileHookManager hookManager;

    private AutoCloseable mocks;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        hookManager = new CopilotFileHookManager();
        
        // Use reflection to inject the mock hooks
        java.lang.reflect.Field hooksField = CopilotFileHookManager.class.getDeclaredField("copFileHooks");
        hooksField.setAccessible(true);
        hooksField.set(hookManager, mockCopFileHooks);
    }

    @After
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void testSortHooksByPriority_MultipleHooks() {
        // Given
        CopilotFileHook hook1 = mock(CopilotFileHook.class);
        CopilotFileHook hook2 = mock(CopilotFileHook.class);
        CopilotFileHook hook3 = mock(CopilotFileHook.class);

        when(hook1.getPriority()).thenReturn(10);
        when(hook2.getPriority()).thenReturn(5);
        when(hook3.getPriority()).thenReturn(15);

        List<CopilotFileHook> mockHookList = new ArrayList<>();
        mockHookList.add(hook1);
        mockHookList.add(hook2);
        mockHookList.add(hook3);

        when(mockCopFileHooks.iterator()).thenReturn(mockHookList.iterator());

        // When
        List<Object> sortedHooks = hookManager.sortHooksByPriority(mockCopFileHooks);

        // Then
        assertEquals(3, sortedHooks.size());
        assertEquals(hook2, sortedHooks.get(0)); // Priority 5
        assertEquals(hook1, sortedHooks.get(1)); // Priority 10
        assertEquals(hook3, sortedHooks.get(2)); // Priority 15
    }

    @Test
    public void testExecuteHooks_SuccessfulExecution() throws Exception {
        // Given
        CopilotFileHook hook1 = mock(CopilotFileHook.class);
        CopilotFileHook hook2 = mock(CopilotFileHook.class);

        when(mockCopilotFile.getType()).thenReturn("testType");
        when(hook1.typeCheck("testType")).thenReturn(true);
        when(hook2.typeCheck("testType")).thenReturn(true);

        List<CopilotFileHook> mockHookList = new ArrayList<>();
        mockHookList.add(hook1);
        mockHookList.add(hook2);

        when(mockCopFileHooks.iterator()).thenReturn(mockHookList.iterator());

        // When
        hookManager.executeHooks(mockCopilotFile);

        // Then
        verify(hook1).exec(mockCopilotFile);
        verify(hook2).exec(mockCopilotFile);
    }

    @Test
    public void testExecuteHooks_FilteredByType() throws Exception {
        // Given
        CopilotFileHook hook1 = mock(CopilotFileHook.class);
        CopilotFileHook hook2 = mock(CopilotFileHook.class);

        when(mockCopilotFile.getType()).thenReturn("testType");
        when(hook1.typeCheck("testType")).thenReturn(true);
        when(hook2.typeCheck("testType")).thenReturn(false);

        List<CopilotFileHook> mockHookList = new ArrayList<>();
        mockHookList.add(hook1);
        mockHookList.add(hook2);

        when(mockCopFileHooks.iterator()).thenReturn(mockHookList.iterator());

        // When
        hookManager.executeHooks(mockCopilotFile);

        // Then
        verify(hook1).exec(mockCopilotFile);
        verify(hook2, never()).exec(mockCopilotFile);
    }

    @Test
    public void testExecuteHooks_ExceptionHandling() throws Exception {
        // Given
        CopilotFileHook hook1 = mock(CopilotFileHook.class);

        when(mockCopilotFile.getType()).thenReturn("testType");
        when(hook1.typeCheck("testType")).thenReturn(true);
        doThrow(new RuntimeException("Test exception")).when(hook1).exec(mockCopilotFile);

        List<CopilotFileHook> mockHookList = new ArrayList<>();
        mockHookList.add(hook1);

        when(mockCopFileHooks.iterator()).thenReturn(mockHookList.iterator());

        // Then
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Test exception");

        // When
        hookManager.executeHooks(mockCopilotFile);
    }
}