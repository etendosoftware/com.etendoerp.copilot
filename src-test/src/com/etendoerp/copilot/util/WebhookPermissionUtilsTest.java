package com.etendoerp.copilot.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.junit.After;
import org.junit.Before;
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
import com.etendoerp.copilot.data.RoleWebhookaccessV;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;

/**
 * Unit tests for {@link WebhookPermissionUtils}.
 */
@RunWith(MockitoJUnitRunner.class)
public class WebhookPermissionUtilsTest {

  @Mock
  private OBDal mockOBDal;
  @Mock
  private OBProvider mockOBProvider;
  @Mock
  private Session mockSession;
  @Mock
  private Role mockRole;
  @Mock
  private CopilotApp mockApp;
  @Mock
  private OBCriteria<RoleWebhookaccessV> mockCriteria;
  @Mock
  private Client mockClient;
  @Mock
  private Organization mockOrg;

  private MockedStatic<OBDal> obDalStatic;
  private MockedStatic<OBProvider> obProviderStatic;

  @Before
  public void setUp() {
    obDalStatic = mockStatic(OBDal.class);
    obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);

    obProviderStatic = mockStatic(OBProvider.class);
    obProviderStatic.when(OBProvider::getInstance).thenReturn(mockOBProvider);

    when(mockOBDal.getSession()).thenReturn(mockSession);
    when(mockOBDal.createCriteria(RoleWebhookaccessV.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any(Criterion.class))).thenReturn(mockCriteria);

    lenient().when(mockRole.getId()).thenReturn("role-1");
    lenient().when(mockRole.getClient()).thenReturn(mockClient);
    lenient().when(mockRole.getOrganization()).thenReturn(mockOrg);
    lenient().when(mockApp.getId()).thenReturn("app-1");
  }

  @After
  public void tearDown() {
    if (obProviderStatic != null) obProviderStatic.close();
    if (obDalStatic != null) obDalStatic.close();
  }

  @Test
  public void testAssignMissingPermissionsNoMissing() {
    when(mockCriteria.list()).thenReturn(Collections.emptyList());

    WebhookPermissionUtils.assignMissingPermissions(mockRole, mockApp);

    verify(mockSession).clear();
    verify(mockOBDal, never()).flush();
    verify(mockOBProvider, never()).get(DefinedwebhookRole.class);
  }

  @Test
  public void testAssignMissingPermissionsCreatesOnePermission() {
    RoleWebhookaccessV mockPermInfo = mock(RoleWebhookaccessV.class);
    DefinedWebHook mockWebhook = mock(DefinedWebHook.class);
    when(mockPermInfo.getRole()).thenReturn(mockRole);
    when(mockPermInfo.getWebHook()).thenReturn(mockWebhook);

    List<RoleWebhookaccessV> missingList = new ArrayList<>();
    missingList.add(mockPermInfo);
    when(mockCriteria.list()).thenReturn(missingList);

    DefinedwebhookRole mockWebhookRole = mock(DefinedwebhookRole.class);
    when(mockOBProvider.get(DefinedwebhookRole.class)).thenReturn(mockWebhookRole);

    WebhookPermissionUtils.assignMissingPermissions(mockRole, mockApp);

    verify(mockWebhookRole).setClient(mockClient);
    verify(mockWebhookRole).setOrganization(mockOrg);
    verify(mockWebhookRole).setActive(true);
    verify(mockWebhookRole).setRole(mockRole);
    verify(mockWebhookRole).setSmfwheDefinedwebhook(mockWebhook);
    verify(mockOBDal).save(mockWebhookRole);
    verify(mockOBDal).flush();
  }

  @Test
  public void testAssignMissingPermissionsCreatesMultiple() {
    RoleWebhookaccessV perm1 = mock(RoleWebhookaccessV.class);
    RoleWebhookaccessV perm2 = mock(RoleWebhookaccessV.class);
    DefinedWebHook webhook1 = mock(DefinedWebHook.class);
    DefinedWebHook webhook2 = mock(DefinedWebHook.class);
    when(perm1.getRole()).thenReturn(mockRole);
    when(perm1.getWebHook()).thenReturn(webhook1);
    when(perm2.getRole()).thenReturn(mockRole);
    when(perm2.getWebHook()).thenReturn(webhook2);

    List<RoleWebhookaccessV> missingList = new ArrayList<>();
    missingList.add(perm1);
    missingList.add(perm2);
    when(mockCriteria.list()).thenReturn(missingList);

    DefinedwebhookRole wr1 = mock(DefinedwebhookRole.class);
    DefinedwebhookRole wr2 = mock(DefinedwebhookRole.class);
    when(mockOBProvider.get(DefinedwebhookRole.class)).thenReturn(wr1, wr2);

    WebhookPermissionUtils.assignMissingPermissions(mockRole, mockApp);

    verify(mockOBDal, times(2)).save(any(DefinedwebhookRole.class));
    verify(mockOBDal).flush();
  }

  @Test
  public void testAssignMissingPermissionsHandlesException() {
    // Simulate exception during criteria query
    when(mockCriteria.list()).thenThrow(new RuntimeException("DB error"));

    // Should not throw, but rollback
    WebhookPermissionUtils.assignMissingPermissions(mockRole, mockApp);

    verify(mockOBDal).rollbackAndClose();
  }
}
