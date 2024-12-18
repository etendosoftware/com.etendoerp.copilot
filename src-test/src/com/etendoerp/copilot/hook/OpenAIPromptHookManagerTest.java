import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;

import com.etendoerp.copilot.data.CopilotApp;

@RunWith(MockitoJUnitRunner.class)
public class OpenAIPromptHookManagerTest {

    @Mock
    private Instance<OpenAIPromptHook> promptHooks;

    @InjectMocks
    private OpenAIPromptHookManager hookManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testExecuteHooks_noHooks() throws OBException {
        when(promptHooks.iterator()).thenReturn(Arrays.asList().iterator());

        CopilotApp app = mock(CopilotApp.class);
        String result = hookManager.executeHooks(app);

        assertEquals("Extra context information: \n", result);
    }

    @Test
    public void testExecuteHooks_withHooks() throws OBException {
        OpenAIPromptHook hook1 = mock(OpenAIPromptHook.class);
        OpenAIPromptHook hook2 = mock(OpenAIPromptHook.class);
        CopilotApp app = mock(CopilotApp.class);

        when(hook1.typeCheck(app)).thenReturn(true);
        when(hook2.typeCheck(app)).thenReturn(true);
        when(hook1.exec(app)).thenReturn("Hook1 executed");
        when(hook2.exec(app)).thenReturn("Hook2 executed");

        when(promptHooks.iterator()).thenReturn(Arrays.asList(hook1, hook2).iterator());

        String result = hookManager.executeHooks(app);

        assertTrue(result.contains("Hook1 executed"));
        assertTrue(result.contains("Hook2 executed"));
    }

    @Test(expected = OBException.class)
    public void testExecuteHooks_exception() throws OBException {
        when(promptHooks.iterator()).thenThrow(new RuntimeException("Error"));

        CopilotApp app = mock(CopilotApp.class);
        hookManager.executeHooks(app);
    }

    @Test
    public void testSortHooksByPriority() {
        OpenAIPromptHook hook1 = mock(OpenAIPromptHook.class);
        OpenAIPromptHook hook2 = mock(OpenAIPromptHook.class);

        when(hook1.getPriority()).thenReturn(10);
        when(hook2.getPriority()).thenReturn(5);

        when(promptHooks.iterator()).thenReturn(Arrays.asList(hook1, hook2).iterator());

        List<Object> sortedHooks = hookManager.sortHooksByPriority(promptHooks);

        assertEquals(hook2, sortedHooks.get(0));
        assertEquals(hook1, sortedHooks.get(1));
    }
}
