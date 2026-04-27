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
package com.etendoerp.copilot.startup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.SessionInfo;

import com.etendoerp.copilot.data.AppInfo;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.process.SyncAssistant;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;

/**
 * Test class for {@link CopilotSyncStartup}.
 * This class ensures that the startup synchronization process for Copilot apps works as expected.
 */
public class CopilotSyncStartupTest extends WeldBaseTest {

  private static final String APP_ID_1 = "APP-ID-1";

  @Mock
  private OBDal obDal;

  @Mock
  private OBCriteria<CopilotApp> criteria;

  @Mock
  private SyncAssistant syncAssistant;

  @Mock
  private CopilotApp copilotApp;

  @Mock
  private AppInfo appInfo;

  @Mock
  private Session session;

  @Mock
  private Query<Role> roleQuery;

  @Mock
  private OBProvider obProvider;

  private TestableCopilotSyncStartup startup;
  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<CopilotAppInfoUtils> mockedUtils;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBProvider> mockedOBProvider;
  private MockedStatic<SessionInfo> mockedSessionInfo;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    startup = new TestableCopilotSyncStartup();

    // Mock static classes
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);

    mockedUtils = mockStatic(CopilotAppInfoUtils.class);

    // Mock OBContext to avoid real DB interaction during setAdminMode/restorePreviousMode
    // Note: WeldBaseTest usually sets up a context, but we are completely mocking DAL here.
    mockedOBContext = mockStatic(OBContext.class);

    // Default behavior for OBDal
    when(obDal.createCriteria(CopilotApp.class)).thenReturn(criteria);
    when(obDal.getSession()).thenReturn(session);
    when(session.createQuery(anyString(), eq(Role.class))).thenReturn(roleQuery);
    when(roleQuery.list()).thenReturn(Collections.emptyList());

    mockedOBProvider = mockStatic(OBProvider.class);
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(obProvider);

    // Default: SessionInfo is already initialized so waitForSessionInfoInitialized() returns immediately.
    mockedSessionInfo = mockStatic(SessionInfo.class);
    mockedSessionInfo.when(SessionInfo::isInitialized).thenReturn(true);
  }

  @After
  public void tearDown() {
    if (mockedOBDal != null) mockedOBDal.close();
    if (mockedUtils != null) mockedUtils.close();
    if (mockedOBContext != null) mockedOBContext.close();
    if (mockedOBProvider != null) mockedOBProvider.close();
    if (mockedSessionInfo != null) mockedSessionInfo.close();
  }

  @Test
  public void testInitialize_NoApps() {
    when(criteria.list()).thenReturn(Collections.emptyList());

    startup.initialize();

    // Verify executeSync was NOT called (via runSyncThread)
    // Since runSyncThread is overridden to run immediately, checking if syncAssistant is created or used implies it ran
    // But createSyncAssistant only called inside executeSync.
    // So verify createSyncAssistant was NEVER called? We can't verify method call on 'startup' easily if not a spy.
    // But we can verify syncAssistant method calls if it was returned.
    // Or we rely on the fact that if executeSync was called, createSyncAssistant would be called.

    // Better: Check logs or verify criteria was called.
    verify(obDal).createCriteria(CopilotApp.class);

    // Verify syncAssistant was never touched (meaning executeSync didn't run)
    verify(syncAssistant, never()).doExecute(any(), any());
  }

  @Test
  public void testInitialize_AppSynced() {
    List<CopilotApp> apps = new ArrayList<>();
    apps.add(copilotApp);
    when(criteria.list()).thenReturn(apps);

    when(copilotApp.isSyncStartup()).thenReturn(true);

    List<AppInfo> infoList = new ArrayList<>();
    infoList.add(appInfo);
    when(copilotApp.getEtcopAppInfoList()).thenReturn(infoList);

    // Case: App is already synchronized
    when(appInfo.getSyncStatus()).thenReturn(CopilotConstants.SYNCHRONIZED_STATE);

    startup.initialize();

    verify(syncAssistant, never()).doExecute(any(), any());
  }

  private void setupPendingApp() throws Exception {
    List<CopilotApp> apps = new ArrayList<>();
    apps.add(copilotApp);
    when(criteria.list()).thenReturn(apps);

    when(copilotApp.getId()).thenReturn(APP_ID_1);
    when(copilotApp.isSyncStartup()).thenReturn(true);

    List<AppInfo> infoList = new ArrayList<>();
    infoList.add(appInfo);
    when(copilotApp.getEtcopAppInfoList()).thenReturn(infoList);
    when(appInfo.getSyncStatus()).thenReturn("DRAFT");

    when(syncAssistant.doExecute(any(), anyString())).thenReturn(new JSONObject());
    when(obDal.get(CopilotApp.class, APP_ID_1)).thenReturn(copilotApp);
  }

  @Test
  public void testInitialize_AppPendingSync() throws Exception {
    setupPendingApp();

    startup.initialize();

    // Verify logic inside executeSync
    verify(syncAssistant).doExecute(any(), anyString());

    verify(obDal, times(2)).get(CopilotApp.class, APP_ID_1);
    mockedUtils.verify(() -> CopilotAppInfoUtils.markAsSynchronized(copilotApp));
    verify(obDal, times(2)).flush();
    verify(obDal).commitAndClose();
  }

  @Test
  public void testInitialize_ExceptionHandling() {
    // Force an exception
    when(obDal.createCriteria(CopilotApp.class)).thenThrow(new RuntimeException("DB Error"));

    startup.initialize();

    // Verify we exited gracefully (caught exception) and restored context (mocked)
    mockedOBContext.verify(OBContext::restorePreviousMode);
  }

  @Test
  public void testInitialize_ExecuteSyncFails() throws Exception {
    setupPendingApp();

    // Override: Sync throws exception
    when(syncAssistant.doExecute(any(), anyString())).thenThrow(new RuntimeException("Sync Failed"));

    startup.initialize();

    // Verify rollback called
    verify(obDal).rollbackAndClose();
  }

  @Test
  public void testInitialize_EnsureRoleAccessCreatesNewRecord() throws Exception {
    setupPendingApp();

    // Mock: roleQuery returns a role so ensureRoleAccess is triggered
    Role adminRole = mock(Role.class);
    Organization roleOrg = mock(Organization.class);
    org.openbravo.model.ad.system.Client roleClient = mock(org.openbravo.model.ad.system.Client.class);
    when(adminRole.getOrganization()).thenReturn(roleOrg);
    when(adminRole.getClient()).thenReturn(roleClient);
    when(roleQuery.list()).thenReturn(Arrays.asList(adminRole));

    // Mock: CopilotRoleApp criteria — no existing record
    OBCriteria<CopilotRoleApp> roleAppCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(roleAppCrit);
    when(roleAppCrit.add(any())).thenReturn(roleAppCrit);
    when(roleAppCrit.uniqueResult()).thenReturn(null);

    CopilotRoleApp newRoleApp = mock(CopilotRoleApp.class);
    when(obProvider.get(CopilotRoleApp.class)).thenReturn(newRoleApp);

    // Exercise the client-specific HQL branch
    org.openbravo.model.ad.system.Client appClient = mock(org.openbravo.model.ad.system.Client.class);
    when(appClient.getId()).thenReturn("TEST_CLIENT");
    when(copilotApp.getClient()).thenReturn(appClient);

    startup.initialize();

    verify(newRoleApp).setOrganization(roleOrg);
    verify(newRoleApp).setClient(roleClient);
    verify(newRoleApp).setCopilotApp(copilotApp);
    verify(newRoleApp).setRole(adminRole);
    verify(obDal).save(newRoleApp);
  }

  @Test
  public void testInitialize_EnsureRoleAccessSkipsExistingRecord() throws Exception {
    setupPendingApp();

    Role adminRole = mock(Role.class);
    when(roleQuery.list()).thenReturn(Arrays.asList(adminRole));

    // Mock: CopilotRoleApp criteria — record already exists
    OBCriteria<CopilotRoleApp> roleAppCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(roleAppCrit);
    when(roleAppCrit.add(any())).thenReturn(roleAppCrit);
    when(roleAppCrit.uniqueResult()).thenReturn(mock(CopilotRoleApp.class));

    // Exercise the "0" client branch
    org.openbravo.model.ad.system.Client appClient = mock(org.openbravo.model.ad.system.Client.class);
    when(appClient.getId()).thenReturn("0");
    when(copilotApp.getClient()).thenReturn(appClient);

    startup.initialize();

    verify(obProvider, never()).get(CopilotRoleApp.class);
  }

  @Test
  public void testInitialize_AppWithEmptyAppInfoList() throws Exception {
    List<CopilotApp> apps = new ArrayList<>();
    apps.add(copilotApp);
    when(criteria.list()).thenReturn(apps);

    when(copilotApp.getId()).thenReturn(APP_ID_1);
    when(copilotApp.isSyncStartup()).thenReturn(true);
    when(copilotApp.getEtcopAppInfoList()).thenReturn(Collections.emptyList());

    when(syncAssistant.doExecute(any(), anyString())).thenReturn(new JSONObject());
    when(obDal.get(CopilotApp.class, APP_ID_1)).thenReturn(copilotApp);

    startup.initialize();

    verify(syncAssistant).doExecute(any(), anyString());
  }

  @Test
  public void testInitialize_WaitsForSessionInfoInitialized() throws Exception {
    setupPendingApp();

    // SessionInfo starts uninitialized then flips to initialized — sync must wait, then proceed.
    java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
    mockedSessionInfo.when(SessionInfo::isInitialized).thenAnswer(inv -> calls.incrementAndGet() >= 3);

    startup.initialize();

    // doExecute only runs after isInitialized() returns true; verify both that the wait polled
    // more than once and that sync still completed.
    org.junit.Assert.assertTrue("Expected SessionInfo.isInitialized to be polled multiple times",
        calls.get() >= 3);
    verify(syncAssistant).doExecute(any(), anyString());
  }

  /**
   * Subclass to override protected methods for testing
   */
  class TestableCopilotSyncStartup extends CopilotSyncStartup {
    @Override
    protected void runSyncThread(Runnable r) {
      // Run immediately in the same thread for testing
      r.run();
    }

    @Override
    protected SyncAssistant createSyncAssistant() {
      return syncAssistant;
    }
  }
}
