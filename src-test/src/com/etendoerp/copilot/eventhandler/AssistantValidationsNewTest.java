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

  private AssistantValidations handler;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private EntityNewEvent newEvent;
  @Mock private CopilotApp copilotApp;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;

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

  @After
  public void tearDown() {
    mockedModelProvider.close();
    mockedOBMessageUtils.close();
  }

  @Test
  public void testOnUpdateValidTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("1.0"));

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnUpdateNullTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(null);

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnUpdateZeroTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(BigDecimal.ZERO);

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnUpdateMaxTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("2"));

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateNegativeTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("-0.1"));
    when(copilotApp.getName()).thenReturn("TestApp");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TemperatureRange"))
        .thenReturn("Temperature for %s out of range");

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateTooHighTemperature() {
    when(updateEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("2.1"));
    when(copilotApp.getName()).thenReturn("TestApp");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TemperatureRange"))
        .thenReturn("Temperature for %s out of range");

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnSaveValidTemperature() {
    when(newEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("0.5"));

    handler.onSave(newEvent);
  }

  @Test(expected = OBException.class)
  public void testOnSaveInvalidTemperature() {
    when(newEvent.getTargetInstance()).thenReturn(copilotApp);
    when(copilotApp.getTemperature()).thenReturn(new BigDecimal("3.0"));
    when(copilotApp.getName()).thenReturn("TestApp");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TemperatureRange"))
        .thenReturn("Temperature for %s out of range");

    handler.onSave(newEvent);
  }
}
