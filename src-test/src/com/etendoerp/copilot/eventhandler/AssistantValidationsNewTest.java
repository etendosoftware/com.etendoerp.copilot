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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;

/**
 * Unit tests for AssistantValidations.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssistantValidationsNewTest {
  private static final String ETCOP_TEMPERATURERANGE = "ETCOP_TemperatureRange";
  private static final String TEMPERATURE_FOR_S_OUT_OF_RANGE = "Temperature for %s out of range";
  private static final String TESTAPP = "TestApp";


  private AssistantValidations handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private CopilotApp copilotApp;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;

  /** Set up. */
  @Before
  public void setUp() {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotApp.class)).thenReturn(entity);

    handler = new AssistantValidations() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedOBMessageUtils.close();
  }

  /** Test on update valid temperature. */
  @Test
  public void testOnUpdateValidTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("1.0"));

    handler.onUpdate(updateEvent);
  }

  /** Test on update null temperature. */
  @Test
  public void testOnUpdateNullTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(null);

    handler.onUpdate(updateEvent);
  }

  /** Test on update zero temperature. */
  @Test
  public void testOnUpdateZeroTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(BigDecimal.ZERO);

    handler.onUpdate(updateEvent);
  }

  /** Test on update max temperature. */
  @Test
  public void testOnUpdateMaxTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("2"));

    handler.onUpdate(updateEvent);
  }

  /** Test on update negative temperature. */
  @Test(expected = OBException.class)
  public void testOnUpdateNegativeTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("-0.1"));
    when(copilotApp.getName()).thenReturn(TESTAPP);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(ETCOP_TEMPERATURERANGE))
        .thenReturn(TEMPERATURE_FOR_S_OUT_OF_RANGE);

    handler.onUpdate(updateEvent);
  }

  /** Test on update too high temperature. */
  @Test(expected = OBException.class)
  public void testOnUpdateTooHighTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("2.1"));
    when(copilotApp.getName()).thenReturn(TESTAPP);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(ETCOP_TEMPERATURERANGE))
        .thenReturn(TEMPERATURE_FOR_S_OUT_OF_RANGE);

    handler.onUpdate(updateEvent);
  }

  /** Test on save valid temperature. */
  @Test
  public void testOnSaveValidTemperature() {
    when(newEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("0.5"));

    handler.onSave(newEvent);
  }

  /** Test on save invalid temperature. */
  @Test(expected = OBException.class)
  public void testOnSaveInvalidTemperature() {
    when(newEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("3.0"));
    when(copilotApp.getName()).thenReturn(TESTAPP);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(ETCOP_TEMPERATURERANGE))
        .thenReturn(TEMPERATURE_FOR_S_OUT_OF_RANGE);

    handler.onSave(newEvent);
  }
}
