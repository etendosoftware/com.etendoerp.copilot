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

  @Test
  public void testOnUpdateNoDefaultChanges() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.FALSE, Boolean.FALSE);
    stubOverrideState(Boolean.FALSE, Boolean.FALSE);

    handler.onUpdate(updateEvent);

    verify(obDal, never()).save(any(CopilotModel.class));
  }

  @Test
  public void testOnUpdateMarkDefaultOpenAI() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.TRUE, Boolean.FALSE);
    lenient().when(copilotModel.getProvider()).thenReturn("openai");
    stubOverrideState(Boolean.FALSE, Boolean.FALSE);

    CopilotModel otherModel = mock(CopilotModel.class);
    lenient().when(criteria.list()).thenReturn(Collections.singletonList(otherModel));
    lenient().when(copilotModel.getId()).thenReturn("model1");

    handler.onUpdate(updateEvent);

    verify(otherModel).setDefault(false);
    verify(obDal).save(otherModel);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateMarkDefaultNonOpenAI() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.TRUE, Boolean.FALSE);
    lenient().when(copilotModel.getProvider()).thenReturn("anthropic");
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_OpenAIModelsDefault"))
        .thenReturn("Only OpenAI models can be default");

    handler.onUpdate(updateEvent);
  }

  @Test(expected = OBException.class)
  public void testOnUpdateMarkDefaultOverrideAlreadyExists() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.FALSE, Boolean.FALSE);
    stubOverrideState(Boolean.TRUE, Boolean.FALSE);
    lenient().when(copilotModel.getId()).thenReturn("model1");

    CopilotModel otherModel = mock(CopilotModel.class);
    lenient().when(criteria.setMaxResults(1)).thenReturn(criteria);
    lenient().when(criteria.uniqueResult()).thenReturn(otherModel);

    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_OneModelDefault"))
        .thenReturn("Only one model can be default override");

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnUpdateMarkDefaultOverrideNoExisting() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.FALSE, Boolean.FALSE);
    stubOverrideState(Boolean.TRUE, Boolean.FALSE);
    lenient().when(copilotModel.getId()).thenReturn("model1");

    lenient().when(criteria.setMaxResults(1)).thenReturn(criteria);
    lenient().when(criteria.uniqueResult()).thenReturn(null);

    handler.onUpdate(updateEvent);
  }

  @Test
  public void testOnUpdateDefaultAlreadyTrue() {
    lenient().when(updateEvent.getTargetInstance()).thenReturn(copilotModel);
    stubDefaultState(Boolean.TRUE, Boolean.TRUE);
    stubOverrideState(Boolean.FALSE, Boolean.FALSE);

    handler.onUpdate(updateEvent);

    verify(obDal, never()).save(any(CopilotModel.class));
  }
}
