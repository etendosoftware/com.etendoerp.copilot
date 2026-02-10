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
package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Language;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotModel;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotModelUtils;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.OpenAIUtils;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;

/**
 * Sync assistant test.
 */
public class SyncAssistantTest extends WeldBaseTest {
  /**
   * The Expected exception.
   */
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private SyncAssistant syncAssistant;
  private AutoCloseable mocks;

  @Mock
  private OBDal obDal;
  @Mock
  private OBCriteria<DefinedwebhookRole> criteria;
  @Mock
  private CopilotApp mockApp;
  @Mock
  private CopilotAppSource mockAppSource;
  @Mock
  private CopilotFile mockFile;
  @Mock
  private org.openbravo.model.ad.system.Client mockClient;
  @Mock
  private OBContext obContext;
  @Mock
  private Connection mockConnection;
  @Mock
  private Session mockSession;
  @Mock
  private OBCriteria<CopilotModel> modelCriteria;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OpenAIUtils> mockedOpenAIUtils;
  private MockedStatic<CopilotUtils> mockedCopilotUtils;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private MockedStatic<WeldUtils> mockedWeldUtils;
  private MockedStatic<CopilotAppInfoUtils> mockedCopilotAppInfoUtils;

  private static final String TEST_APP_ID = "testAppId";
  private static final String RECORD_IDS = "recordIds";
  private static final String RESULT_NOT_NULL = "Result should not be null";


  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    syncAssistant = new SyncAssistant();

    // Set up static mocks
    mockedOBDal = mockStatic(OBDal.class);
    mockedOpenAIUtils = mockStatic(OpenAIUtils.class);
    mockedCopilotUtils = mockStatic(CopilotUtils.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedWeldUtils = mockStatic(WeldUtils.class);
    mockedCopilotAppInfoUtils = mockStatic(CopilotAppInfoUtils.class);

    // Configure OBContext mock
    mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getCurrentClient()).thenReturn(mockClient);
    when(mockClient.getId()).thenReturn("TEST_CLIENT_ID");

