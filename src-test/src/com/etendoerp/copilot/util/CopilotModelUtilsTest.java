package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.hibernate.criterion.Criterion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotModel;

/**
 * Unit tests for {@link CopilotModelUtils}.
 * Covers getProvider, getAppModel (both overloads), and getDefaultModel.
 */
@RunWith(MockitoJUnitRunner.class)
public class CopilotModelUtilsTest {

  @Mock
  private OBDal mockOBDal;
  @Mock
  private OBProvider mockOBProvider;
  @Mock
  private OBPropertiesProvider mockPropertiesProvider;
  @Mock
  private OBContext mockOBContext;
  @Mock
  private CopilotApp mockApp;
  @Mock
  private CopilotModel mockModel;
  @Mock
  private OBCriteria<CopilotModel> mockCriteria;

  private MockedStatic<OBDal> obDalStatic;
  private MockedStatic<OBProvider> obProviderStatic;
  private MockedStatic<OBPropertiesProvider> obPropsStatic;
  private MockedStatic<OBContext> obContextStatic;
  private MockedStatic<OBMessageUtils> obMessageStatic;
  private MockedStatic<CopilotUtils> copilotUtilsStatic;

  @Before
  public void setUp() {
    obDalStatic = mockStatic(OBDal.class);
    obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);

    obProviderStatic = mockStatic(OBProvider.class);
    obProviderStatic.when(OBProvider::getInstance).thenReturn(mockOBProvider);

    obPropsStatic = mockStatic(OBPropertiesProvider.class);
    obPropsStatic.when(OBPropertiesProvider::getInstance).thenReturn(mockPropertiesProvider);

    obContextStatic = mockStatic(OBContext.class);

    obMessageStatic = mockStatic(OBMessageUtils.class);
    obMessageStatic.when(() -> OBMessageUtils.messageBD(any(String.class))).thenReturn("No default model found for provider: %s");

    copilotUtilsStatic = mockStatic(CopilotUtils.class);

