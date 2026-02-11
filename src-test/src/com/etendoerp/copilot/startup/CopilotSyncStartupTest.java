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
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.AppInfo;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.process.SyncAssistant;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;

public class CopilotSyncStartupTest extends WeldBaseTest {

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

  private TestableCopilotSyncStartup startup;
  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<CopilotAppInfoUtils> mockedUtils;
  private MockedStatic<OBContext> mockedOBContext;

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
  }

  @After
  public void tearDown() {
    if (mockedOBDal != null) mockedOBDal.close();
    if (mockedUtils != null) mockedUtils.close();
    if (mockedOBContext != null) mockedOBContext.close();
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

  @Test
  public void testInitialize_AppPendingSync() throws Exception {
    List<CopilotApp> apps = new ArrayList<>();
    apps.add(copilotApp);
    when(criteria.list()).thenReturn(apps);

    when(copilotApp.getId()).thenReturn("APP-ID-1");
    when(copilotApp.isSyncStartup()).thenReturn(true);

    List<AppInfo> infoList = new ArrayList<>();
    infoList.add(appInfo);
    when(copilotApp.getEtcopAppInfoList()).thenReturn(infoList);

    // Case: App is NOT synchronized
    when(appInfo.getSyncStatus()).thenReturn("DRAFT");

    // Setup behavior for executeSync
    when(syncAssistant.doExecute(any(), anyString())).thenReturn(new JSONObject());

    // Need to mock OBDal.get() inside executeSync
    when(obDal.get(CopilotApp.class, "APP-ID-1")).thenReturn(copilotApp);

    startup.initialize();

    // Verify logic inside executeSync
    verify(syncAssistant).doExecute(any(), anyString()); // Check arguments if needed

    verify(obDal).get(CopilotApp.class, "APP-ID-1");
    mockedUtils.verify(() -> CopilotAppInfoUtils.markAsSynchronized(copilotApp));
    verify(obDal).flush();
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
    List<CopilotApp> apps = new ArrayList<>();
    apps.add(copilotApp);
    when(criteria.list()).thenReturn(apps);

    when(copilotApp.getId()).thenReturn("APP-ID-1");
    when(copilotApp.isSyncStartup()).thenReturn(true);

    List<AppInfo> infoList = new ArrayList<>();
    infoList.add(appInfo);
    when(copilotApp.getEtcopAppInfoList()).thenReturn(infoList);
    when(appInfo.getSyncStatus()).thenReturn("DRAFT");

    // Sync throws exception
    when(syncAssistant.doExecute(any(), anyString())).thenThrow(new RuntimeException("Sync Failed"));

    startup.initialize();

    // Verify rollback called
    verify(obDal).rollbackAndClose();
    // And finally restore mode (called twice: once inside executeSync finally, once in initialize finally?)
    // Actually executeSync does not re-throw exception, it catches it.
    // So initialize logic finishes normally.
    // OBContext.setAdminMode is called in initialize AND executeSync.
    // restorePreviousMode called in finally of executeSync AND initialize.
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
