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
package com.etendoerp.copilot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.RoleInheritance;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.AgentMemory;
import com.etendoerp.copilot.data.CopilotApp;

/**
 * Test class for AgentMemoryHook.
 *
 * <p>This test suite validates the functionality of retrieving agent memory records
 * based on user context, including organization hierarchy, role inheritance, and
 * proper filtering of memory records.</p>
 */
public class AgentMemoryHookTest extends WeldBaseTest {

  private AgentMemoryHook agentMemoryHook;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBDal> mockedOBDal;
  private AutoCloseable mocks;

  @Mock
  private OBContext mockOBContext;

  @Mock
  private OBDal mockOBDal;

  @Mock
  private CopilotApp mockCopilotApp;

  @Mock
  private Organization mockOrganization;

  @Mock
  private OrganizationStructureProvider mockOrgStructureProvider;

  @Mock
  private User mockUser;

  @Mock
  private Role mockRole;

  @Mock
  private Session mockSession;

  @Mock
  private Query<AgentMemory> mockQuery;

  private static final String TEST_APP_ID = "test-app-id";
  private static final String TEST_USER_ID = "test-user-id";
  private static final String TEST_ROLE_ID = "test-role-id";
  private static final String TEST_ORG_ID = "test-org-id";
  private static final String PARENT_ORG_ID = "parent-org-id";
  private static final String PARENT_ROLE_ID = "parent-role-id";
  private static final String TEST_MEMORY_TEXT = "Test memory";

  /**
   * Set up test environment before each test.
   * Initializes mocks and configures static mock behavior.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    agentMemoryHook = new AgentMemoryHook();

    // Setup static mocks
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(mockOBContext);

    mockedOBDal = mockStatic(OBDal.class);
    mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);

    // Setup mock IDs
    when(mockCopilotApp.getId()).thenReturn(TEST_APP_ID);
    when(mockUser.getId()).thenReturn(TEST_USER_ID);
    when(mockRole.getId()).thenReturn(TEST_ROLE_ID);
    when(mockOrganization.getId()).thenReturn(TEST_ORG_ID);

    // Setup OBContext behavior
    when(mockOBContext.getOrganizationStructureProvider()).thenReturn(mockOrgStructureProvider);
    when(mockOBContext.getCurrentOrganization()).thenReturn(mockOrganization);
    when(mockOBContext.getUser()).thenReturn(mockUser);
    when(mockOBContext.getRole()).thenReturn(mockRole);

    // Setup organization hierarchy
    List<String> orgHierarchy = Arrays.asList(TEST_ORG_ID, PARENT_ORG_ID);
    when(mockOrgStructureProvider.getParentList(TEST_ORG_ID, true)).thenReturn(orgHierarchy);

    // Setup organization mocks
    Organization parentOrg = mock(Organization.class);
    when(parentOrg.getId()).thenReturn(PARENT_ORG_ID);
    when(mockOBDal.get(eq(Organization.class), eq(TEST_ORG_ID))).thenReturn(mockOrganization);
    when(mockOBDal.get(eq(Organization.class), eq(PARENT_ORG_ID))).thenReturn(parentOrg);

    // Setup role mocks - important for getRolesByInheritance
    when(mockOBDal.get(eq(Role.class), eq(TEST_ROLE_ID))).thenReturn(mockRole);
    doNothing().when(mockOBDal).refresh(any(Role.class));

    // Setup role inheritance (no inheritance by default)
    when(mockRole.getADRoleInheritanceList()).thenReturn(Collections.emptyList());

    // Setup session and query
    when(mockOBDal.getSession()).thenReturn(mockSession);
    when(mockSession.createQuery(anyString(), eq(AgentMemory.class))).thenReturn(mockQuery);
    when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
    when(mockQuery.setParameterList(anyString(), any(Collection.class))).thenReturn(mockQuery);
  }

  /**
   * Tear down test environment after each test.
   * Closes all mocks properly.
   *
   * @throws Exception if teardown fails
   */
  @After
  public void tearDown() throws Exception {
    if (mockedOBContext != null) {
      mockedOBContext.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mocks != null) {
      mocks.close();
    }
  }

  /**
   * Test successful execution with memory records found.
   */
  @Test
  public void testExec_WithMemoryRecords() {
    // Setup
    AgentMemory memory1 = mock(AgentMemory.class);
    AgentMemory memory2 = mock(AgentMemory.class);
    when(memory1.getTextField()).thenReturn("Memory 1 content");
    when(memory2.getTextField()).thenReturn("Memory 2 content");

    List<AgentMemory> memoryList = Arrays.asList(memory1, memory2);
    when(mockQuery.list()).thenReturn(memoryList);

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertNotNull(result);
    assertTrue(result.contains("Use the following relevant previous information"));
    assertTrue(result.contains("Memory 1 content"));
    assertTrue(result.contains("Memory 2 content"));
    verify(mockQuery).setParameter("agentId", TEST_APP_ID);
    verify(mockQuery).setParameter("userId", TEST_USER_ID);
  }

