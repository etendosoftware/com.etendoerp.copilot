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
package com.etendoerp.copilot.eventhandler;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;

/**
 * Unit tests for AutoRegistrationAssistantHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class AutoRegistrationAssistantHandlerNewTest {
  private static final String EXECUTE = "execute";


  private AutoRegistrationAssistantHandler handler;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBProvider> mockedOBProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock private OBDal obDal;
  @Mock private OBContext obContext;
  @Mock private OBProvider obProvider;
  @Mock private Role role;
  @Mock private CopilotApp copilotApp;
  @Mock private CopilotRoleApp newRoleApp;
  @Mock private OBCriteria<CopilotRoleApp> criteria;

  /** Set up. */
  @Before
  public void setUp() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBProvider = mockStatic(OBProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(obProvider);

    lenient().when(obContext.getRole()).thenReturn(role);
    lenient().when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(criteria);
    lenient().when(criteria.add(any(Criterion.class))).thenReturn(criteria);
    lenient().when(criteria.setMaxResults(1)).thenReturn(criteria);

    handler = new AutoRegistrationAssistantHandler();
  }

  /** Tear down. */
  @After
  public void tearDown() {
    mockedOBDal.close();
    mockedOBContext.close();
    mockedOBProvider.close();
    mockedOBMessageUtils.close();
  }

  /**
   * Test execute new registration.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecuteNewRegistration() throws Exception {
    String appId = "testAppId";
    when(obDal.get(CopilotApp.class, appId)).thenReturn(copilotApp);
    when(criteria.uniqueResult()).thenReturn(null);
    when(obProvider.get(CopilotRoleApp.class)).thenReturn(newRoleApp);

    Method executeMethod = AutoRegistrationAssistantHandler.class.getDeclaredMethod(
        EXECUTE, Map.class, String.class);
    executeMethod.setAccessible(true);

    JSONObject result = (JSONObject) executeMethod.invoke(handler,
        new HashMap<String, Object>(), "{\"appId\": \"" + appId + "\"}");

    assertNotNull(result);
    verify(newRoleApp).setCopilotApp(copilotApp);
    verify(newRoleApp).setRole(role);
    verify(obDal).save(newRoleApp);
    verify(obDal).flush();
  }

  /**
   * Test execute already registered.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecuteAlreadyRegistered() throws Exception {
    String appId = "testAppId";
    CopilotRoleApp existingRoleApp = mock(CopilotRoleApp.class);
    when(obDal.get(CopilotApp.class, appId)).thenReturn(copilotApp);
    when(criteria.uniqueResult()).thenReturn(existingRoleApp);

    Method executeMethod = AutoRegistrationAssistantHandler.class.getDeclaredMethod(
        EXECUTE, Map.class, String.class);
    executeMethod.setAccessible(true);

    JSONObject result = (JSONObject) executeMethod.invoke(handler,
        new HashMap<String, Object>(), "{\"appId\": \"" + appId + "\"}");

    assertNotNull(result);
    verify(obDal, never()).save(any(CopilotRoleApp.class));
  }

  /**
   * Test execute invalid json.
   * @throws Exception if an error occurs
   */
  @Test
  public void testExecuteInvalidJson() throws Exception {
    Method executeMethod = AutoRegistrationAssistantHandler.class.getDeclaredMethod(
        EXECUTE, Map.class, String.class);
    executeMethod.setAccessible(true);

    JSONObject result = (JSONObject) executeMethod.invoke(handler,
        new HashMap<String, Object>(), "invalid json");

    assertNotNull(result);
    // Should have error in result since JSON parse will fail
  }
}
