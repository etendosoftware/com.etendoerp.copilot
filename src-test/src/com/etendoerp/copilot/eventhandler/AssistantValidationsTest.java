package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.math.BigDecimal;

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
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;

public class AssistantValidationsTest extends WeldBaseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private AssistantValidations validations;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
    private AutoCloseable mocks;
    private Method isValidEventMethod;

    @Mock
    private EntityUpdateEvent updateEvent;
    @Mock
    private EntityNewEvent newEvent;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private Entity copilotEntity;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        validations = new AssistantValidations() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        when(modelProvider.getEntity(CopilotApp.class)).thenReturn(copilotEntity);

        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TemperatureRange"))
                .thenReturn("Temperature must be between 0 and 2 for assistant %s");

        // Setup common mock behavior
        when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
        when(newEvent.getTargetInstance()).thenReturn(copilotApp);
        when(copilotApp.getName()).thenReturn("TestAssistant");

        // Prepare reflection for isValidEvent if needed
        isValidEventMethod = AssistantValidations.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
        isValidEventMethod.setAccessible(true);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedModelProvider != null) {
            mockedModelProvider.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void testOnUpdate_ValidTemperature() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("1.5"));

        // When
        validations.onUpdate(updateEvent);

        // Then - no exception should be thrown
        verify(copilotApp, times(1)).getTemperature();
    }

    @Test
    public void testOnSave_ValidTemperature() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("1.0"));

        // When
        validations.onSave(newEvent);

        // Then - no exception should be thrown
        verify(copilotApp, times(1)).getTemperature();
    }

    @Test
    public void testOnUpdate_NullTemperature() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(null);

        // When
        validations.onUpdate(updateEvent);

        // Then - no exception should be thrown
        verify(copilotApp, times(1)).getTemperature();
    }

    @Test
    public void testOnUpdate_TemperatureTooHigh() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("2.1"));
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Temperature must be between 0 and 2 for assistant TestAssistant");

        // When
        validations.onUpdate(updateEvent);
    }

    @Test
    public void testOnUpdate_TemperatureTooLow() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("-0.1"));
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Temperature must be between 0 and 2 for assistant TestAssistant");

        // When
        validations.onUpdate(updateEvent);
    }

    @Test
    public void testOnSave_TemperatureTooHigh() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("2.1"));
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Temperature must be between 0 and 2 for assistant TestAssistant");

        // When
        validations.onSave(newEvent);
    }

    @Test
    public void testOnSave_TemperatureTooLow() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("-0.1"));
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Temperature must be between 0 and 2 for assistant TestAssistant");

        // When
        validations.onSave(newEvent);
    }
}