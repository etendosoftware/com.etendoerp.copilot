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
package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotModel;

/**
 * Test class for CopilotModelUtils.
 */
public class CopilotModelUtilsTest {

  private static final String DEFAULT_KEY = "default-key";

  private AutoCloseable mocks;
  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<Preferences> mockedPreferences;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Mock
  private OBDal obDal;
  @Mock
  private OBContext obContext;
  @Mock
  private Client client;
  @Mock
  private Organization organization;
  @Mock
  private User user;
  @Mock
  private Role role;
  @Mock
  private OBCriteria<CopilotModel> modelCriteria;

  /**
   * Sets up the test environment by initializing mocks and static mocked classes.
   */
  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedPreferences = mockStatic(Preferences.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.createCriteria(CopilotModel.class)).thenReturn(modelCriteria);

    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getCurrentOrganization()).thenReturn(organization);
    when(obContext.getUser()).thenReturn(user);
    when(obContext.getRole()).thenReturn(role);

    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(anyString())).thenAnswer(i -> i.getArgument(0));
  }

  /**
   * Cleans up the test environment by closing all mocked static objects.
   *
   * @throws Exception if an error occurs during teardown
   */
  @After
  public void tearDown() throws Exception {
    if (mockedOBDal != null) mockedOBDal.close();
    if (mockedOBContext != null) mockedOBContext.close();
    if (mockedPreferences != null) mockedPreferences.close();
    if (mockedOBMessageUtils != null) mockedOBMessageUtils.close();
    if (mocks != null) mocks.close();
  }

  // --- Tests for getProvider(CopilotApp) ---

  /**
   * Tests {@link CopilotModelUtils#getProvider(CopilotApp)} when the application is null.
   */
  @Test
  public void testGetProviderAppNull() {
    assertEquals(CopilotConstants.PROVIDER_OPENAI, CopilotModelUtils.getProvider(null));
  }

  /**
   * Tests {@link CopilotModelUtils#getProvider(CopilotApp)} when the model is null.
   */
  @Test
  public void testGetProviderModelNull() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getModel()).thenReturn(null);
    assertEquals(CopilotConstants.PROVIDER_OPENAI, CopilotModelUtils.getProvider(app));
  }

  /**
   * Tests {@link CopilotModelUtils#getProvider(CopilotApp)} when the provider is empty.
   */
  @Test
  public void testGetProviderProviderEmpty() {
    CopilotApp app = mock(CopilotApp.class);
    CopilotModel model = mock(CopilotModel.class);
    when(app.getModel()).thenReturn(model);
    when(model.getProvider()).thenReturn("");
    assertEquals(CopilotConstants.PROVIDER_OPENAI, CopilotModelUtils.getProvider(app));
  }

  /**
   * Tests {@link CopilotModelUtils#getProvider(CopilotApp)} when the provider is set.
   */
  @Test
  public void testGetProviderProviderSet() {
    CopilotApp app = mock(CopilotApp.class);
    CopilotModel model = mock(CopilotModel.class);
    when(app.getModel()).thenReturn(model);
    when(model.getProvider()).thenReturn("anthropic");
    assertEquals("anthropic", CopilotModelUtils.getProvider(app));
  }

  /**
   * Tests {@link CopilotModelUtils#getProvider(CopilotApp)} when an exception occurs.
   */
  @Test
  public void testGetProviderException() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getModel()).thenThrow(new RuntimeException("Error"));
    assertThrows(OBException.class, () -> CopilotModelUtils.getProvider(app));
  }

  // --- Tests for getAppModel(CopilotApp) ---

  /**
   * Tests {@link CopilotModelUtils#getAppModel(CopilotApp)} with the default flow.
   */
  @Test
  public void testGetAppModelDefaultFlow() {
    CopilotApp app = mock(CopilotApp.class);
    CopilotModel model = mock(CopilotModel.class);
    when(app.getModel()).thenReturn(model);
    when(model.getSearchkey()).thenReturn("gpt-3.5");

    // Should behave like getAppModel(app, provider) but first branch returns early
    assertEquals("gpt-3.5", CopilotModelUtils.getAppModel(app));
  }

  /**
   * Tests {@link CopilotModelUtils#getAppModel(CopilotApp)} and checks if it calls the overloaded method with default values.
   */
  @Test
  public void testGetAppModelCallsOverloadedWithDefault() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getModel()).thenReturn(null);
    // getProvider will return 'openai'
    // then getAppModel(app, 'openai') will be called

    // Simulate getDefaultModel finding something
    CopilotModel defaultModel = mock(CopilotModel.class);
    when(defaultModel.getSearchkey()).thenReturn("default-model");
    when(defaultModel.isDefault()).thenReturn(true);

    when(modelCriteria.list()).thenReturn(List.of(defaultModel));
    when(modelCriteria.add(any())).thenReturn(modelCriteria);
    when(modelCriteria.addOrderBy(anyString(), anyBoolean())).thenReturn(modelCriteria);

    assertEquals("default-model", CopilotModelUtils.getAppModel(app));
  }

  // --- Tests for getAppModel(CopilotApp, String) ---

  /**
   * Tests {@link CopilotModelUtils#getAppModel(CopilotApp, String)} when the application has a model.
   */
  @Test
  public void testGetAppModelWithProviderAppHasModel() {
    CopilotApp app = mock(CopilotApp.class);
    CopilotModel model = mock(CopilotModel.class);
    when(app.getModel()).thenReturn(model);
    when(model.getSearchkey()).thenReturn("app-model");

    assertEquals("app-model", CopilotModelUtils.getAppModel(app, "any"));
  }

  /**
   * Tests {@link CopilotModelUtils#getAppModel(CopilotApp, String)} when it should use the default model.
   */
  @Test
  public void testGetAppModelWithProviderUseDefaultModel() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getModel()).thenReturn(null);

    CopilotModel defaultModel = mock(CopilotModel.class);
    when(defaultModel.getSearchkey()).thenReturn(DEFAULT_KEY);
    when(defaultModel.isDefault()).thenReturn(true);

    when(modelCriteria.list()).thenReturn(List.of(defaultModel));
    when(modelCriteria.add(any())).thenReturn(modelCriteria);

    assertEquals(DEFAULT_KEY, CopilotModelUtils.getAppModel(app, "test-provider"));
  }

  /**
   * Tests {@link CopilotModelUtils#getAppModel(CopilotApp, String)} when no default model is found.
   */
  @Test
  public void testGetAppModelWithProviderNoDefaultFound() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getModel()).thenReturn(null);

    when(modelCriteria.list()).thenReturn(List.of()); // No models found
    when(modelCriteria.add(any())).thenReturn(modelCriteria);

    assertThrows(OBException.class, () -> CopilotModelUtils.getAppModel(app, "test-provider"));
  }

  // --- Tests for getDefaultModel (and readOverrideDefaultModel) ---

  /**
   * Tests {@link CopilotModelUtils#getDefaultModel(String)} when the override preference exists.
   *
   * @throws Exception if an error occurs during the test
   */
  @Test
  public void testGetDefaultModelOverridePreferenceExists() throws Exception {
    // Setup preference
    mockedPreferences.when(() -> Preferences.getPreferenceValue(
        eq("ETCOP_DefaultModelOverride"), anyBoolean(), any(Client.class), any(), any(), any(), any()))
        .thenReturn("openai/override-model");

    // Setup override model query
    CopilotModel overrideModel = mock(CopilotModel.class);
    when(overrideModel.getSearchkey()).thenReturn("override-model");
    when(modelCriteria.add(any())).thenReturn(modelCriteria);
    when(modelCriteria.setMaxResults(1)).thenReturn(modelCriteria);
    when(modelCriteria.uniqueResult()).thenReturn(overrideModel);

    // Call indirectly via getDefaultModel
    CopilotModel result = CopilotModelUtils.getDefaultModel(null);
    assertNotNull(result);
    assertEquals("override-model", result.getSearchkey());
  }

  /**
   * Tests {@link CopilotModelUtils#getDefaultModel(String)} when the override preference is malformed.
   *
   * @throws Exception if an error occurs during the test
   */
  @Test
  public void testGetDefaultModelOverridePreferenceMalformed() throws Exception {
    mockedPreferences.when(() -> Preferences.getPreferenceValue(
        eq("ETCOP_DefaultModelOverride"), anyBoolean(), any(Client.class), any(), any(), any(), any()))
        .thenReturn("malformed-preference");

    // Should fall back to regular query
    CopilotModel dbDefault = mock(CopilotModel.class);
    when(dbDefault.isDefault()).thenReturn(true);
    when(modelCriteria.list()).thenReturn(List.of(dbDefault));
    when(modelCriteria.add(any())).thenReturn(modelCriteria);

    CopilotModel result = CopilotModelUtils.getDefaultModel("openai");
    assertNotNull(result);
    assertEquals(dbDefault, result);
  }

  /**
   * Tests {@link CopilotModelUtils#getDefaultModel(String)} when no override exists and the model is found in the database.
   */
  @Test
  public void testGetDefaultModelNoOverrideFoundInDB() {
    mockedPreferences.when(() -> Preferences.getPreferenceValue(anyString(), anyBoolean(), any(Client.class), any(), any(), any(), any()))
        .thenReturn(null);

    CopilotModel dbDefault = mock(CopilotModel.class);
    when(dbDefault.isDefault()).thenReturn(true);
    when(modelCriteria.list()).thenReturn(List.of(dbDefault));
    when(modelCriteria.add(any())).thenReturn(modelCriteria);

    CopilotModel result = CopilotModelUtils.getDefaultModel("openai");
    assertEquals(dbDefault, result);
  }

  // --- Tests for getModelProviderResult(CopilotApp) ---

  /**
   * Tests {@link CopilotModelUtils#getModelProviderResult(CopilotApp)} when the agent is null.
   */
  @Test
  public void testGetModelProviderResultAgentNull() {
    assertThrows(OBException.class, () -> CopilotModelUtils.getModelProviderResult(null));
  }

  /**
   * Tests {@link CopilotModelUtils#getModelProviderResult(CopilotApp)} when the agent has a model.
   */
  @Test
  public void testGetModelProviderResultAgentHasModel() {
    CopilotApp app = mock(CopilotApp.class);
    CopilotModel model = mock(CopilotModel.class);
    when(app.getModel()).thenReturn(model);
    when(model.getSearchkey()).thenReturn("agent-model");
    when(model.getProvider()).thenReturn("agent-provider");

    CopilotModelUtils.ModelProviderResult res = CopilotModelUtils.getModelProviderResult(app);
    assertEquals("agent-model", res.modelStr);
    assertEquals("agent-provider", res.providerStr);
  }

  /**
   * Tests {@link CopilotModelUtils#getModelProviderResult(CopilotApp)} when the agent has no model and should use the default.
   */
  @Test
  public void testGetModelProviderResultAgentNoModelUseDefault() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getModel()).thenReturn(null);

    // Default model setup
    CopilotModel dbDefault = mock(CopilotModel.class);
    when(dbDefault.isDefault()).thenReturn(true);
    when(dbDefault.getSearchkey()).thenReturn(DEFAULT_KEY);
    when(dbDefault.getProvider()).thenReturn("default-provider");

    when(modelCriteria.list()).thenReturn(List.of(dbDefault));
    when(modelCriteria.add(any())).thenReturn(modelCriteria);

    CopilotModelUtils.ModelProviderResult res = CopilotModelUtils.getModelProviderResult(app);
    assertEquals(DEFAULT_KEY, res.modelStr);
    assertEquals("default-provider", res.providerStr);
  }

  /**
   * Tests {@link CopilotModelUtils#getModelProviderResult(CopilotApp)} when no model is found.
   */
  @Test
  public void testGetModelProviderResultNoModelFound() {
    CopilotApp app = mock(CopilotApp.class);
    when(app.getModel()).thenReturn(null);
    when(modelCriteria.list()).thenReturn(List.of()); // No default models

    assertThrows(OBException.class, () -> CopilotModelUtils.getModelProviderResult(app));
  }

  // --- Test Private Constructor (Reflection) ---
  /**
   * Tests the private constructor of {@link CopilotModelUtils} using reflection.
   *
   * @throws Exception if an error occurs during the test
   */
  @Test
  public void testPrivateConstructor() throws Exception {
    java.lang.reflect.Constructor<CopilotModelUtils> constructor = CopilotModelUtils.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    CopilotModelUtils instance = constructor.newInstance();
    assertNotNull(instance);
  }

}
