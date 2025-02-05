package com.etendoerp.copilot.eventhandler;

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
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.data.CopilotApp;
import org.openbravo.model.ad.system.Language;

import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Skill tool view handler test.
 */
public class SkillToolViewHandlerTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private SkillToolViewHandler handler;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBContext> mockedOBContext;
    private AutoCloseable mocks;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

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
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

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
    }

    /**
     * Tear down.
     *
     * @throws Exception the exception
     */
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
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test on update valid copilot app tool same client.
     */
    @Test
    public void testOnUpdateValidCopilotAppToolSameClient() {
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

    /**
     * Test on update valid team member same client.
     */
    @Test
    public void testOnUpdateValidTeamMemberSameClient() {
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

    /**
     * Test on update different client throws exception.
     */
    @Test
    public void testOnUpdateDifferentClientThrowsException() {
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

        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_errorClient"))
                .thenReturn("The current role does not have permission to modify, add or delete the assistant settings.");

        expectedException.expect(OBException.class);
        expectedException.expectMessage("The current role does not have permission to modify, add or delete the assistant settings.");

        // When
        handler.onUpdate(updateEvent);
    }

    /**
     * Test on save valid copilot app tool same client.
     */
    @Test
    public void testOnSaveValidCopilotAppToolSameClient() {
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

    /**
     * Test on delete valid copilot app tool same client.
     */
    @Test
    public void testOnDeleteValidCopilotAppToolSameClient() {
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

    /**
     * Test on delete null client throws exception.
     */
    @Test
    public void testOnDeleteNullClientThrowsException() {
        // Given
        String clientId = "100";
        when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(obDal.get(Client.class, clientId)).thenReturn(null);

        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_errorClient"))
                .thenReturn("The current role does not have permission to modify, add or delete the assistant settings.");

        expectedException.expect(OBException.class);
        expectedException.expectMessage("The current role does not have permission to modify, add or delete the assistant settings.");

        // When
        handler.onDelete(deleteEvent);
    }
}