  /**
   * Test execution with no memory records found returns empty string.
   */
  @Test
  public void testExec_NoMemoryRecords() {
    // Setup
    when(mockQuery.list()).thenReturn(Collections.emptyList());

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertEquals("", result);
  }

  /**
   * Test execution with role inheritance.
   */
  @Test
  public void testExec_WithRoleInheritance() {
    // Setup
    Role parentRole = mock(Role.class);
    when(parentRole.getId()).thenReturn(PARENT_ROLE_ID);
    when(parentRole.getADRoleInheritanceList()).thenReturn(Collections.emptyList());

    // Setup OBDal.get() for parent role
    when(mockOBDal.get(eq(Role.class), eq(PARENT_ROLE_ID))).thenReturn(parentRole);

    RoleInheritance roleInheritance = mock(RoleInheritance.class);
    when(roleInheritance.isActive()).thenReturn(true);
    when(roleInheritance.getInheritFrom()).thenReturn(parentRole);

    when(mockRole.getADRoleInheritanceList()).thenReturn(Collections.singletonList(roleInheritance));

    AgentMemory memory = mock(AgentMemory.class);
    when(memory.getTextField()).thenReturn(TEST_MEMORY_TEXT);
    when(mockQuery.list()).thenReturn(Collections.singletonList(memory));

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertNotNull(result);
    assertTrue(result.contains(TEST_MEMORY_TEXT));
  }

  /**
   * Test execution with inactive role inheritance should be ignored.
   */
  @Test
  public void testExec_WithInactiveRoleInheritance() {
    // Setup
    Role parentRole = mock(Role.class);
    when(parentRole.getId()).thenReturn(PARENT_ROLE_ID);

    RoleInheritance roleInheritance = mock(RoleInheritance.class);
    when(roleInheritance.isActive()).thenReturn(false);
    when(roleInheritance.getInheritFrom()).thenReturn(parentRole);

    when(mockRole.getADRoleInheritanceList()).thenReturn(Collections.singletonList(roleInheritance));
    doNothing().when(mockOBDal).refresh(any(Role.class));

    AgentMemory memory = mock(AgentMemory.class);
    when(memory.getTextField()).thenReturn(TEST_MEMORY_TEXT);
    when(mockQuery.list()).thenReturn(Collections.singletonList(memory));

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify - Should still work but not include parent role in query
    assertNotNull(result);
  }

  /**
   * Test execution handles exceptions gracefully and returns empty string.
   */
  @Test
  public void testExec_ExceptionHandling() {
    // Setup
    when(mockOBContext.getOrganizationStructureProvider()).thenThrow(new RuntimeException("Test exception"));

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertEquals("", result);
  }

  /**
   * Test typeCheck always returns true.
   */
  @Test
  public void testTypeCheck_AlwaysReturnsTrue() {
    // Execute & Verify
    assertTrue(agentMemoryHook.typeCheck(mockCopilotApp));
    assertTrue(agentMemoryHook.typeCheck(null));
  }

  /**
   * Test execution with complex organization hierarchy.
   */
  @Test
  public void testExec_ComplexOrganizationHierarchy() {
    // Setup
    String grandparentOrgId = "grandparent-org-id";
    List<String> orgHierarchy = Arrays.asList(TEST_ORG_ID, PARENT_ORG_ID, grandparentOrgId);
    when(mockOrgStructureProvider.getParentList(TEST_ORG_ID, true)).thenReturn(orgHierarchy);

    Organization grandparentOrg = mock(Organization.class);
    when(grandparentOrg.getId()).thenReturn(grandparentOrgId);
    when(mockOBDal.get(eq(Organization.class), eq(grandparentOrgId))).thenReturn(grandparentOrg);

    AgentMemory memory = mock(AgentMemory.class);
    when(memory.getTextField()).thenReturn(TEST_MEMORY_TEXT);
    when(mockQuery.list()).thenReturn(Collections.singletonList(memory));

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertNotNull(result);
    assertTrue(result.contains(TEST_MEMORY_TEXT));
  }

