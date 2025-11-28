/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.eventhandler;

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

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Assistant validations test.
 */
public class AssistantValidationsTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private AssistantValidations validations;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
    private AutoCloseable mocks;
    private static final String TEMP_ERROR_MSG = "Temperature must be between 0 and 2 for assistant TestAssistant";

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
        Method isValidEventMethod = AssistantValidations.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
        isValidEventMethod.setAccessible(true);
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
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test on update valid temperature.
     */
    @Test
    public void testOnUpdateValidTemperature() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("1.5"));

        // When
        validations.onUpdate(updateEvent);

        // Then - no exception should be thrown
        verify(copilotApp, times(1)).getTemperature();
    }

    /**
     * Test on save valid temperature.
     */
    @Test
    public void testOnSaveValidTemperature() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("1.0"));

        // When
        validations.onSave(newEvent);

        // Then - no exception should be thrown
        verify(copilotApp, times(1)).getTemperature();
    }

    /**
     * Test on update null temperature.
     */
    @Test
    public void testOnUpdateNullTemperature() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(null);

        // When
        validations.onUpdate(updateEvent);

        // Then - no exception should be thrown
        verify(copilotApp, times(1)).getTemperature();
    }

    /**
     * Test on update temperature too high.
     */
    @Test
    public void testOnUpdateTemperatureTooHigh() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("2.1"));
        expectedException.expect(OBException.class);
        expectedException.expectMessage(TEMP_ERROR_MSG);

        // When
        validations.onUpdate(updateEvent);
    }

    /**
     * Test on update temperature too low.
     */
    @Test
    public void testOnUpdateTemperatureTooLow() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("-0.1"));
        expectedException.expect(OBException.class);
        expectedException.expectMessage(TEMP_ERROR_MSG);

        // When
        validations.onUpdate(updateEvent);
    }

    /**
     * Test on save temperature too high.
     */
    @Test
    public void testOnSaveTemperatureTooHigh() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("2.1"));
        expectedException.expect(OBException.class);
        expectedException.expectMessage(TEMP_ERROR_MSG);

        // When
        validations.onSave(newEvent);
    }

    /**
     * Test on save temperature too low.
     */
    @Test
    public void testOnSaveTemperatureTooLow() {
        // Given
        when(copilotApp.getTemperature()).thenReturn(new BigDecimal("-0.1"));
        expectedException.expect(OBException.class);
        expectedException.expectMessage(TEMP_ERROR_MSG);

        // When
        validations.onSave(newEvent);
    }
}
