package com.etendoerp.copilot.hook;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotFile;

@RunWith(MockitoJUnitRunner.class)
public class CopilotFileHookManagerTest {

    @Mock
    private Instance<CopilotFileHook> mockCopFileHooks;

    @Mock
    private CopilotFileHook mockHook1;

    @Mock
    private CopilotFileHook mockHook2;

    @InjectMocks
    private CopilotFileHookManager hookManager;

    private CopilotFile copilotFile;

    @Before
    public void setUp() {
        copilotFile = new CopilotFile();
        copilotFile.setType("testType");

        when(mockHook1.typeCheck("testType")).thenReturn(true);
        when(mockHook2.typeCheck("testType")).thenReturn(true);
        when(mockHook1.getPriority()).thenReturn(1);
        when(mockHook2.getPriority()).thenReturn(2);

        List<CopilotFileHook> hooks = new ArrayList<>();
        hooks.add(mockHook1);
        hooks.add(mockHook2);

        when(mockCopFileHooks.iterator()).thenReturn(hooks.iterator());
    }

    @Test
    public void testExecuteHooks() throws OBException {
        hookManager.executeHooks(copilotFile);

        verify(mockHook1).exec(copilotFile);
        verify(mockHook2).exec(copilotFile);
    }

    @Test
    public void testSortHooksByPriority() {
        List<Object> sortedHooks = hookManager.sortHooksByPriority(mockCopFileHooks);

        assertEquals(mockHook1, sortedHooks.get(0));
        assertEquals(mockHook2, sortedHooks.get(1));
    }

    @Test(expected = OBException.class)
    public void testExecuteHooksThrowsOBException() throws OBException {
        doThrow(new RuntimeException()).when(mockHook1).exec(copilotFile);

        hookManager.executeHooks(copilotFile);
    }
}
