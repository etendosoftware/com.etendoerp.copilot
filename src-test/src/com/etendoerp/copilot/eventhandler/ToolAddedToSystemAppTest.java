package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import org.openbravo.model.ad.system.Language;

import java.lang.reflect.Method;

public class ToolAddedToSystemAppTest extends WeldBaseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ToolAddedToSystemApp handler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBContext> mockedOBContext;
    private AutoCloseable mocks;
    private Method isValidEventMethod;

    @Mock
    private EntityUpdateEvent updateEvent;
    @Mock
    private EntityNewEvent newEvent;
    @Mock
    private EntityDeleteEvent deleteEvent;
    @Mock
    private CopilotAppTool copilotAppTool;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private Client client;
    @Mock
    private Client contextClient;
    @Mock
    private OBContext obContext;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private Entity entity;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new ToolAddedToSystemApp() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedOBContext = mockStatic(OBContext.class);

        // Configure static mocks
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);

        // Configure common mock behavior
        when(modelProvider.getEntity(anyString())).thenReturn(entity);
        when(obContext.getCurrentClient()).thenReturn(contextClient);

        // Mock OBContext.getLanguage() to return a valid Language object
        Language mockLanguage = mock(Language.class);
        when(mockLanguage.getId()).thenReturn("en_US");
        when(obContext.getLanguage()).thenReturn(mockLanguage);

        // Prepare reflection for isValidEvent if needed
        isValidEventMethod = ToolAddedToSystemApp.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
        isValidEventMethod.setAccessible(true);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedModelProvider != null) {
            mockedModelProvider.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void testOnUpdate_SameClient_Success() {
        // Given
        String clientId = "100";
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(contextClient.getId()).thenReturn(clientId);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(obContext).getCurrentClient();
    }

    @Test
    public void testOnUpdate_DifferentClient_ThrowsException() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn("100");
        when(contextClient.getId()).thenReturn("200");
        
        expectedException.expect(OBException.class);

        // When
        handler.onUpdate(updateEvent);
    }

    @Test
    public void testOnSave_SameClient_Success() {
        // Given
        String clientId = "100";
        when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(contextClient.getId()).thenReturn(clientId);

        // When
        handler.onSave(newEvent);

        // Then
        verify(obContext).getCurrentClient();
    }

    @Test
    public void testOnSave_DifferentClient_ThrowsException() {
        // Given
        when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn("100");
        when(contextClient.getId()).thenReturn("200");
        
        expectedException.expect(OBException.class);

        // When
        handler.onSave(newEvent);
    }
}