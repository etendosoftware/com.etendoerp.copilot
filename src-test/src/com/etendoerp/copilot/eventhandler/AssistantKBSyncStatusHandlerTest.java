package com.etendoerp.copilot.eventhandler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

import java.lang.reflect.Method;

public class AssistantKBSyncStatusHandlerTest extends WeldBaseTest {

    private AssistantKBSyncStatusHandler handler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private AutoCloseable mocks;
    private Method isValidEventMethod;

    @Mock
    private EntityUpdateEvent updateEvent;
    @Mock
    private EntityNewEvent newEvent;
    @Mock
    private EntityDeleteEvent deleteEvent;
    @Mock
    private CopilotAppSource copilotAppSource;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private Entity copilotAppSourceEntity;
    @Mock
    private Property fileProperty;
    @Mock
    private OBDal obDal;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new AssistantKBSyncStatusHandler() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        when(modelProvider.getEntity(CopilotAppSource.class)).thenReturn(copilotAppSourceEntity);

        mockedOBDal = mockStatic(OBDal.class);
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);

        mockedCopilotUtils = mockStatic(CopilotUtils.class);

        // Setup common mock behavior
        when(copilotAppSource.getEntity()).thenReturn(copilotAppSourceEntity);
        when(copilotAppSource.getEtcopApp()).thenReturn(copilotApp);
        when(copilotAppSourceEntity.getProperty(CopilotAppSource.PROPERTY_FILE)).thenReturn(fileProperty);

        // Prepare reflection for isValidEvent if needed
        isValidEventMethod = AssistantKBSyncStatusHandler.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
        isValidEventMethod.setAccessible(true);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedModelProvider != null) {
            mockedModelProvider.close();
        }
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedCopilotUtils != null) {
            mockedCopilotUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void testOnUpdate_FileChanged() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
        when(updateEvent.getPreviousState(fileProperty)).thenReturn("oldFile.txt");
        when(updateEvent.getCurrentState(fileProperty)).thenReturn("newFile.txt");

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(copilotApp).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(obDal).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }

    @Test
    public void testOnUpdate_FileUnchanged() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
        when(updateEvent.getPreviousState(fileProperty)).thenReturn("same.txt");
        when(updateEvent.getCurrentState(fileProperty)).thenReturn("same.txt");

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(copilotApp, never()).setSyncStatus(anyString());
        verify(obDal, never()).save(any(CopilotApp.class));
    }

    @Test
    public void testOnSave() {
        // Given
        // Ensure the mock is properly configured
        when(newEvent.getTargetInstance()).thenReturn(copilotAppSource);
        when(copilotAppSource.getEtcopApp()).thenReturn(copilotApp);

        // Add verification of mock setup
        assertNotNull(newEvent.getTargetInstance());
        assertNotNull(copilotAppSource.getEtcopApp());

        // When
        handler.onSave(newEvent);

        // Then
        // Verify that setSyncStatus is called with the correct parameter
        verify(copilotApp, atLeastOnce()).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
    }

    @Test
    public void testOnDelete() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(copilotAppSource);
        when(copilotAppSource.getEtcopApp()).thenReturn(copilotApp);

        // When
        handler.onDelete(deleteEvent);

        // Then
        verify(copilotApp).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(obDal).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }
}