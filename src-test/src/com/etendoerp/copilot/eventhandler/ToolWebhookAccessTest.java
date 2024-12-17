package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.*;

import com.etendoerp.copilot.data.CopilotApp;
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
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.ToolWebhook;
import org.openbravo.model.ad.system.Language;

import java.lang.reflect.Method;

public class ToolWebhookAccessTest extends WeldBaseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ToolWebhookAccess handler;
    private MockedStatic<OBDal> mockedOBDal;
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
    private ToolWebhook toolWebhook;
    @Mock
    private Client systemClient;
    @Mock
    private Client regularClient;
    @Mock
    private OBDal obDal;
    @Mock
    private OBContext obContext;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private Entity entity;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new ToolWebhookAccess() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedOBDal = mockStatic(OBDal.class);
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedOBContext = mockStatic(OBContext.class);

        // Configure static mocks
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);

        // Configure common mock behavior
        when(modelProvider.getEntity(ToolWebhook.class)).thenReturn(entity);
        when(systemClient.getId()).thenReturn("0");
        when(regularClient.getId()).thenReturn("100");

        // Mock OBContext.getLanguage() to return a valid Language object
        Language mockLanguage = mock(Language.class);
        when(mockLanguage.getId()).thenReturn("en_US");
        when(obContext.getLanguage()).thenReturn(mockLanguage);

        // Prepare reflection for isValidEvent if needed
        isValidEventMethod = ToolWebhookAccess.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
        isValidEventMethod.setAccessible(true);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
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
    public void testOnUpdate_SystemAdmin_Success() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(toolWebhook);
        when(obContext.getCurrentClient()).thenReturn(systemClient);
        when(obDal.get(Client.class, "0")).thenReturn(systemClient);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(obDal).get(Client.class, "0");
    }

    @Test
    public void testOnUpdate_NonSystemAdmin_ThrowsException() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(toolWebhook);
        when(obContext.getCurrentClient()).thenReturn(regularClient);
        when(obDal.get(Client.class, "0")).thenReturn(systemClient);
        
        expectedException.expect(OBException.class);

        // When
        handler.onUpdate(updateEvent);
    }

    @Test
    public void testOnSave_SystemAdmin_Success() {
        // Given
        when(newEvent.getTargetInstance()).thenReturn(toolWebhook);
        when(obContext.getCurrentClient()).thenReturn(systemClient);
        when(obDal.get(Client.class, "0")).thenReturn(systemClient);

        // When
        handler.onSave(newEvent);

        // Then
        verify(obDal).get(Client.class, "0");
    }

    @Test
    public void testOnSave_NonSystemAdmin_ThrowsException() {
        // Given
        when(newEvent.getTargetInstance()).thenReturn(toolWebhook);
        when(obContext.getCurrentClient()).thenReturn(regularClient);
        when(obDal.get(Client.class, "0")).thenReturn(systemClient);
        
        expectedException.expect(OBException.class);

        // When
        handler.onSave(newEvent);
    }

    @Test
    public void testOnDelete_SystemAdmin_Success() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(toolWebhook);
        when(obContext.getCurrentClient()).thenReturn(systemClient);
        when(obDal.get(Client.class, "0")).thenReturn(systemClient);

        // When
        handler.onDelete(deleteEvent);

        // Then
        verify(obDal).get(Client.class, "0");
    }

    @Test
    public void testOnDelete_NonSystemAdmin_ThrowsException() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(toolWebhook);
        when(obContext.getCurrentClient()).thenReturn(regularClient);
        when(obDal.get(Client.class, "0")).thenReturn(systemClient);
        
        expectedException.expect(OBException.class);

        // When
        handler.onDelete(deleteEvent);
    }
}