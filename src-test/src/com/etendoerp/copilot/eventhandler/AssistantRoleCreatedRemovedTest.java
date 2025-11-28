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
import java.util.Collections;
import java.util.List;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Assistant role created removed test.
 */
public class AssistantRoleCreatedRemovedTest extends WeldBaseTest {
    private AssistantRoleCreatedRemoved assistantRoleCreatedRemoved;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<ModelProvider> mockedModelProvider;

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
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        if (mockedOBDal != null) mockedOBDal.close();
        if (mockedModelProvider != null) mockedModelProvider.close();
    }

    /**
     * Test on delete role app exists removed.
     */
    @Test
    public void testOnDeleteRoleAppExistsRemoved() {
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

    /**
     * Test on delete no role app no removal.
     */
    @Test
    public void testOnDeleteNoRoleAppNoRemoval() {
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
