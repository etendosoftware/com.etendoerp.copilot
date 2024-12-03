package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.*;

import com.etendoerp.copilot.data.CopilotAppSource;
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

import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.data.CopilotApp;
import org.openbravo.model.ad.system.Language;

import java.lang.reflect.Method;

public class SkillToolViewHandlerTest extends WeldBaseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private SkillToolViewHandler handler;
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
    private CopilotAppTool copilotAppTool;
    @Mock
    private TeamMember teamMember;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private Client client;
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
        handler = new SkillToolViewHandler() {
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
        when(modelProvider.getEntity(CopilotAppTool.class)).thenReturn(entity);
        when(modelProvider.getEntity(TeamMember.class)).thenReturn(entity);
        when(obContext.getCurrentClient()).thenReturn(client);

        // Mock OBContext.getLanguage() to return a valid Language object
        Language mockLanguage = mock(Language.class);
        when(mockLanguage.getId()).thenReturn("en_US");
        when(obContext.getLanguage()).thenReturn(mockLanguage);

        // Prepare reflection for isValidEvent if needed
        isValidEventMethod = SkillToolViewHandler.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
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
    public void testOnUpdate_ValidCopilotAppTool_SameClient() {
        // Given
        String clientId = "100";
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(obDal.get(Client.class, clientId)).thenReturn(client);
        when(obContext.getCurrentClient()).thenReturn(client);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(obDal).get(Client.class, clientId);
    }

    @Test
    public void testOnUpdate_ValidTeamMember_SameClient() {
        // Given
        String clientId = "100";
        when(updateEvent.getTargetInstance()).thenReturn(teamMember);
        when(teamMember.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(obDal.get(Client.class, clientId)).thenReturn(client);
        when(obContext.getCurrentClient()).thenReturn(client);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(obDal).get(Client.class, clientId);
    }

    @Test
    public void testOnUpdate_DifferentClient_ThrowsException() {
        // Given
        String currentClientId = "100";
        String differentClientId = "200";
        Client differentClient = mock(Client.class);
        
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(differentClient);
        when(differentClient.getId()).thenReturn(differentClientId);
        when(client.getId()).thenReturn(currentClientId);
        when(obDal.get(Client.class, differentClientId)).thenReturn(differentClient);
        
        expectedException.expect(OBException.class);

        // When
        handler.onUpdate(updateEvent);
    }

    @Test
    public void testOnSave_ValidCopilotAppTool_SameClient() {
        // Given
        String clientId = "100";
        when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(obDal.get(Client.class, clientId)).thenReturn(client);
        when(obContext.getCurrentClient()).thenReturn(client);

        // When
        handler.onSave(newEvent);

        // Then
        verify(obDal).get(Client.class, clientId);
    }

    @Test
    public void testOnDelete_ValidCopilotAppTool_SameClient() {
        // Given
        String clientId = "100";
        when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(obDal.get(Client.class, clientId)).thenReturn(client);
        when(obContext.getCurrentClient()).thenReturn(client);

        // When
        handler.onDelete(deleteEvent);

        // Then
        verify(obDal).get(Client.class, clientId);
    }

    @Test
    public void testOnDelete_NullClient_ThrowsException() {
        // Given
        String clientId = "100";
        when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(obDal.get(Client.class, clientId)).thenReturn(null);
        
        expectedException.expect(OBException.class);

        // When
        handler.onDelete(deleteEvent);
    }
}