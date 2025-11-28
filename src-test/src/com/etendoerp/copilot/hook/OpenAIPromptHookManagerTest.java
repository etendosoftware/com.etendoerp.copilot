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
package com.etendoerp.copilot.hook;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.apache.commons.lang3.StringUtils;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Open AI prompt hook manager test.
 */
public class OpenAIPromptHookManagerTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private Instance<OpenAIPromptHook> mockPromptHooks;

    @Mock
    private CopilotApp mockApp;

    private OpenAIPromptHookManager hookManager;

    private AutoCloseable mocks;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        hookManager = new OpenAIPromptHookManager();

        // Use reflection to inject the mock hooks
        java.lang.reflect.Field hooksField = OpenAIPromptHookManager.class.getDeclaredField("promptHooks");
        hooksField.setAccessible(true);
        hooksField.set(hookManager, mockPromptHooks);
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
    public void testSortHooksByPriorityEmptyHooks() {
        // Given
        when(mockPromptHooks.iterator()).thenReturn(new ArrayList<OpenAIPromptHook>().iterator());

        // When
        List<Object> sortedHooks = hookManager.sortHooksByPriority(mockPromptHooks);

        // Then
        assertTrue("Sorted hooks list should be empty", sortedHooks.isEmpty());
    }

    /**
     * Test sort hooks by priority multiple hooks.
     */
    @Test
    public void testSortHooksByPriorityMultipleHooks() {
        // Given
        OpenAIPromptHook hook1 = mock(OpenAIPromptHook.class);
        OpenAIPromptHook hook2 = mock(OpenAIPromptHook.class);
        OpenAIPromptHook hook3 = mock(OpenAIPromptHook.class);

        when(hook1.getPriority()).thenReturn(3);
        when(hook2.getPriority()).thenReturn(1);
        when(hook3.getPriority()).thenReturn(2);

        List<OpenAIPromptHook> hookList = List.of(hook1, hook2, hook3);
        when(mockPromptHooks.iterator()).thenReturn(hookList.iterator());

        // When
        List<Object> sortedHooks = hookManager.sortHooksByPriority(mockPromptHooks);

        // Then
        assertEquals("Sorted hooks should have 3 elements", 3, sortedHooks.size());
        assertEquals("First hook should have lowest priority", hook2, sortedHooks.get(0));
        assertEquals("Second hook should have middle priority", hook3, sortedHooks.get(1));
        assertEquals("Third hook should have highest priority", hook1, sortedHooks.get(2));
    }

    /**
     * Test execute hooks no hooks.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooksNoHooks() throws Exception {
        // Given
        when(mockPromptHooks.iterator()).thenReturn(new ArrayList<OpenAIPromptHook>().iterator());

        // When
        String result = hookManager.executeHooks(mockApp);

        // Then
        assertTrue("Result should be empty when no hooks", StringUtils.isEmpty(result));
    }

    /**
     * Test execute hooks single hook.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooksSingleHook() throws Exception {
        // Given
        OpenAIPromptHook mockHook = mock(OpenAIPromptHook.class);
        when(mockHook.typeCheck(mockApp)).thenReturn(true);
        when(mockHook.exec(mockApp)).thenReturn("Test Hook Context");
        when(mockPromptHooks.iterator()).thenReturn(List.of(mockHook).iterator());

        // When
        String result = hookManager.executeHooks(mockApp);

        // Then
        assertTrue("Result should contain hook context", StringUtils.contains(result, "Test Hook Context"));
        verify(mockHook).typeCheck(mockApp);
        verify(mockHook).exec(mockApp);
    }

    /**
     * Test execute hooks multiple hooks.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooksMultipleHooks() throws Exception {
        // Given
        OpenAIPromptHook hook1 = mock(OpenAIPromptHook.class);
        OpenAIPromptHook hook2 = mock(OpenAIPromptHook.class);

        when(hook1.getPriority()).thenReturn(1);
        when(hook2.getPriority()).thenReturn(2);

        when(hook1.typeCheck(mockApp)).thenReturn(true);
        when(hook2.typeCheck(mockApp)).thenReturn(true);

        when(hook1.exec(mockApp)).thenReturn("Hook 1 Context");
        when(hook2.exec(mockApp)).thenReturn("Hook 2 Context");

        when(mockPromptHooks.iterator()).thenReturn(List.of(hook1, hook2).iterator());

        // When
        String result = hookManager.executeHooks(mockApp);

        // Then
        assertTrue("Result should contain contexts from both hooks",
                StringUtils.contains(result, "Hook 1 Context") && StringUtils.contains(result, "Hook 2 Context"));
        verify(hook1).typeCheck(mockApp);
        verify(hook2).typeCheck(mockApp);
        verify(hook1).exec(mockApp);
        verify(hook2).exec(mockApp);
    }

    /**
     * Test execute hooks hook type check fails.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooksHookTypeCheckFails() throws Exception {
        // Given
        OpenAIPromptHook mockHook = mock(OpenAIPromptHook.class);
        when(mockHook.typeCheck(mockApp)).thenReturn(false);
        when(mockPromptHooks.iterator()).thenReturn(List.of(mockHook).iterator());

        // When
        String result = hookManager.executeHooks(mockApp);

        // Then
        assertEquals("Extra context information: \n", result);  // Changed assertion
        verify(mockHook).typeCheck(mockApp);
        verify(mockHook, never()).exec(mockApp);
    }

    /**
     * Test execute hooks exception thrown.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooksExceptionThrown() throws Exception {
        // Given
        OpenAIPromptHook mockHook = mock(OpenAIPromptHook.class);
        when(mockHook.typeCheck(mockApp)).thenThrow(new OBException("Test Exception"));
        when(mockPromptHooks.iterator()).thenReturn(List.of(mockHook).iterator());

        // Then
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Test Exception");

        // When
        hookManager.executeHooks(mockApp);
    }
}
