package com.etendoerp.copilot.eventhandler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

import java.lang.reflect.Method;

/**
 * Assistant Knowledge Base sync status handler test.
 */
public class AssistantKBSyncStatusHandlerTest extends WeldBaseTest {

    private AssistantKBSyncStatusHandler handler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private MockedStatic<CopilotAppInfoUtils> mockedCopilotAppInfoUtils;
    private AutoCloseable mocks;

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
        mockedCopilotAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);

        // Setup common mock behavior
        when(copilotAppSource.getEntity()).thenReturn(copilotAppSourceEntity);
        when(copilotAppSource.getEtcopApp()).thenReturn(copilotApp);
        when(copilotAppSourceEntity.getProperty(CopilotAppSource.PROPERTY_FILE)).thenReturn(fileProperty);
    }

    /**
     * Tear down.
     *
     * @throws Exception the exception
     */
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
        if (mockedCopilotAppInfoUtils != null) {
            mockedCopilotAppInfoUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test on update file changed.
     */
    @Test
    public void testOnUpdateFileChanged() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
        when(updateEvent.getPreviousState(fileProperty)).thenReturn("oldFile.txt");
        when(updateEvent.getCurrentState(fileProperty)).thenReturn("newFile.txt");

        // When
        handler.onUpdate(updateEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }

    /**
     * Test on update file unchanged.
     */
    @Test
    public void testOnUpdateFileUnchanged() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppSource);
        when(updateEvent.getPreviousState(fileProperty)).thenReturn("same.txt");
        when(updateEvent.getCurrentState(fileProperty)).thenReturn("same.txt");

        // When
        handler.onUpdate(updateEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp), never());
        verify(obDal, never()).save(any(CopilotApp.class));
    }

    /**
     * Test on save.
     */
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
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp), atLeastOnce());
    }

    /**
     * Test on delete.
     */
    @Test
    public void testOnDelete() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(copilotAppSource);
        when(copilotAppSource.getEtcopApp()).thenReturn(copilotApp);

        // When
        handler.onDelete(deleteEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }
}
