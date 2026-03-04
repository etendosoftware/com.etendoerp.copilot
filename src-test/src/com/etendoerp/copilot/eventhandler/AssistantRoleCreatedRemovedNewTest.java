package com.etendoerp.copilot.eventhandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.hibernate.criterion.Criterion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;

/**
 * Unit tests for AssistantRoleCreatedRemoved.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssistantRoleCreatedRemovedNewTest {

  private AssistantRoleCreatedRemoved handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<ModelProvider> mockedModelProvider;

  @Mock private EntityDeleteEvent deleteEvent;
  @Mock private CopilotApp copilotApp;
  @Mock private OBDal obDal;
  @Mock private ModelProvider modelProvider;
  @Mock private Entity entity;
  @Mock private OBCriteria<CopilotRoleApp> criteria;
  @Mock private CopilotRoleApp roleApp1;
  @Mock private CopilotRoleApp roleApp2;

  @Before
  public void setUp() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedModelProvider = mockStatic(ModelProvider.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    lenient().when(modelProvider.getEntity(CopilotApp.ENTITY_NAME)).thenReturn(entity);

    handler = new AssistantRoleCreatedRemoved() {
      @Override
      protected boolean isValidEvent(EntityPersistenceEvent event) {
        return true;
      }
    };

    lenient().when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(criteria);
    lenient().when(criteria.add(any(Criterion.class))).thenReturn(criteria);
  }

  @After
  public void tearDown() {
    mockedOBDal.close();
    mockedModelProvider.close();
  }

  @Test
  public void testOnDeleteRemovesRoleApps() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotApp);
    when(criteria.list()).thenReturn(Arrays.asList(roleApp1, roleApp2));

    handler.onDelete(deleteEvent);

    verify(obDal).remove(roleApp1);
    verify(obDal).remove(roleApp2);
  }

  @Test
  public void testOnDeleteEmptyList() {
    when(deleteEvent.getTargetInstance()).thenReturn(copilotApp);
    when(criteria.list()).thenReturn(Collections.emptyList());

    handler.onDelete(deleteEvent);

    verify(obDal, never()).remove(any());
  }
}
