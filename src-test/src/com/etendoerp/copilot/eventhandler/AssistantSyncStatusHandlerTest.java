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
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.util.CopilotConstants;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Assistant sync status handler test.
 */
public class AssistantSyncStatusHandlerTest extends WeldBaseTest {
    private AssistantSyncStatusHandler assistantSyncStatusHandler;
    private MockedStatic<ModelProvider> mockedModelProvider;

    @Mock
    private EntityUpdateEvent mockUpdateEvent;
    @Mock
    private Entity mockAppEntity;
    @Mock
    private Property mockSyncStatusProp;

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

        // Configure ModelProvider
        when(ModelProvider.getInstance()).thenReturn(mock(ModelProvider.class));
        when(ModelProvider.getInstance().getEntity(CopilotApp.ENTITY_NAME)).thenReturn(mockAppEntity);
        when(mockAppEntity.getProperty(CopilotApp.PROPERTY_SYNCSTATUS)).thenReturn(mockSyncStatusProp);

        // Prepare reflection for isValidEvent if needed
        Method isValidEventMethod = AssistantSyncStatusHandler.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
        isValidEventMethod.setAccessible(true);
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        if (mockedModelProvider != null) mockedModelProvider.close();
    }

    /**
     * Test on update properties changed sync status updated.
     */
    @Test
    public void testOnUpdate_PropertiesChanged_SyncStatusUpdated() {
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

            // Given
            Property mockProperty = mock(Property.class);
            when(mockAppEntity.getProperty(property)).thenReturn(mockProperty);
            when(mockUpdateEvent.getPreviousState(mockProperty)).thenReturn("oldValue");
            when(mockUpdateEvent.getCurrentState(mockProperty)).thenReturn("newValue");

            // When
            assistantSyncStatusHandler.onUpdate(mockUpdateEvent);

            // Then
            verify(mockUpdateEvent).setCurrentState(
                eq(mockSyncStatusProp), 
                eq(CopilotConstants.PENDING_SYNCHRONIZATION_STATE)
            );
        }
    }

    /**
     * Test on update no properties changed no sync status update.
     */
    @Test
    public void testOnUpdate_NoPropertiesChanged_NoSyncStatusUpdate() {
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

            // Given
            Property mockProperty = mock(Property.class);
            when(mockAppEntity.getProperty(property)).thenReturn(mockProperty);
            when(mockUpdateEvent.getPreviousState(mockProperty)).thenReturn("sameValue");
            when(mockUpdateEvent.getCurrentState(mockProperty)).thenReturn("sameValue");

            // When
            assistantSyncStatusHandler.onUpdate(mockUpdateEvent);

            // Then
            verify(mockUpdateEvent, never()).setCurrentState(
                eq(mockSyncStatusProp), 
                eq(CopilotConstants.PENDING_SYNCHRONIZATION_STATE)
            );
        }
    }
}
