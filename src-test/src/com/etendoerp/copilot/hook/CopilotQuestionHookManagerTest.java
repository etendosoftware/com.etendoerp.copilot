package com.etendoerp.copilot.hook;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;

@RunWith(MockitoJUnitRunner.class)
public class CopilotQuestionHookManagerTest {

    @Mock
    private Instance<CopilotQuestionHook> mockQuestionHooks;

    @Mock
    private CopilotQuestionHook mockHook1;

    @Mock
    private CopilotQuestionHook mockHook2;

    @InjectMocks
    private CopilotQuestionHookManager hookManager;

    private CopilotApp mockApp;
    private JSONObject mockJsonRequest;

    @Before
    public void setUp() throws Exception {
        mockApp = mock(CopilotApp.class);
        mockJsonRequest = mock(JSONObject.class);

        when(mockHook1.typeCheck(mockApp)).thenReturn(true);
        when(mockHook2.typeCheck(mockApp)).thenReturn(true);
        when(mockHook1.getPriority()).thenReturn(1);
        when(mockHook2.getPriority()).thenReturn(2);

        when(mockQuestionHooks.iterator()).thenReturn(Arrays.asList(mockHook1, mockHook2).iterator());
    }

    @Test
    public void testExecuteHooks() throws Exception {
        hookManager.executeHooks(mockApp, mockJsonRequest);

        verify(mockHook1).exec(mockApp, mockJsonRequest);
        verify(mockHook2).exec(mockApp, mockJsonRequest);
    }

    @Test
    public void testSortHooksByPriority() {
        List<Object> sortedHooks = hookManager.sortHooksByPriority(mockQuestionHooks);

        assertEquals(mockHook1, sortedHooks.get(0));
        assertEquals(mockHook2, sortedHooks.get(1));
    }

    @Test(expected = OBException.class)
    public void testExecuteHooksThrowsOBException() throws Exception {
        doThrow(new RuntimeException()).when(mockHook1).exec(mockApp, mockJsonRequest);

        hookManager.executeHooks(mockApp, mockJsonRequest);
    }
}
