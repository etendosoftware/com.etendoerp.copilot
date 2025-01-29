package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.test.WeldBaseTest;

import com.etendoerp.copilot.data.CopilotApp;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Copilot question hook manager test.
 */
public class CopilotQuestionHookManagerTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private Instance<CopilotQuestionHook> mockQuestionHooks;

    @Mock
    private CopilotApp mockApp;

    @Mock
    private JSONObject mockJsonRequest;

    private CopilotQuestionHookManager hookManager;

    private AutoCloseable mocks;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        hookManager = new CopilotQuestionHookManager();

        java.lang.reflect.Field hooksField = CopilotQuestionHookManager.class.getDeclaredField("questionHooks");
        hooksField.setAccessible(true);
        hooksField.set(hookManager, mockQuestionHooks);
    }

    /**
     * Tear down.
     *
     * @throws Exception the exception
     */
    @After
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test sort hooks by priority empty hooks.
     */
    @Test
    public void testSortHooksByPriority_EmptyHooks() {
        // Given
        when(mockQuestionHooks.iterator()).thenReturn(new ArrayList<CopilotQuestionHook>().iterator());

        // When
        List<Object> sortedHooks = hookManager.sortHooksByPriority(mockQuestionHooks);

        // Then
        assertEquals(0, sortedHooks.size());
    }

    /**
     * Test sort hooks by priority multiple hooks.
     */
    @Test
    public void testSortHooksByPriority_MultipleHooks() {
        // Given
        CopilotQuestionHook hook1 = mock(CopilotQuestionHook.class);
        CopilotQuestionHook hook2 = mock(CopilotQuestionHook.class);
        CopilotQuestionHook hook3 = mock(CopilotQuestionHook.class);

        when(hook1.getPriority()).thenReturn(3);
        when(hook2.getPriority()).thenReturn(1);
        when(hook3.getPriority()).thenReturn(2);

        List<CopilotQuestionHook> hookList = new ArrayList<>();
        hookList.add(hook1);
        hookList.add(hook2);
        hookList.add(hook3);

        when(mockQuestionHooks.iterator()).thenReturn(hookList.iterator());

        // When
        List<Object> sortedHooks = hookManager.sortHooksByPriority(mockQuestionHooks);

        // Then
        assertEquals(3, sortedHooks.size());
        assertEquals(hook2, sortedHooks.get(0)); // Priority 1
        assertEquals(hook3, sortedHooks.get(1)); // Priority 2
        assertEquals(hook1, sortedHooks.get(2)); // Priority 3
    }

    /**
     * Test execute hooks no hooks.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooks_NoHooks() throws Exception {
        // Given
        when(mockQuestionHooks.iterator()).thenReturn(new ArrayList<CopilotQuestionHook>().iterator());

        // When
        hookManager.executeHooks(mockApp, mockJsonRequest);

        // Then - No exception should be thrown
        // Verify no interactions with the app or json request
        verifyNoInteractions(mockApp, mockJsonRequest);
    }

    /**
     * Test execute hooks single hook.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooks_SingleHook() throws Exception {
        // Given
        CopilotQuestionHook mockHook = mock(CopilotQuestionHook.class);
        when(mockHook.typeCheck(mockApp)).thenReturn(true);

        List<CopilotQuestionHook> hookList = new ArrayList<>();
        hookList.add(mockHook);

        when(mockQuestionHooks.iterator()).thenReturn(hookList.iterator());

        // When
        hookManager.executeHooks(mockApp, mockJsonRequest);

        // Then
        verify(mockHook).typeCheck(mockApp);
        verify(mockHook).exec(mockApp, mockJsonRequest);
    }

    /**
     * Test execute hooks multiple hooks.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooks_MultipleHooks() throws Exception {
        // Given
        CopilotQuestionHook mockHook1 = mock(CopilotQuestionHook.class);
        CopilotQuestionHook mockHook2 = mock(CopilotQuestionHook.class);

        when(mockHook1.typeCheck(mockApp)).thenReturn(true);
        when(mockHook2.typeCheck(mockApp)).thenReturn(true);

        List<CopilotQuestionHook> hookList = new ArrayList<>();
        hookList.add(mockHook1);
        hookList.add(mockHook2);

        when(mockQuestionHooks.iterator()).thenReturn(hookList.iterator());

        // When
        hookManager.executeHooks(mockApp, mockJsonRequest);

        // Then
        verify(mockHook1).typeCheck(mockApp);
        verify(mockHook1).exec(mockApp, mockJsonRequest);
        verify(mockHook2).typeCheck(mockApp);
        verify(mockHook2).exec(mockApp, mockJsonRequest);
    }

    /**
     * Test execute hooks hook type check fails.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooks_HookTypeCheckFails() throws Exception {
        // Given
        CopilotQuestionHook mockHook = mock(CopilotQuestionHook.class);
        when(mockHook.typeCheck(mockApp)).thenReturn(false);

        List<CopilotQuestionHook> hookList = new ArrayList<>();
        hookList.add(mockHook);

        when(mockQuestionHooks.iterator()).thenReturn(hookList.iterator());

        // When
        hookManager.executeHooks(mockApp, mockJsonRequest);

        // Then
        verify(mockHook).typeCheck(mockApp);
        verify(mockHook, never()).exec(mockApp, mockJsonRequest);
    }

    /**
     * Test execute hooks exception thrown.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooks_ExceptionThrown() throws Exception {
        // Given
        CopilotQuestionHook mockHook = mock(CopilotQuestionHook.class);
        when(mockHook.typeCheck(mockApp)).thenReturn(true);
        doThrow(new RuntimeException("Test exception")).when(mockHook).exec(mockApp, mockJsonRequest);

        List<CopilotQuestionHook> hookList = new ArrayList<>();
        hookList.add(mockHook);

        when(mockQuestionHooks.iterator()).thenReturn(hookList.iterator());

        // Then
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Test exception");

        // When
        hookManager.executeHooks(mockApp, mockJsonRequest);
    }
}
