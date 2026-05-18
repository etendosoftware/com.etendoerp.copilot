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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;

import org.hibernate.criterion.Criterion;
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
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotModel;

/**
 * Unit tests for ModelHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class ModelHandlerNewTest {
  private static final String MODEL1 = "model1";


  private ModelHandler handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private EntityUpdateEvent updateEvent;
  @Mock private OBDal obDal;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity modelEntity;
  @Mock private Property defaultProp;
  @Mock private Property defaultOverrideProp;
  @Mock private CopilotModel copilotModel;
  @Mock private OBCriteria<CopilotModel> criteria;

  /**
   * Set up.
   * @throws Exception if an error occurs
   */
  @Before
  public void setUp() throws Exception {
    mockedOBDal = mockStatic(OBDal.class);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotModel.class)).thenReturn(modelEntity);
    lenient().when(modelEntity.getProperty(CopilotModel.PROPERTY_DEFAULT)).thenReturn(defaultProp);
    lenient().when(modelEntity.getProperty(CopilotModel.PROPERTY_DEFAULTOVERRIDE)).thenReturn(defaultOverrideProp);

    handler = new ModelHandler() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    // Force the static entities array to use our mocked entity
    Field entitiesField = ModelHandler.class.getDeclaredField("entities");
    entitiesField.setAccessible(true);
    entitiesField.set(null, new Entity[] { modelEntity });

    lenient().when(obDal.createCriteria(CopilotModel.class)).thenReturn(criteria);
    lenient().when(criteria.add(any(Criterion.class))).thenReturn(criteria);
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedOBDal.close();
    mockedModelProvider.close();
    mockedOBMessageUtils.close();
  }

  private void stubDefaultState(Boolean current, Boolean previous) {
    lenient().when(updateEvent.getCurrentState(defaultProp)).thenReturn(current);
    lenient().when(updateEvent.getPreviousState(defaultProp)).thenReturn(previous);
  }

  private void stubOverrideState(Boolean current, Boolean previous) {
    lenient().when(updateEvent.getCurrentState(defaultOverrideProp)).thenReturn(current);
    lenient().when(updateEvent.getPreviousState(defaultOverrideProp)).thenReturn(previous);
  }

  /** Test on update no default changes. */
  @Test
  public void testOnUpdateNoDefaultChanges() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.FALSE, Boolean.FALSE);
    stubOverrideState(Boolean.FALSE, Boolean.FALSE);

    handler.onUpdate(updateEvent);

    verify(obDal, never()).save(any(CopilotModel.class));
  }

  /** Test on update mark default open a i. */
  @Test
  public void testOnUpdateMarkDefaultOpenAI() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.TRUE, Boolean.FALSE);
    lenient().when(copilotModel.getProvider()).thenReturn("openai");
    stubOverrideState(Boolean.FALSE, Boolean.FALSE);

    CopilotModel otherModel = mock(CopilotModel.class);
    lenient().when(criteria.list()).thenReturn(Collections.singletonList(otherModel));
    lenient().when(copilotModel.getId()).thenReturn(MODEL1);

    handler.onUpdate(updateEvent);

    verify(otherModel).setDefault(false);
    verify(obDal).save(otherModel);
  }

  /** Test on update mark default non open a i. */
  @Test(expected = OBException.class)
  public void testOnUpdateMarkDefaultNonOpenAI() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.TRUE, Boolean.FALSE);
    lenient().when(copilotModel.getProvider()).thenReturn("anthropic");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_OpenAIModelsDefault"))
        .thenReturn("Only OpenAI models can be default");

    handler.onUpdate(updateEvent);
  }

  /** Test on update mark default override already exists. */
  @Test(expected = OBException.class)
  public void testOnUpdateMarkDefaultOverrideAlreadyExists() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.FALSE, Boolean.FALSE);
    stubOverrideState(Boolean.TRUE, Boolean.FALSE);
    lenient().when(copilotModel.getId()).thenReturn(MODEL1);

    CopilotModel otherModel = mock(CopilotModel.class);
    lenient().when(criteria.setMaxResults(1)).thenReturn(criteria);
    lenient().when(criteria.uniqueResult()).thenReturn(otherModel);

    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_OneModelDefault"))
        .thenReturn("Only one model can be default override");

    handler.onUpdate(updateEvent);
  }

  /** Test on update mark default override no existing. */
  @Test
  public void testOnUpdateMarkDefaultOverrideNoExisting() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.FALSE, Boolean.FALSE);
    stubOverrideState(Boolean.TRUE, Boolean.FALSE);
    lenient().when(copilotModel.getId()).thenReturn(MODEL1);

    lenient().when(criteria.setMaxResults(1)).thenReturn(criteria);
    lenient().when(criteria.uniqueResult()).thenReturn(null);

    handler.onUpdate(updateEvent);
  }

  /** Test on update default already true. */
  @Test
  public void testOnUpdateDefaultAlreadyTrue() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.TRUE, Boolean.TRUE);
    stubOverrideState(Boolean.FALSE, Boolean.FALSE);

    handler.onUpdate(updateEvent);

    verify(obDal, never()).save(any(CopilotModel.class));
  }
}
