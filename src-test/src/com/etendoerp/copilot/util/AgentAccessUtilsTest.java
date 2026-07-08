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
package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hibernate.criterion.Criterion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;

/**
 * Unit tests for {@link AgentAccessUtils}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AgentAccessUtilsTest {

  @Mock
  private Role mockRole;
  @Mock
  private Client mockClient;
  @Mock
  private Organization mockOrg;

  /** Makes {@code mockRole} an eligible, active, non-System client-admin role. */
  private void makeEligible() {
    when(mockRole.isClientAdmin()).thenReturn(Boolean.TRUE);
    when(mockRole.isActive()).thenReturn(Boolean.TRUE);
    when(mockRole.getClient()).thenReturn(mockClient);
    when(mockClient.getId()).thenReturn("ABC123");
  }

  @Test
  public void skipsNonClientAdminRole() {
    when(mockRole.isClientAdmin()).thenReturn(Boolean.FALSE);

    try (MockedStatic<OBDal> obDal = mockStatic(OBDal.class)) {
      int created = AgentAccessUtils.ensureSharedAgentsGranted(mockRole);

      assertEquals(0, created);
      obDal.verifyNoInteractions();
    }
  }

  @Test
  public void skipsSystemClientRole() {
    when(mockRole.isClientAdmin()).thenReturn(Boolean.TRUE);
    when(mockRole.isActive()).thenReturn(Boolean.TRUE);
    when(mockRole.getClient()).thenReturn(mockClient);
    when(mockClient.getId()).thenReturn("0");

    try (MockedStatic<OBDal> obDal = mockStatic(OBDal.class)) {
      int created = AgentAccessUtils.ensureSharedAgentsGranted(mockRole);

      assertEquals(0, created);
      obDal.verifyNoInteractions();
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void grantsOnlyTheMissingSharedAgents() {
    makeEligible();
    when(mockRole.getOrganization()).thenReturn(mockOrg);

    CopilotApp a1 = mock(CopilotApp.class);
    CopilotApp a2 = mock(CopilotApp.class);
    when(a1.getId()).thenReturn("app-1");
    when(a2.getId()).thenReturn("app-2");

    // Role already has a1 granted; a2 is missing.
    CopilotRoleApp existingGrant = mock(CopilotRoleApp.class);
    when(existingGrant.getCopilotApp()).thenReturn(a1);

    OBCriteria<CopilotApp> appCrit = mock(OBCriteria.class);
    OBCriteria<CopilotRoleApp> raCrit = mock(OBCriteria.class);
    CopilotRoleApp newGrant = mock(CopilotRoleApp.class);

    try (MockedStatic<OBDal> obDal = mockStatic(OBDal.class);
         MockedStatic<OBProvider> obProvider = mockStatic(OBProvider.class)) {
      OBDal dal = mock(OBDal.class);
      OBProvider provider = mock(OBProvider.class);
      obDal.when(OBDal::getInstance).thenReturn(dal);
      obProvider.when(OBProvider::getInstance).thenReturn(provider);

      when(dal.createCriteria(CopilotApp.class)).thenReturn(appCrit);
      when(appCrit.add(any(Criterion.class))).thenReturn(appCrit);
      when(appCrit.list()).thenReturn(Arrays.asList(a1, a2));

      when(dal.createCriteria(CopilotRoleApp.class)).thenReturn(raCrit);
      when(raCrit.add(any(Criterion.class))).thenReturn(raCrit);
      when(raCrit.list()).thenReturn(Collections.singletonList(existingGrant));

      when(provider.get(CopilotRoleApp.class)).thenReturn(newGrant);

      int created = AgentAccessUtils.ensureSharedAgentsGranted(mockRole);

      assertEquals(1, created);
      verify(newGrant).setCopilotApp(a2);
      verify(newGrant).setRole(mockRole);
      verify(newGrant).setClient(mockClient);
      verify(newGrant).setOrganization(mockOrg);
      verify(dal, times(1)).save(newGrant);
      verify(dal, times(1)).flush();
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void noOpAndNoFlushWhenAllSharedAgentsAlreadyGranted() {
    makeEligible();

    CopilotApp a1 = mock(CopilotApp.class);
    when(a1.getId()).thenReturn("app-1");

    CopilotRoleApp existingGrant = mock(CopilotRoleApp.class);
    when(existingGrant.getCopilotApp()).thenReturn(a1);

    OBCriteria<CopilotApp> appCrit = mock(OBCriteria.class);
    OBCriteria<CopilotRoleApp> raCrit = mock(OBCriteria.class);

    try (MockedStatic<OBDal> obDal = mockStatic(OBDal.class);
         MockedStatic<OBProvider> obProvider = mockStatic(OBProvider.class)) {
      OBDal dal = mock(OBDal.class);
      obDal.when(OBDal::getInstance).thenReturn(dal);

      when(dal.createCriteria(CopilotApp.class)).thenReturn(appCrit);
      when(appCrit.add(any(Criterion.class))).thenReturn(appCrit);
      when(appCrit.list()).thenReturn(Collections.singletonList(a1));

      when(dal.createCriteria(CopilotRoleApp.class)).thenReturn(raCrit);
      when(raCrit.add(any(Criterion.class))).thenReturn(raCrit);
      when(raCrit.list()).thenReturn(Collections.singletonList(existingGrant));

      int created = AgentAccessUtils.ensureSharedAgentsGranted(mockRole);

      assertEquals(0, created);
      verify(dal, never()).save(any());
      verify(dal, never()).flush();
      // Provider is never touched when there is nothing to create.
      obProvider.verifyNoInteractions();
    }
  }
}
