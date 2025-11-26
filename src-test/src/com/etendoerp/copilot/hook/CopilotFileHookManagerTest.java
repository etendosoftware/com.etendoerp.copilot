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

import static org.junit.Assert.assertEquals;

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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Copilot file hook manager test.
 */
public class CopilotFileHookManagerTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private Instance<CopilotFileHook> mockCopFileHooks;

    @Mock
    private CopilotFile mockCopilotFile;

    private CopilotFileHookManager hookManager;
    private AutoCloseable mocks;
    private static final String TEST_TYPE = "testType";

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        hookManager = new CopilotFileHookManager();
        
        // Use reflection to inject the mock hooks
        java.lang.reflect.Field hooksField = CopilotFileHookManager.class.getDeclaredField("copFileHooks");
        hooksField.setAccessible(true);
        hooksField.set(hookManager, mockCopFileHooks);
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
     * Test sort hooks by priority multiple hooks.
     */
    @Test
    public void testSortHooksByPriorityMultipleHooks() {
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

    /**
     * Test execute hooks successful execution.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooksSuccessfulExecution() throws Exception {
        // Given
        CopilotFileHook hook1 = mock(CopilotFileHook.class);
        CopilotFileHook hook2 = mock(CopilotFileHook.class);

        when(mockCopilotFile.getType()).thenReturn(TEST_TYPE);
        when(hook1.typeCheck(TEST_TYPE)).thenReturn(true);
        when(hook2.typeCheck(TEST_TYPE)).thenReturn(true);

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

    /**
     * Test execute hooks filtered by type.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooksFilteredByType() throws Exception {
        // Given
        CopilotFileHook hook1 = mock(CopilotFileHook.class);
        CopilotFileHook hook2 = mock(CopilotFileHook.class);

        when(mockCopilotFile.getType()).thenReturn(TEST_TYPE);
        when(hook1.typeCheck(TEST_TYPE)).thenReturn(true);
        when(hook2.typeCheck(TEST_TYPE)).thenReturn(false);

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

    /**
     * Test execute hooks exception handling.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExecuteHooksExceptionHandling() throws Exception {
        // Given
        CopilotFileHook hook1 = mock(CopilotFileHook.class);

        when(mockCopilotFile.getType()).thenReturn(TEST_TYPE);
        when(hook1.typeCheck(TEST_TYPE)).thenReturn(true);
        doThrow(new OBException("Test exception")).when(hook1).exec(mockCopilotFile);

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
