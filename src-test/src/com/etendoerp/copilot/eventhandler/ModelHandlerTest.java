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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotModel;

/**
 * Tests for {@link ModelHandler}.
 */
public class ModelHandlerTest {

  private ModelHandler handler;
  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBDal> mockedOBDal;
  private AutoCloseable mocks;

  @Mock
  private EntityUpdateEvent updateEvent;
  @Mock
  private org.openbravo.dal.service.OBDal obDal;
  @Mock
  private ModelProvider modelProvider;
  @Mock
  private Entity copilotModelEntity;
  @Mock
  private Property propDefault;
  @Mock
  private Property propDefaultOverride;
  @Mock
  private CopilotModel targetModel;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    handler = new ModelHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    // Mock static providers
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    when(modelProvider.getEntity(CopilotModel.class)).thenReturn(copilotModelEntity);

    mockedOBDal = mockStatic(OBDal.class);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);

    // Properties mapping
    when(copilotModelEntity.getProperty(CopilotModel.PROPERTY_DEFAULT)).thenReturn(propDefault);
    when(copilotModelEntity.getProperty(CopilotModel.PROPERTY_DEFAULTOVERRIDE)).thenReturn(propDefaultOverride);
    when(updateEvent.getTargetInstance()).thenReturn(targetModel);
  }

  @After
  public void tearDown() throws Exception {
    if (mockedModelProvider != null) {
      mockedModelProvider.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  public void testOnUpdateThrowsWhenNonOpenAIDefault() {
    // Given: current default true, previous false and provider != openai
    when(updateEvent.getCurrentState(propDefault)).thenReturn(true);
    when(updateEvent.getPreviousState(propDefault)).thenReturn(false);
    when(targetModel.getProvider()).thenReturn("anthropic");

    // Then: exception expected
    assertThrows(OBException.class, () -> handler.onUpdate(updateEvent));
  }

  @Test
  public void testOnUpdateClearsOtherDefaultsWhenOpenAI() {
    // Given: promoting this model to default, provider openai
    when(updateEvent.getCurrentState(propDefault)).thenReturn(true);
    when(updateEvent.getPreviousState(propDefault)).thenReturn(false);
    when(targetModel.getProvider()).thenReturn("openai");

    // Mock criteria to return existing default models
    CopilotModel other = org.mockito.Mockito.mock(CopilotModel.class);
    when(obDal.createCriteria(CopilotModel.class)).thenReturn(org.mockito.Mockito.mock(org.openbravo.dal.service.OBCriteria.class));
    org.openbravo.dal.service.OBCriteria<CopilotModel> crit = org.mockito.Mockito.mock(org.openbravo.dal.service.OBCriteria.class);
    when(obDal.createCriteria(CopilotModel.class)).thenReturn(crit);
    when(crit.add(any())).thenReturn(crit);
    when(crit.add(any())).thenReturn(crit);
    when(crit.list()).thenReturn(Arrays.asList(other));

    // When
    handler.onUpdate(updateEvent);

    // Then: other.setDefault(false) and save called
    verify(other).setDefault(false);
    verify(obDal).save(other);
  }

  @Test
  public void testOnUpdateDefaultOverrideThrowsIfOtherExists() {
    // Given: defaultOverride switched from false to true
    when(updateEvent.getCurrentState(propDefaultOverride)).thenReturn(true);
    when(updateEvent.getPreviousState(propDefaultOverride)).thenReturn(false);

    // Mock criteria uniqueResult returns another model
    org.openbravo.dal.service.OBCriteria<CopilotModel> crit = org.mockito.Mockito.mock(org.openbravo.dal.service.OBCriteria.class);
    when(obDal.createCriteria(CopilotModel.class)).thenReturn(crit);
    when(crit.add(any())).thenReturn(crit);
    when(crit.add(any())).thenReturn(crit);
    when(crit.setMaxResults(1)).thenReturn(crit);
    when(crit.uniqueResult()).thenReturn(org.mockito.Mockito.mock(CopilotModel.class));

    // Then
    assertThrows(OBException.class, () -> handler.onUpdate(updateEvent));
  }

  @Test
  public void testOnUpdateDefaultOverrideNoOther() {
    // Given: defaultOverride switched from false to true
    when(updateEvent.getCurrentState(propDefaultOverride)).thenReturn(true);
    when(updateEvent.getPreviousState(propDefaultOverride)).thenReturn(false);

    // Mock criteria uniqueResult returns null
    org.openbravo.dal.service.OBCriteria<CopilotModel> crit = org.mockito.Mockito.mock(org.openbravo.dal.service.OBCriteria.class);
    when(obDal.createCriteria(CopilotModel.class)).thenReturn(crit);
    when(crit.add(any())).thenReturn(crit);
    when(crit.add(any())).thenReturn(crit);
    when(crit.setMaxResults(1)).thenReturn(crit);
    when(crit.uniqueResult()).thenReturn(null);

    // When should not throw
    handler.onUpdate(updateEvent);
    //add assertion to verify that uniqueResult was called
    verify(crit).uniqueResult();
  }
}