    lenient().when(mockOBDal.createCriteria(CopilotModel.class)).thenReturn(mockCriteria);
    lenient().when(mockCriteria.add(any(Criterion.class))).thenReturn(mockCriteria);
  }

  @After
  public void tearDown() {
    if (copilotUtilsStatic != null) copilotUtilsStatic.close();
    if (obMessageStatic != null) obMessageStatic.close();
    if (obContextStatic != null) obContextStatic.close();
    if (obPropsStatic != null) obPropsStatic.close();
    if (obProviderStatic != null) obProviderStatic.close();
    if (obDalStatic != null) obDalStatic.close();
  }

  // --- getProvider tests ---

  @Test
  public void testGetProviderReturnsModelProvider() {
    when(mockApp.getModel()).thenReturn(mockModel);
    when(mockModel.getProvider()).thenReturn("anthropic");

    String result = CopilotModelUtils.getProvider(mockApp);

    assertEquals("anthropic", result);
  }

  @Test
  public void testGetProviderReturnsOpenaiWhenAppIsNull() {
    String result = CopilotModelUtils.getProvider(null);

    assertEquals("openai", result);
  }

  @Test
  public void testGetProviderReturnsOpenaiWhenModelIsNull() {
    when(mockApp.getModel()).thenReturn(null);

    String result = CopilotModelUtils.getProvider(mockApp);

    assertEquals("openai", result);
  }

  @Test
  public void testGetProviderReturnsOpenaiWhenProviderIsEmpty() {
    when(mockApp.getModel()).thenReturn(mockModel);
    when(mockModel.getProvider()).thenReturn("");

    String result = CopilotModelUtils.getProvider(mockApp);

    assertEquals("openai", result);
  }

  @Test
  public void testGetProviderReturnsOpenaiWhenProviderIsNull() {
    when(mockApp.getModel()).thenReturn(mockModel);
    when(mockModel.getProvider()).thenReturn(null);

    String result = CopilotModelUtils.getProvider(mockApp);

    assertEquals("openai", result);
  }

  // --- getAppModel(CopilotApp) tests ---

  @Test
  public void testGetAppModelReturnsSearchkeyWhenModelSet() {
    when(mockApp.getModel()).thenReturn(mockModel);
    when(mockModel.getSearchkey()).thenReturn("gpt-4");

    String result = CopilotModelUtils.getAppModel(mockApp);

    assertEquals("gpt-4", result);
  }

  @Test
  public void testGetAppModelFallsBackToDefaultWhenModelIsNull() {
    when(mockApp.getModel()).thenReturn(null);

    // getProvider(null model) returns "openai"
    // getDefaultModel("openai") needs to return a model
    CopilotModel defaultModel = mock(CopilotModel.class);
    when(defaultModel.isDefaultOverride()).thenReturn(false);
    when(defaultModel.isDefault()).thenReturn(true);
    when(defaultModel.getSearchkey()).thenReturn("gpt-3.5-turbo");

    List<CopilotModel> modelList = new ArrayList<>();
    modelList.add(defaultModel);
    when(mockCriteria.list()).thenReturn(modelList);

    String result = CopilotModelUtils.getAppModel(mockApp);

    assertEquals("gpt-3.5-turbo", result);
  }

  @Test(expected = OBException.class)
  public void testGetAppModelThrowsWhenNoDefault() {
    when(mockApp.getModel()).thenReturn(null);
    when(mockCriteria.list()).thenReturn(Collections.emptyList());

    CopilotModelUtils.getAppModel(mockApp);
  }

  // --- getAppModel(CopilotApp, String) tests ---

  @Test
  public void testGetAppModelWithProviderReturnsSearchkey() {
    when(mockApp.getModel()).thenReturn(mockModel);
    when(mockModel.getSearchkey()).thenReturn("claude-3");

    String result = CopilotModelUtils.getAppModel(mockApp, "anthropic");

    assertEquals("claude-3", result);
  }

  @Test
  public void testGetAppModelWithProviderFallsBackToDefault() {
    when(mockApp.getModel()).thenReturn(null);

    CopilotModel defaultModel = mock(CopilotModel.class);
    when(defaultModel.isDefaultOverride()).thenReturn(false);
    when(defaultModel.isDefault()).thenReturn(true);
    when(defaultModel.getSearchkey()).thenReturn("gpt-4o");

    List<CopilotModel> modelList = new ArrayList<>();
    modelList.add(defaultModel);
    when(mockCriteria.list()).thenReturn(modelList);

    String result = CopilotModelUtils.getAppModel(mockApp, "openai");

    assertEquals("gpt-4o", result);
  }

  @Test(expected = OBException.class)
  public void testGetAppModelWithProviderThrowsWhenNoDefault() {
    when(mockApp.getModel()).thenReturn(null);
    when(mockCriteria.list()).thenReturn(Collections.emptyList());

    CopilotModelUtils.getAppModel(mockApp, "openai");
  }

  @Test
  public void testGetAppModelWithProviderReturnsNullSearchkeyFallback() {
    // Model exists but searchkey is null -> falls back to default
    when(mockApp.getModel()).thenReturn(mockModel);
    when(mockModel.getSearchkey()).thenReturn(null);

    CopilotModel defaultModel = mock(CopilotModel.class);
    when(defaultModel.isDefaultOverride()).thenReturn(true);
    when(defaultModel.getSearchkey()).thenReturn("default-model");

    List<CopilotModel> modelList = new ArrayList<>();
    modelList.add(defaultModel);
    when(mockCriteria.list()).thenReturn(modelList);

    String result = CopilotModelUtils.getAppModel(mockApp, "openai");

    assertEquals("default-model", result);
  }

  // --- getDefaultModel tests ---

  @Test
  public void testGetDefaultModelReturnsOverrideFirst() {
    CopilotModel normalDefault = mock(CopilotModel.class);
    when(normalDefault.isDefaultOverride()).thenReturn(false);
    lenient().when(normalDefault.isDefault()).thenReturn(true);

    CopilotModel overrideDefault = mock(CopilotModel.class);
    when(overrideDefault.isDefaultOverride()).thenReturn(true);
    when(overrideDefault.getSearchkey()).thenReturn("override-model");

    List<CopilotModel> modelList = new ArrayList<>();
    modelList.add(normalDefault);
    modelList.add(overrideDefault);
    when(mockCriteria.list()).thenReturn(modelList);

    CopilotModel result = CopilotModelUtils.getDefaultModel("openai");

    assertNotNull(result);
    assertEquals("override-model", result.getSearchkey());
  }

  @Test
  public void testGetDefaultModelReturnsNormalDefaultWhenNoOverride() {
    CopilotModel normalDefault = mock(CopilotModel.class);
    when(normalDefault.isDefaultOverride()).thenReturn(false);
    when(normalDefault.isDefault()).thenReturn(true);
    when(normalDefault.getSearchkey()).thenReturn("normal-default");

    List<CopilotModel> modelList = new ArrayList<>();
    modelList.add(normalDefault);
    when(mockCriteria.list()).thenReturn(modelList);

    CopilotModel result = CopilotModelUtils.getDefaultModel("openai");

    assertNotNull(result);
    assertEquals("normal-default", result.getSearchkey());
  }

  @Test
  public void testGetDefaultModelReturnsNullWhenEmpty() {
    when(mockCriteria.list()).thenReturn(Collections.emptyList());

    CopilotModel result = CopilotModelUtils.getDefaultModel("openai");

    assertNull(result);
  }

  @Test
  public void testGetDefaultModelWithEmptyProvider() {
    CopilotModel defaultModel = mock(CopilotModel.class);
    when(defaultModel.isDefaultOverride()).thenReturn(false);
    when(defaultModel.isDefault()).thenReturn(true);
    when(defaultModel.getSearchkey()).thenReturn("any-default");

    List<CopilotModel> modelList = new ArrayList<>();
    modelList.add(defaultModel);
    when(mockCriteria.list()).thenReturn(modelList);

    CopilotModel result = CopilotModelUtils.getDefaultModel("");

    assertNotNull(result);
    assertEquals("any-default", result.getSearchkey());
  }

  @Test
  public void testGetDefaultModelWithNullProvider() {
    CopilotModel defaultModel = mock(CopilotModel.class);
    when(defaultModel.isDefaultOverride()).thenReturn(false);
    when(defaultModel.isDefault()).thenReturn(true);
    when(defaultModel.getSearchkey()).thenReturn("null-provider-default");

    List<CopilotModel> modelList = new ArrayList<>();
    modelList.add(defaultModel);
    when(mockCriteria.list()).thenReturn(modelList);

    CopilotModel result = CopilotModelUtils.getDefaultModel(null);

    assertNotNull(result);
    assertEquals("null-provider-default", result.getSearchkey());
  }

  @Test
  public void testGetDefaultModelNeitherDefaultNorOverride() {
    // Model in list but neither default nor override
    CopilotModel nonDefault = mock(CopilotModel.class);
    when(nonDefault.isDefaultOverride()).thenReturn(false);
    when(nonDefault.isDefault()).thenReturn(false);

    List<CopilotModel> modelList = new ArrayList<>();
    modelList.add(nonDefault);
    when(mockCriteria.list()).thenReturn(modelList);

    CopilotModel result = CopilotModelUtils.getDefaultModel("openai");

    assertNull(result);
  }

  // --- syncModels tests ---

  @Test(expected = OBException.class)
  public void testSyncModelsThrowsOnMissingProperties() {
    Properties props = new Properties();
    // No URL or branch set - will try to connect and fail
    when(mockPropertiesProvider.getOpenbravoProperties()).thenReturn(props);

    CopilotModelUtils.syncModels();
  }
}
