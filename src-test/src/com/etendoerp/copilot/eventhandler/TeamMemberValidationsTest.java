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

import com.etendoerp.copilot.data.CopilotApp;
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
import org.openbravo.base.model.Property;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import java.lang.reflect.Method;

import com.etendoerp.copilot.data.TeamMember;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for TeamMemberValidations
 */
public class TeamMemberValidationsTest extends WeldBaseTest {

    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private TeamMemberValidations validations;

    @Mock
    private EntityUpdateEvent updateEvent;

    @Mock
    private EntityNewEvent newEvent;

    @Mock
    private TeamMember teamMember;

    @Mock
    private CopilotApp copilotAppMember;

    @Mock
    private ModelProvider modelProvider;

    @Mock
    private Entity teamMemberEntity;

    @Mock
    private Property property;

    @Mock
    private OBDal obDal;

    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
    private AutoCloseable mocks;
    private static final String ASSISTANT_MEMBER_NAME = "Test copilotAppMember";

    @Before
    public void setUp() throws Exception {
        // Initialize mocks
        mocks = MockitoAnnotations.openMocks(this);
        validations = new TeamMemberValidations() {
            protected boolean isValidEvent(EntityPersistenceEvent event) {
                return true;
            }
        };

        // Mock static methods
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedOBDal = mockStatic(OBDal.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        // Set up mock behavior
        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);

        // Mock entity behavior
        when(modelProvider.getEntity(TeamMember.class)).thenReturn(teamMemberEntity);
        when(teamMemberEntity.getProperty(TeamMember.PROPERTY_MEMBER)).thenReturn(property);

        // Set up event behavior
        when(updateEvent.getTargetInstance()).thenReturn(teamMember);
        when(newEvent.getTargetInstance()).thenReturn(teamMember);
        when(teamMember.getEntity()).thenReturn(teamMemberEntity);

        // Set up default message behavior
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberNull"))
                .thenReturn("Team member cannot be null");
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_TeamMemberDesc"))
                .thenReturn("Member %s must have a description");

        // Prepare reflection for isValidEvent if needed
        Method isValidEventMethod = TeamMemberValidations.class.getSuperclass().getDeclaredMethod("isValidEvent", EntityPersistenceEvent.class);
        isValidEventMethod.setAccessible(true);
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
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }


    /**
     * Test on update valid team member.
     */
    @Test
    public void testOnUpdateValidTeamMember() {
        // Given
        when(teamMember.getMember()).thenReturn(copilotAppMember);
        when(copilotAppMember.getDescription()).thenReturn("Valid description");
        when(copilotAppMember.getName()).thenReturn(ASSISTANT_MEMBER_NAME);

        // When
        validations.onUpdate(updateEvent);

        // Then
        verify(teamMember, atLeastOnce()).getMember();
        verify(copilotAppMember, atLeastOnce()).getDescription();
    }

    /**
     * Test on save valid team member.
     */
    @Test
    public void testOnSaveValidTeamMember() {
        // Given
        when(teamMember.getMember()).thenReturn(copilotAppMember);
        when(copilotAppMember.getDescription()).thenReturn("Valid description");
        when(copilotAppMember.getName()).thenReturn(ASSISTANT_MEMBER_NAME);

        // When
        validations.onSave(newEvent);

        // Then
        verify(teamMember, atLeastOnce()).getMember();
        verify(copilotAppMember, atLeastOnce()).getDescription();
    }

    /**
     * Test on update null member.
     */
    @Test
    public void testOnUpdateNullMember() {
        // Given
        when(teamMember.getMember()).thenReturn(null);

        expectedException.expect(OBException.class);
        expectedException.expectMessage("Team member cannot be null");

        // When
        validations.onUpdate(updateEvent);
    }

    /**
     * Test on save empty description.
     */
    @Test
    public void testOnSaveEmptyDescription() {
        // Given
        when(teamMember.getMember()).thenReturn(copilotAppMember);
        when(copilotAppMember.getDescription()).thenReturn("");
        when(copilotAppMember.getName()).thenReturn(ASSISTANT_MEMBER_NAME);

        expectedException.expect(OBException.class);
        expectedException.expectMessage("Member Test copilotAppMember must have a description");

        // When
        validations.onSave(newEvent);
    }

    /**
     * Test on update null description.
     */
    @Test
    public void testOnUpdateNullDescription() {
        // Given
        when(teamMember.getMember()).thenReturn(copilotAppMember);
        when(copilotAppMember.getDescription()).thenReturn(null);
        when(copilotAppMember.getName()).thenReturn(ASSISTANT_MEMBER_NAME);

        expectedException.expect(OBException.class);
        expectedException.expectMessage("Member Test copilotAppMember must have a description");

        // When
        validations.onUpdate(updateEvent);
    }
}