    // Configure OBDal mock
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.createCriteria(DefinedwebhookRole.class)).thenReturn(criteria);
    when(obDal.createCriteria(CopilotModel.class)).thenReturn(modelCriteria);
    when(modelCriteria.add(any())).thenReturn(modelCriteria);
    when(modelCriteria.list()).thenReturn(new ArrayList<>());

    // Set up basic mocks
    when(mockApp.getId()).thenReturn(TEST_APP_ID);
    when(mockApp.getAppType()).thenReturn(CopilotConstants.APP_TYPE_OPENAI);
    List<CopilotAppSource> sources = new ArrayList<>();
    sources.add(mockAppSource);
    when(mockApp.getETCOPAppSourceList()).thenReturn(sources);
    when(mockAppSource.getBehaviour()).thenReturn(CopilotConstants.FILE_BEHAVIOUR_KB);
    when(mockAppSource.getClient()).thenReturn(mockClient);

    when(mockAppSource.getFile()).thenReturn(mockFile);
    when(mockFile.getName()).thenReturn("testFile.txt");

    // Set up admin context
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN,
        TestConstants.Clients.FB_GRP, TestConstants.Orgs.ESP_NORTE);

    // Mock OBContext.getLanguage() to return a valid Language object
    Language mockLanguage = mock(Language.class);
    when(mockLanguage.getId()).thenReturn("en_US");
    when(obContext.getLanguage()).thenReturn(mockLanguage);

    // Mock the getConnection() method to return our mockConnection
    when(obDal.getConnection()).thenReturn(mockConnection);
    // Mock the rollback() method to do nothing
    doNothing().when(mockConnection).rollback();

    Query mockQuery = mock(Query.class);
    List<Object[]> queryResults = new ArrayList<>();
    // Add your expected results to the list
    when(mockQuery.list()).thenReturn(queryResults);

    // Configure the session to return this query
    when(obDal.getSession()).thenReturn(mockSession);
    when(mockSession.createQuery(anyString())).thenReturn(mockQuery);
    when(mockQuery.setParameter(eq("appId"), any())).thenReturn(mockQuery);

    // Mock WeldUtils
    CopilotFileHookManager mockHookManager = mock(CopilotFileHookManager.class);
    mockedWeldUtils.when(() ->
        WeldUtils.getInstanceFromStaticBeanManager(CopilotFileHookManager.class)
    ).thenReturn(mockHookManager);

    // Ensure executeHooks does nothing
    doNothing().when(mockHookManager).executeHooks(any(CopilotFile.class));

    // Success msg
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_SuccessSync"))
        .thenReturn("Successful Sync");
  }

  /**
   * Tear down.
   *
   * @throws Exception
   *     the exception
   */
  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mockedOpenAIUtils != null) {
      mockedOpenAIUtils.close();
    }
    if (mockedCopilotUtils != null) {
      mockedCopilotUtils.close();
    }
    if (mockedOBContext != null) {
      mockedOBContext.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
    if (mockedWeldUtils != null) {
      mockedWeldUtils.close();
    }
    if (mockedCopilotAppInfoUtils != null) {
      mockedCopilotAppInfoUtils.close();
    }
  }

  /**
   * Test do execute success.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testDoExecuteSuccess() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    JSONObject content = new JSONObject();
    JSONArray recordIds = new JSONArray();
    recordIds.put(TEST_APP_ID);
    content.put(RECORD_IDS, recordIds);

    // Mock CopilotRoleApp criteria
    OBCriteria<CopilotRoleApp> roleCriteria = mock(OBCriteria.class);
    when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(roleCriteria);
    when(roleCriteria.add(any())).thenReturn(roleCriteria);

    // Create empty role list
    List<CopilotRoleApp> roleApps = new ArrayList<>();
    when(roleCriteria.list()).thenReturn(roleApps);

    when(mockApp.getAppType()).thenReturn(CopilotConstants.APP_TYPE_OPENAI);
    when(obDal.get(CopilotApp.class, TEST_APP_ID)).thenReturn(mockApp);
    when(criteria.uniqueResult()).thenReturn(mockApp);

    // Mock getApiKey
    mockedOpenAIUtils.when(OpenAIUtils::getOpenaiApiKey).thenReturn("test-api-key");

    // When
    try (MockedStatic<CopilotModelUtils> modelUtilsMockedStatic = mockStatic(CopilotModelUtils.class)) {


      JSONObject result = syncAssistant.doExecute(parameters, content.toString());

      // Then
      assertNotNull(RESULT_NOT_NULL, result);
      mockedCopilotAppInfoUtils.verify(() -> CopilotAppInfoUtils.markAsSynchronized(mockApp));
    }
  }

  /**
   * Test do execute no records selected.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testDoExecuteNoRecordsSelected() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    JSONObject content = new JSONObject();
    content.put(RECORD_IDS, new JSONArray());

    OBError error = new OBError();
    String errorMsg = "No records selected";
    error.setMessage(errorMsg);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_NoSelectedRecords"))
        .thenReturn(errorMsg);
    mockedOBMessageUtils.when(() -> OBMessageUtils.translateError(anyString()))
        .thenReturn(error);

    // When
    JSONObject result = syncAssistant.doExecute(parameters, content.toString());

    // Then
    assertNotNull(RESULT_NOT_NULL, result);
    JSONObject message = result.getJSONObject("message");
    assertEquals("Should have error severity", "error", message.getString("severity"));
    assertEquals("Should have no records error message", errorMsg, message.getString("text"));
  }



  /**
   * Test do execute lang chain sync.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testDoExecuteLangChainSync() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    JSONObject content = new JSONObject();
    JSONArray recordIds = new JSONArray();
    recordIds.put(TEST_APP_ID);
    content.put(RECORD_IDS, recordIds);

    // Mock CopilotRoleApp criteria
    OBCriteria<CopilotRoleApp> roleCriteria = mock(OBCriteria.class);
    when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(roleCriteria);
    when(roleCriteria.add(any())).thenReturn(roleCriteria);

    // Create empty role list
    List<CopilotRoleApp> roleApps = new ArrayList<>();
    when(roleCriteria.list()).thenReturn(roleApps);

    when(mockApp.getAppType()).thenReturn(CopilotConstants.APP_TYPE_LANGCHAIN);
    when(obDal.get(CopilotApp.class, TEST_APP_ID)).thenReturn(mockApp);
    when(criteria.uniqueResult()).thenReturn(mockApp);

    // Mock getApiKey
    mockedOpenAIUtils.when(OpenAIUtils::getOpenaiApiKey).thenReturn("test-api-key");
    try (MockedStatic<CopilotModelUtils> modelUtilsMockedStatic = mockStatic(CopilotModelUtils.class)) {

      // When
      JSONObject result = syncAssistant.doExecute(parameters, content.toString());

      // Then
      assertNotNull(RESULT_NOT_NULL, result);
      mockedCopilotUtils.verify(() -> CopilotUtils.resetVectorDB(any(CopilotApp.class)));
      mockedCopilotUtils.verify(() -> CopilotUtils.syncAppLangchainSource(any(CopilotAppSource.class)));
      mockedCopilotUtils.verify(() -> CopilotUtils.purgeVectorDB(any(CopilotApp.class)));
    }
  }

  /**
   * Test do execute no api key.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testDoExecuteNoApiKey() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    JSONObject content = new JSONObject();
    JSONArray recordIds = new JSONArray();
    recordIds.put(TEST_APP_ID);
    content.put(RECORD_IDS, recordIds);

    // Mock CopilotRoleApp criteria
    OBCriteria<CopilotRoleApp> roleCriteria = mock(OBCriteria.class);
    when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(roleCriteria);
    when(roleCriteria.add(any())).thenReturn(roleCriteria);

    // Create empty role list
    List<CopilotRoleApp> roleApps = new ArrayList<>();
    when(roleCriteria.list()).thenReturn(roleApps);

    when(mockApp.getAppType()).thenReturn(CopilotConstants.APP_TYPE_LANGCHAIN);
    when(obDal.get(CopilotApp.class, TEST_APP_ID)).thenReturn(mockApp);
    when(criteria.uniqueResult()).thenReturn(mockApp);

    OBError error = new OBError();
    String errorMsg = "No ApiKey Found";
    error.setMessage(errorMsg);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ApiKeyNotFound"))
        .thenReturn(errorMsg);
    mockedOBMessageUtils.when(() -> OBMessageUtils.translateError(anyString()))
        .thenReturn(error);

    // Mock getApiKey
    mockedOpenAIUtils.when(OpenAIUtils::getOpenaiApiKey).thenReturn(null);

    try (MockedStatic<CopilotModelUtils> modelUtilsMockedStatic = mockStatic(CopilotModelUtils.class)) {

      // When
      JSONObject result = syncAssistant.doExecute(parameters, content.toString());

      // Then
      assertNotNull(RESULT_NOT_NULL, result);
      JSONObject message = result.getJSONObject("message");
      assertEquals("Should have error severity", "error", message.getString("severity"));
      assertEquals("Should have connection error message", errorMsg, message.getString("text"));
    }
  }

  /**
   * Test do execute unsupported app type.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testDoExecuteUnsupportedAppType() throws Exception {
    // Given
    Map<String, Object> parameters = new HashMap<>();
    JSONObject content = new JSONObject();
    JSONArray recordIds = new JSONArray();
    recordIds.put(TEST_APP_ID);
    content.put(RECORD_IDS, recordIds);

    // Mock CopilotRoleApp criteria
    OBCriteria<CopilotRoleApp> roleCriteria = mock(OBCriteria.class);
    when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(roleCriteria);
    when(roleCriteria.add(any())).thenReturn(roleCriteria);

    // Create empty role list
    List<CopilotRoleApp> roleApps = new ArrayList<>();
    when(roleCriteria.list()).thenReturn(roleApps);

    when(mockApp.getAppType()).thenReturn("UNSUPPORTED_TYPE");
    when(obDal.get(CopilotApp.class, TEST_APP_ID)).thenReturn(mockApp);
    when(criteria.uniqueResult()).thenReturn(null);

    // Mock getApiKey
    mockedOpenAIUtils.when(OpenAIUtils::getOpenaiApiKey).thenReturn("test-api-key");

    try (MockedStatic<CopilotModelUtils> modelUtilsMockedStatic = mockStatic(CopilotModelUtils.class)) {
      modelUtilsMockedStatic.when(CopilotModelUtils::syncModels).thenAnswer(invocation -> null);
      // When
      JSONObject result = syncAssistant.doExecute(parameters, content.toString());

      // Then
      assertNotNull(RESULT_NOT_NULL, result);

      // Verify that no synchronization methods were called
      mockedOpenAIUtils.verify(() -> OpenAIUtils.syncAppSource(any(), any()), never());
      mockedCopilotUtils.verify(() -> CopilotUtils.syncAppLangchainSource(any()), never());
    }
  }
}
