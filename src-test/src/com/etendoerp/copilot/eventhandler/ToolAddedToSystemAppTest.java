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
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import org.openbravo.model.ad.system.Language;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * System Assistant Tools test.
 */
public class ToolAddedToSystemAppTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ToolAddedToSystemApp handler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBContext> mockedOBContext;
    private AutoCloseable mocks;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

    @Mock
    private EntityUpdateEvent updateEvent;
    @Mock
    private EntityNewEvent newEvent;
    @Mock
    private EntityDeleteEvent deleteEvent;
    @Mock
    private CopilotAppTool copilotAppTool;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private Client client;
    @Mock
    private Client contextClient;
    @Mock
    private OBContext obContext;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private Entity entity;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new ToolAddedToSystemApp() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Setup static mocks
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedOBContext = mockStatic(OBContext.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        // Configure static mocks
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);

        // Configure common mock behavior
        when(modelProvider.getEntity(anyString())).thenReturn(entity);
        when(obContext.getCurrentClient()).thenReturn(contextClient);

        // Mock OBContext.getLanguage() to return a valid Language object
        Language mockLanguage = mock(Language.class);
        when(mockLanguage.getId()).thenReturn("en_US");
        when(obContext.getLanguage()).thenReturn(mockLanguage);
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
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test on update same client success.
     */
    @Test
    public void testOnUpdateSameClientSuccess() {
        // Given
        String clientId = "100";
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(contextClient.getId()).thenReturn(clientId);

        // When
        handler.onUpdate(updateEvent);

        // Then
        verify(obContext).getCurrentClient();
    }

    /**
     * Test on update different client throws exception.
     */
    @Test
    public void testOnUpdateDifferentClientThrowsException() {
        // Given
        when(updateEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn("100");
        when(contextClient.getId()).thenReturn("200");

        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_WrongClientApp"))
                .thenReturn("It is only possible to add a Skill/Tool in the same client that the Assistant was defined.");
        
        expectedException.expect(OBException.class);
        expectedException.expectMessage("It is only possible to add a Skill/Tool in the same client that the Assistant was defined.");

        // When
        handler.onUpdate(updateEvent);
    }

    /**
     * Test on save same client success.
     */
    @Test
    public void testOnSaveSameClientSuccess() {
        // Given
        String clientId = "100";
        when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(clientId);
        when(contextClient.getId()).thenReturn(clientId);

        // When
        handler.onSave(newEvent);

        // Then
        verify(obContext).getCurrentClient();
    }

    /**
     * Test on save different client throws exception.
     */
    @Test
    public void testOnSaveDifferentClientThrowsException() {
        // Given
        when(newEvent.getTargetInstance()).thenReturn(copilotAppTool);
        when(copilotAppTool.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getClient()).thenReturn(client);
        when(client.getId()).thenReturn("100");
        when(contextClient.getId()).thenReturn("200");

        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_WrongClientApp"))
                .thenReturn("It is only possible to add a Skill/Tool in the same client that the Assistant was defined.");

        expectedException.expect(OBException.class);
        expectedException.expectMessage("It is only possible to add a Skill/Tool in the same client that the Assistant was defined.");
        
        expectedException.expect(OBException.class);

        // When
        handler.onSave(newEvent);
    }
}
