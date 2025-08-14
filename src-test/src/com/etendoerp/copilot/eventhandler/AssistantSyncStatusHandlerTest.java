package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
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
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;

/**
 * Assistant sync status handler test.
 */
public class AssistantSyncStatusHandlerTest extends WeldBaseTest {
    private AssistantSyncStatusHandler assistantSyncStatusHandler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<CopilotAppInfoUtils> mockedCopilotAppInfoUtils;

    @Mock
    private EntityUpdateEvent mockUpdateEvent;
    @Mock
    private Entity mockAppEntity;
    @Mock
    private Property mockSyncStatusProp;

    private CopilotApp mockTargetApp;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        assistantSyncStatusHandler = new AssistantSyncStatusHandler() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedCopilotAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);

        // Configure ModelProvider
        when(ModelProvider.getInstance()).thenReturn(mock(ModelProvider.class));
        when(ModelProvider.getInstance().getEntity(CopilotApp.ENTITY_NAME)).thenReturn(mockAppEntity);

        // Prepare target instance for events
        mockTargetApp = mock(CopilotApp.class);
        when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTargetApp);
         // previously used CopilotApp property directly, now using CopilotAppInfoUtils
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        if (mockedModelProvider != null) mockedModelProvider.close();
        if (mockedCopilotAppInfoUtils != null) mockedCopilotAppInfoUtils.close();
    }

    /**
     * Test on update properties changed sync status updated.
     */
    @Test
    public void testOnUpdatePropertiesChangedSyncStatusUpdated() {
        // Test cases for different properties
        String[] properties = {
            CopilotApp.PROPERTY_PROMPT,
            CopilotApp.PROPERTY_NAME,
            CopilotApp.PROPERTY_APPTYPE,
            CopilotApp.PROPERTY_ORGANIZATION,
            CopilotApp.PROPERTY_DESCRIPTION,
            CopilotApp.PROPERTY_MODULE,
            CopilotApp.PROPERTY_SYSTEMAPP,
            CopilotApp.PROPERTY_PROVIDER,
            CopilotApp.PROPERTY_MODEL,
            CopilotApp.PROPERTY_TEMPERATURE
        };

        for (String property : properties) {
            // Reset mocks
            reset(mockUpdateEvent);
            // reset clears previous stubs; restore target instance stub
            when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTargetApp);
            // Reset static mock invocations to isolate each iteration
            mockedCopilotAppInfoUtils.reset();

            // Given
            Property mockProperty = mock(Property.class);
            when(mockAppEntity.getProperty(property)).thenReturn(mockProperty);
            when(mockUpdateEvent.getPreviousState(mockProperty)).thenReturn("oldValue");
            when(mockUpdateEvent.getCurrentState(mockProperty)).thenReturn("newValue");

            // When
            assistantSyncStatusHandler.onUpdate(mockUpdateEvent);

            // Then: CopilotAppInfoUtils.markAsPendingSynchronization must be invoked with the event target
            mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(mockTargetApp));
        }
    }

    /**
     * Test on update no properties changed no sync status update.
     */
    @Test
    public void testOnUpdateNoPropertiesChangedNoSyncStatusUpdate() {
        // Test cases for different properties
        String[] properties = {
            CopilotApp.PROPERTY_PROMPT,
            CopilotApp.PROPERTY_NAME,
            CopilotApp.PROPERTY_APPTYPE,
            CopilotApp.PROPERTY_ORGANIZATION,
            CopilotApp.PROPERTY_DESCRIPTION,
            CopilotApp.PROPERTY_MODULE,
            CopilotApp.PROPERTY_SYSTEMAPP,
            CopilotApp.PROPERTY_PROVIDER,
            CopilotApp.PROPERTY_MODEL,
            CopilotApp.PROPERTY_TEMPERATURE
        };

        for (String property : properties) {
            // Reset mocks
            reset(mockUpdateEvent);
            // reset clears previous stubs; restore target instance stub
            when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTargetApp);
            // Reset static mock invocations to isolate each iteration
            mockedCopilotAppInfoUtils.reset();

            // Given
            Property mockProperty = mock(Property.class);
            when(mockAppEntity.getProperty(property)).thenReturn(mockProperty);
            when(mockUpdateEvent.getPreviousState(mockProperty)).thenReturn("sameValue");
            when(mockUpdateEvent.getCurrentState(mockProperty)).thenReturn("sameValue");

            // When
            assistantSyncStatusHandler.onUpdate(mockUpdateEvent);

            // Then: CopilotAppInfoUtils.markAsPendingSynchronization should NOT be invoked
            mockedCopilotAppInfoUtils.verifyNoInteractions();
        }
    }
}
