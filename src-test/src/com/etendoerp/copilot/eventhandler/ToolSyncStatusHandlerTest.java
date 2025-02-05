package com.etendoerp.copilot.eventhandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.hibernate.criterion.Criterion;
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
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.util.CopilotConstants;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tool sync status handler test.
 */
public class ToolSyncStatusHandlerTest extends WeldBaseTest {

    private ToolSyncStatusHandler handler;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private AutoCloseable mocks;

    @Mock
    private EntityUpdateEvent updateEvent;
    @Mock
    private EntityNewEvent newEvent;
    @Mock
    private EntityDeleteEvent deleteEvent;
    @Mock
    private CopilotTool copilotTool;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private CopilotAppTool copilotAppTool;
    @Mock
    private OBDal obDal;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private Entity entity;
    @Mock
    private Property property;
    @Mock
    private OBCriteria<CopilotAppTool> criteria;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new ToolSyncStatusHandler() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedOBDal = mockStatic(OBDal.class);
        mockedModelProvider = mockStatic(ModelProvider.class);

        // Configure static mocks
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);

        // Configure common mock behavior
        when(modelProvider.getEntity(CopilotTool.class)).thenReturn(entity);
        when(entity.getProperty(anyString())).thenReturn(property);
        when(obDal.createCriteria(CopilotAppTool.class)).thenReturn(criteria);
        when(criteria.add(any(Criterion.class))).thenReturn(criteria);
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
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test on update no property changes no status update.
     */
    @Test
    public void testOnUpdateNoPropertyChangesNoStatusUpdate() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotTool);
        when(copilotTool.getEntity()).thenReturn(entity);
        when(updateEvent.getPreviousState(any(Property.class))).thenReturn("value");
        when(updateEvent.getCurrentState(any(Property.class))).thenReturn("value");

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(obDal, never()).save(any(CopilotApp.class));
    }

    /**
     * Test on update property changed updates status.
     */
    @Test
    public void testOnUpdatePropertyChangedUpdatesStatus() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotTool);
        when(copilotTool.getEntity()).thenReturn(entity);
        when(updateEvent.getPreviousState(any(Property.class))).thenReturn("oldValue");
        when(updateEvent.getCurrentState(any(Property.class))).thenReturn("newValue");
        
        List<CopilotAppTool> appTools = Arrays.asList(copilotAppTool);
        when(criteria.list()).thenReturn(appTools);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(copilotApp).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(obDal).save(copilotApp);
    }

    /**
     * Test on delete updates status.
     */
    @Test
    public void testOnDeleteUpdatesStatus() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(copilotTool);
        List<CopilotAppTool> appTools = Arrays.asList(copilotAppTool);
        when(criteria.list()).thenReturn(appTools);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);

        // When
        handler.onDelete(deleteEvent);

        // Then
        verify(copilotApp).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(obDal).save(copilotApp);
    }

    /**
     * Test on update multiple apps updates all status.
     */
    @Test
    public void testOnUpdateMultipleAppsUpdatesAllStatus() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotTool);
        when(copilotTool.getEntity()).thenReturn(entity);
        when(updateEvent.getPreviousState(any(Property.class))).thenReturn("oldValue");
        when(updateEvent.getCurrentState(any(Property.class))).thenReturn("newValue");
        
        CopilotApp copilotApp2 = mock(CopilotApp.class);
        CopilotAppTool copilotAppTool2 = mock(CopilotAppTool.class);
        when(copilotAppTool2.getCopilotApp()).thenReturn(copilotApp2);
        
        List<CopilotAppTool> appTools = Arrays.asList(copilotAppTool, copilotAppTool2);
        when(criteria.list()).thenReturn(appTools);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(copilotApp).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(copilotApp2).setSyncStatus(CopilotConstants.PENDING_SYNCHRONIZATION_STATE);
        verify(obDal).save(copilotApp);
        verify(obDal).save(copilotApp2);
    }
}
