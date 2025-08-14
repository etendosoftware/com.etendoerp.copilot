package com.etendoerp.copilot.eventhandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import com.etendoerp.copilot.data.CopilotApp;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Knowledge Base FIle sync status handler test.
 */
public class KBSyncStatusHandlerTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private KBSyncStatusHandler handler;
    private AutoCloseable mocks;

    @Mock
    private EntityUpdateEvent updateEvent;
    @Mock
    private EntityDeleteEvent deleteEvent;
    @Mock
    private EntityNewEvent newEvent;
    @Mock
    private CopilotFile copilotFile;
    @Mock
    private Entity fileEntity;
    @Mock
    private OBDal obDal;
    @Mock
    private OBCriteria<CopilotAppSource> criteria;
    @Mock
    private CopilotAppSource appSource;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private Property property;

    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<CopilotAppInfoUtils> mockedCopilotAppInfoUtils;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new KBSyncStatusHandler() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedOBDal = mockStatic(OBDal.class);
        mockedCopilotUtils = mockStatic(CopilotUtils.class);
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedCopilotAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);

        // Configure common mock behavior
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
        when(obDal.createCriteria(CopilotAppSource.class)).thenReturn(criteria);
        when(criteria.list()).thenReturn(Collections.singletonList(appSource));
        when(appSource.getEtcopApp()).thenReturn(copilotApp);
        when(copilotFile.getEntity()).thenReturn(fileEntity);
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
        if (mockedCopilotUtils != null) {
            mockedCopilotUtils.close();
        }
        if (mockedModelProvider != null) {
            mockedModelProvider.close();
        }
        if (mockedCopilotAppInfoUtils != null) {
            mockedCopilotAppInfoUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test on update with changed properties.
     */
    @Test
    public void testOnUpdateWithChangedProperties() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
        when(fileEntity.getProperty(CopilotFile.PROPERTY_NAME)).thenReturn(property);
        when(updateEvent.getPreviousState(property)).thenReturn("oldName");
        when(updateEvent.getCurrentState(property)).thenReturn("newName");

        // When
        handler.onUpdate(updateEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
    }

    /**
     * Test on update without changed properties.
     */
    @Test
    public void testOnUpdateWithoutChangedProperties() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
        when(fileEntity.getProperty(anyString())).thenReturn(property);
        when(updateEvent.getPreviousState(property)).thenReturn("sameName");
        when(updateEvent.getCurrentState(property)).thenReturn("sameName");

        // When
        handler.onUpdate(updateEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp), never());
        verify(obDal, never()).save(any(CopilotApp.class));
    }

    /**
     * Test on delete success.
     */
    @Test
    public void testOnDeleteSuccess() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(copilotFile);

        // When
        handler.onDelete(deleteEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
    }

    /**
     * Test on save no action.
     */
    @Test
    public void testOnSaveNoAction() {
        // Given
        when(newEvent.getTargetInstance()).thenReturn(copilotFile);

        // When
        handler.onSave(newEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp), never());
        verify(obDal, never()).save(any(CopilotApp.class));
    }

    /**
     * Test on update multiple properties.
     */
    @Test
    public void testOnUpdateMultipleProperties() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotFile);
        when(fileEntity.getProperty(anyString())).thenReturn(property);

        // Mock different values for different properties
        when(updateEvent.getPreviousState(property))
            .thenReturn("old")
            .thenReturn(true)
            .thenReturn("oldDesc");
        when(updateEvent.getCurrentState(property))
            .thenReturn("new")
            .thenReturn(false)
            .thenReturn("newDesc");

        // When
        handler.onUpdate(updateEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
    }

    /**
     * Test on delete multiple app sources.
     */
    @Test
    public void testOnDeleteMultipleAppSources() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(copilotFile);
        CopilotAppSource appSource2 = mock(CopilotAppSource.class);
        CopilotApp etcopApp2 = mock(CopilotApp.class);
        when(appSource2.getEtcopApp()).thenReturn(etcopApp2);
        when(criteria.list()).thenReturn(Arrays.asList(appSource, appSource2));

        // When
        handler.onDelete(deleteEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(etcopApp2));
        verify(obDal, never()).save(copilotApp);
        verify(obDal, never()).save(etcopApp2);
    }
}
