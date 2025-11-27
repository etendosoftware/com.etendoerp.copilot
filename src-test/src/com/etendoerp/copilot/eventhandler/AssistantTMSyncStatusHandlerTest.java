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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.TeamMember;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Assistant tm sync status handler test.
 */
public class AssistantTMSyncStatusHandlerTest extends WeldBaseTest {

    private AssistantTMSyncStatusHandler handler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private MockedStatic<CopilotAppInfoUtils> mockedCopilotAppInfoUtils;
    private AutoCloseable mocks;

    @Mock
    private EntityUpdateEvent updateEvent;
    @Mock
    private EntityNewEvent newEvent;
    @Mock
    private EntityDeleteEvent deleteEvent;
    @Mock
    private TeamMember teamMember;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private Entity teamMemberEntity;
    @Mock
    private Property memberProperty;
    @Mock
    private Property appProperty;
    @Mock
    private OBDal obDal;
    @Mock
    private User previousUser;
    @Mock
    private User currentUser;
    @Mock
    private CopilotApp previousApp;
    @Mock
    private CopilotApp currentApp;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new AssistantTMSyncStatusHandler() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        when(modelProvider.getEntity(TeamMember.class)).thenReturn(teamMemberEntity);

        mockedOBDal = mockStatic(OBDal.class);
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);

        mockedCopilotUtils = mockStatic(CopilotUtils.class);
        mockedCopilotAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);

        // Setup common mock behavior
        when(teamMember.getEntity()).thenReturn(teamMemberEntity);
        when(teamMember.getCopilotApp()).thenReturn(copilotApp);
        when(teamMemberEntity.getProperty(TeamMember.PROPERTY_MEMBER)).thenReturn(memberProperty);
        when(teamMemberEntity.getProperty(TeamMember.PROPERTY_COPILOTAPP)).thenReturn(appProperty);
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
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedCopilotUtils != null) {
            mockedCopilotUtils.close();
        }
        if (mockedCopilotAppInfoUtils != null) {
            mockedCopilotAppInfoUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test on update member changed.
     */
    @Test
    public void testOnUpdateMemberChanged() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(teamMember);
        when(updateEvent.getPreviousState(memberProperty)).thenReturn(previousUser);
        when(updateEvent.getCurrentState(memberProperty)).thenReturn(currentUser);
        when(updateEvent.getPreviousState(appProperty)).thenReturn(copilotApp);
        when(updateEvent.getCurrentState(appProperty)).thenReturn(copilotApp);

        // When
        handler.onUpdate(updateEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }

    /**
     * Test on update app changed.
     */
    @Test
    public void testOnUpdateAppChanged() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(teamMember);
        when(updateEvent.getPreviousState(memberProperty)).thenReturn(previousUser);
        when(updateEvent.getCurrentState(memberProperty)).thenReturn(previousUser);
        when(updateEvent.getPreviousState(appProperty)).thenReturn(previousApp);
        when(updateEvent.getCurrentState(appProperty)).thenReturn(currentApp);

        // When
        handler.onUpdate(updateEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }

    /**
     * Test on update no changes.
     */
    @Test
    public void testOnUpdateNoChanges() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(teamMember);
        when(updateEvent.getPreviousState(memberProperty)).thenReturn(previousUser);
        when(updateEvent.getCurrentState(memberProperty)).thenReturn(previousUser);
        when(updateEvent.getPreviousState(appProperty)).thenReturn(previousApp);
        when(updateEvent.getCurrentState(appProperty)).thenReturn(previousApp);

        // When
        handler.onUpdate(updateEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp), never());
        verify(obDal, never()).save(any(CopilotApp.class));
    }

    /**
     * Test on save.
     */
    @Test
    public void testOnSave() {
        // Given
        when(newEvent.getTargetInstance()).thenReturn(teamMember);

        // When
        handler.onSave(newEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }

    /**
     * Test on delete.
     */
    @Test
    public void testOnDelete() {
        // Given
        when(deleteEvent.getTargetInstance()).thenReturn(teamMember);

        // When
        handler.onDelete(deleteEvent);

        // Then
        mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsPendingSynchronization(copilotApp));
        verify(obDal, never()).save(copilotApp);
        mockedCopilotUtils.verify(() -> CopilotUtils.logIfDebug(anyString()));
    }
}
