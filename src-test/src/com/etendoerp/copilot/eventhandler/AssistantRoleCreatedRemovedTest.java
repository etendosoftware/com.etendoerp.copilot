package com.etendoerp.copilot.eventhandler;

import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;

public class AssistantRoleCreatedRemovedTest extends WeldBaseTest {
    private AssistantRoleCreatedRemoved assistantRoleCreatedRemoved;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private Method isValidEventMethod;

    @Mock
    private EntityDeleteEvent mockDeleteEvent;
    @Mock
    private CopilotApp mockCopilotApp;
    @Mock
    private OBCriteria<CopilotRoleApp> mockCriteria;
    @Mock
    private CopilotRoleApp mockCopilotRoleApp;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        assistantRoleCreatedRemoved = new AssistantRoleCreatedRemoved() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedOBDal = mockStatic(OBDal.class);
        mockedModelProvider = mockStatic(ModelProvider.class);

        // Configure ModelProvider
        Entity mockCopilotAppEntity = mock(Entity.class);
        when(ModelProvider.getInstance()).thenReturn(mock(ModelProvider.class));
        when(ModelProvider.getInstance().getEntity(CopilotApp.ENTITY_NAME)).thenReturn(mockCopilotAppEntity);

        // Configure OBDal
        when(OBDal.getInstance()).thenReturn(mock(OBDal.class));
        when(OBDal.getInstance().createCriteria(CopilotRoleApp.class)).thenReturn(mockCriteria);

        // Prepare reflection for isValidEvent if needed
        isValidEventMethod = AssistantRoleCreatedRemoved.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
        isValidEventMethod.setAccessible(true);
    }

    @After
    public void tearDown() {
        if (mockedOBDal != null) mockedOBDal.close();
        if (mockedModelProvider != null) mockedModelProvider.close();
    }

    @Test
    public void testOnDelete_RoleAppExists_Removed() {
        // Given
        when(mockDeleteEvent.getTargetInstance()).thenReturn(mockCopilotApp);
        when(mockCriteria.add(any())).thenReturn(mockCriteria);
        
        // Simulate finding associated CopilotRoleApps
        List<CopilotRoleApp> roleAppList = Collections.singletonList(mockCopilotRoleApp);
        when(mockCriteria.list()).thenReturn(roleAppList);

        // When
        assistantRoleCreatedRemoved.onDelete(mockDeleteEvent);

        // Then
        verify(OBDal.getInstance()).remove(mockCopilotRoleApp);
    }

    @Test
    public void testOnDelete_NoRoleApp_NoRemoval() {
        // Given
        when(mockDeleteEvent.getTargetInstance()).thenReturn(mockCopilotApp);
        when(mockCriteria.add(any())).thenReturn(mockCriteria);
        
        // Simulate no associated CopilotRoleApps
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        // When
        assistantRoleCreatedRemoved.onDelete(mockDeleteEvent);

        // Then
        verify(OBDal.getInstance(), never()).remove(any());
    }
}