  /**
   * Test execution with multiple role inheritance levels.
   */
  @Test
  public void testExec_MultipleRoleInheritanceLevels() {
    // Setup
    Role parentRole = mock(Role.class);
    when(parentRole.getId()).thenReturn(PARENT_ROLE_ID);

    Role grandparentRole = mock(Role.class);
    String grandparentRoleId = "grandparent-role-id";
    when(grandparentRole.getId()).thenReturn(grandparentRoleId);
    when(grandparentRole.getADRoleInheritanceList()).thenReturn(Collections.emptyList());

    // Setup OBDal.get() for all roles in the hierarchy
    when(mockOBDal.get(eq(Role.class), eq(PARENT_ROLE_ID))).thenReturn(parentRole);
    when(mockOBDal.get(eq(Role.class), eq(grandparentRoleId))).thenReturn(grandparentRole);

    RoleInheritance parentInheritance = mock(RoleInheritance.class);
    when(parentInheritance.isActive()).thenReturn(true);
    when(parentInheritance.getInheritFrom()).thenReturn(grandparentRole);

    when(parentRole.getADRoleInheritanceList()).thenReturn(Collections.singletonList(parentInheritance));

    RoleInheritance roleInheritance = mock(RoleInheritance.class);
    when(roleInheritance.isActive()).thenReturn(true);
    when(roleInheritance.getInheritFrom()).thenReturn(parentRole);

    when(mockRole.getADRoleInheritanceList()).thenReturn(Collections.singletonList(roleInheritance));

    AgentMemory memory = mock(AgentMemory.class);
    when(memory.getTextField()).thenReturn(TEST_MEMORY_TEXT);
    when(mockQuery.list()).thenReturn(Collections.singletonList(memory));

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertNotNull(result);
    assertTrue(result.contains(TEST_MEMORY_TEXT));
  }

  /**
   * Test execution with memory containing special characters.
   */
  @Test
  public void testExec_MemoryWithSpecialCharacters() {
    // Setup
    AgentMemory memory = mock(AgentMemory.class);
    when(memory.getTextField()).thenReturn("Memory with <html> & \"quotes\" and new\nlines");

    when(mockQuery.list()).thenReturn(Collections.singletonList(memory));

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertNotNull(result);
    assertTrue(result.contains("Memory with <html> & \"quotes\" and new\nlines"));
  }

  /**
   * Test execution with very long memory text.
   */
  @Test
  public void testExec_LongMemoryText() {
    // Setup
    AgentMemory memory = mock(AgentMemory.class);
    String longText = "This is a very long memory text. ".repeat(100);
    when(memory.getTextField()).thenReturn(longText);

    when(mockQuery.list()).thenReturn(Collections.singletonList(memory));

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertNotNull(result);
    assertTrue(result.contains(longText));
  }

  /**
   * Test execution with multiple memories formats output correctly.
   */
  @Test
  public void testExec_MultipleMemoriesFormatting() {
    // Setup
    List<AgentMemory> memories = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      AgentMemory memory = mock(AgentMemory.class);
      when(memory.getTextField()).thenReturn("Memory " + i);
      memories.add(memory);
    }
    when(mockQuery.list()).thenReturn(memories);

    // Execute
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify
    assertNotNull(result);
    assertTrue(result.startsWith("Use the following relevant previous information"));
    for (int i = 1; i <= 5; i++) {
      assertTrue(result.contains("- Memory " + i));
    }
  }

  /**
   * Test that circular role inheritance is handled (should not cause infinite loop).
   */
  @Test
  public void testExec_CircularRoleInheritance() {
    // Setup - Create a circular reference
    Role roleA = mock(Role.class);
    Role roleB = mock(Role.class);

    String roleAId = "role-a-id";
    String roleBId = "role-b-id";
    when(roleA.getId()).thenReturn(roleAId);
    when(roleB.getId()).thenReturn(roleBId);

    // Setup OBDal.get() for circular roles
    when(mockOBDal.get(eq(Role.class), eq(roleAId))).thenReturn(roleA);
    when(mockOBDal.get(eq(Role.class), eq(roleBId))).thenReturn(roleB);

    RoleInheritance inheritanceAtoB = mock(RoleInheritance.class);
    when(inheritanceAtoB.isActive()).thenReturn(true);
    when(inheritanceAtoB.getInheritFrom()).thenReturn(roleB);

    RoleInheritance inheritanceBtoA = mock(RoleInheritance.class);
    when(inheritanceBtoA.isActive()).thenReturn(true);
    when(inheritanceBtoA.getInheritFrom()).thenReturn(roleA);

    when(roleA.getADRoleInheritanceList()).thenReturn(Collections.singletonList(inheritanceAtoB));
    when(roleB.getADRoleInheritanceList()).thenReturn(Collections.singletonList(inheritanceBtoA));
    when(mockRole.getADRoleInheritanceList()).thenReturn(Collections.singletonList(inheritanceAtoB));

    AgentMemory memory = mock(AgentMemory.class);
    when(memory.getTextField()).thenReturn(TEST_MEMORY_TEXT);
    when(mockQuery.list()).thenReturn(Collections.singletonList(memory));

    // Execute - Should not hang
    String result = agentMemoryHook.exec(mockCopilotApp);

    // Verify - Should complete successfully
    assertNotNull(result);
  }
}
