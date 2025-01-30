package com.etendoerp.copilot.eventhandler;

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
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Assistant tool sync status handler test.
 */
public class AssistantToolSyncStatusHandlerTest extends WeldBaseTest {

    private AssistantToolSyncStatusHandler handler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private AutoCloseable mocks;

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
    private ModelProvider modelProvider;
    @Mock
    private Entity copilotAppToolEntity;
    @Mock
    private Property toolProperty;
    @Mock
    private OBDal obDal;
    @Mock
    private CopilotTool previousTool;
    @Mock
    private CopilotTool currentTool;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new AssistantToolSyncStatusHandler(){
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        when(modelProvider.getEntity(CopilotAppTool.class)).thenReturn(copilotAppToolEntity);

        mockedOBDal = mockStatic(OBDal.class);
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);

        mockedCopilotUtils = mockStatic(CopilotUtils.class);

        // Setup common mock behavior
        when(copilotAppTool.getEntity()).thenReturn(copilotAppToolEntity);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotAppToolEntity.getProperty(CopilotAppTool.PROPERTY_COPILOTTOOL)).thenReturn(toolProperty);
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
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test on update tool changed.
     */
    @Test
    public void testOnUpdate_ToolChanged() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(updateEvent.getPreviousState(toolProperty)).thenReturn(previousTool);
        when(updateEvent.getCurrentState(toolProperty)).thenReturn(currentTool);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(copilotApp).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(obDal).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }

    /**
     * Test on update tool unchanged.
     */
    @Test
    public void testOnUpdate_ToolUnchanged() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(updateEvent.getPreviousState(toolProperty)).thenReturn(previousTool);
        when(updateEvent.getCurrentState(toolProperty)).thenReturn(previousTool);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(copilotApp, never()).setSyncStatus(anyString());
        verify(obDal, never()).save(any(CopilotApp.class));
    }

    /**
     * Test on save.
     */
    @Test
    public void testOnSave() {
        // Given
        when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);

        // When
        handler.onSave(newEvent);

        // Then
        verify(copilotApp).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(obDal).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }

    /**
     * Test on delete.
     */
    @Test
    public void testOnDelete() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(copilotAppTool);

        // When
        handler.onDelete(deleteEvent);

        // Then
        verify(copilotApp).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(obDal).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }
}
