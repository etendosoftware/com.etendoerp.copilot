package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Language;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.hook.CopilotFileHookManager;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.copilot.util.OpenAIUtils;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;

/**
 * Test class for {@link SyncAssistant}
 */
public class SyncAssistantTest extends WeldBaseTest {
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
    private OBContext obContext;
    @Mock
    private Connection mockConnection;
    @Mock
    private Session mockSession;

    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OpenAIUtils> mockedOpenAIUtils;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private MockedStatic<OBContext> mockedOBContext;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
    private MockedStatic<WeldUtils> mockedWeldUtils;

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

        // Configure OBDal mock
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
        when(obDal.createCriteria(DefinedwebhookRole.class)).thenReturn(criteria);

        // Set up basic mocks
        when(mockApp.getId()).thenReturn("testAppId");
        when(mockApp.getAppType()).thenReturn(CopilotConstants.APP_TYPE_OPENAI);
        List<CopilotAppSource> sources = new ArrayList<>();
        sources.add(mockAppSource);
        when(mockApp.getETCOPAppSourceList()).thenReturn(sources);

        when(mockAppSource.getFile()).thenReturn(mockFile);
        when(mockFile.getName()).thenReturn("testFile.txt");

        // Set up OpenAI utils mock
        mockedOpenAIUtils.when(OpenAIUtils::getOpenaiApiKey).thenReturn("test-api-key");

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
    }

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
        OBContext.restorePreviousMode();
    }

    @Test
    public void testDoExecute_Success() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        JSONObject content = new JSONObject();
        JSONArray recordIds = new JSONArray();
        recordIds.put("testAppId");
        content.put("recordIds", recordIds);

        when(obDal.get(CopilotApp.class, "testAppId")).thenReturn(mockApp);
        when(criteria.uniqueResult()).thenReturn(null);

        // When
        JSONObject result = syncAssistant.doExecute(parameters, content.toString());

        // Then
        assertNotNull("Result should not be null", result);
        verify(mockApp).setSyncStatus(CopilotConstants.SYNCHRONIZED_STATE);
        mockedOpenAIUtils.verify(() -> OpenAIUtils.syncOpenaiModels(anyString()));
    }

    @Test
    public void testDoExecute_NoRecordsSelected() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        JSONObject content = new JSONObject();
        content.put("recordIds", new JSONArray());

        OBError error = new OBError();
        error.setMessage("No records selected");
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_NoSelectedRecords"))
                .thenReturn("No records selected");
        mockedOBMessageUtils.when(() -> OBMessageUtils.translateError(anyString()))
                .thenReturn(error);

        // When
        JSONObject result = syncAssistant.doExecute(parameters, content.toString());

        // Then
        assertNotNull("Result should not be null", result);
        JSONObject message = result.getJSONObject("message");
        assertEquals("Should have error severity", "error", message.getString("severity"));
        assertEquals("Should have connection error message", "No records selected", message.getString("text"));
    }

    @Test
    public void testDoExecute_OpenAISync() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        JSONObject content = new JSONObject();
        JSONArray recordIds = new JSONArray();
        recordIds.put("testAppId");
        content.put("recordIds", recordIds);

        when(obDal.get(CopilotApp.class, "testAppId")).thenReturn(mockApp);
        when(criteria.uniqueResult()).thenReturn(null);

        // When
        JSONObject result = syncAssistant.doExecute(parameters, content.toString());

        // Then
        assertNotNull("Result should not be null", result);
        mockedOpenAIUtils.verify(() -> OpenAIUtils.syncOpenaiModels(anyString()));
        mockedOpenAIUtils.verify(() -> OpenAIUtils.syncAppSource(any(CopilotAppSource.class), anyString()));
        mockedOpenAIUtils.verify(() -> OpenAIUtils.refreshVectorDb(any(CopilotApp.class)));
    }

    @Test
    public void testDoExecute_LangChainSync() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        JSONObject content = new JSONObject();
        JSONArray recordIds = new JSONArray();
        recordIds.put("testAppId");
        content.put("recordIds", recordIds);

        when(mockApp.getAppType()).thenReturn(CopilotConstants.APP_TYPE_LANGCHAIN);
        when(obDal.get(CopilotApp.class, "testAppId")).thenReturn(mockApp);
        when(criteria.uniqueResult()).thenReturn(null);

        // When
        JSONObject result = syncAssistant.doExecute(parameters, content.toString());

        // Then
        assertNotNull("Result should not be null", result);
        mockedCopilotUtils.verify(() -> CopilotUtils.resetVectorDB(any(CopilotApp.class)));
        mockedCopilotUtils.verify(() -> CopilotUtils.syncAppLangchainSource(any(CopilotAppSource.class)));
        mockedCopilotUtils.verify(() -> CopilotUtils.purgeVectorDB(any(CopilotApp.class)));
    }

    @Test
    public void testDoExecute_NoApiKey() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        JSONObject content = new JSONObject();
        JSONArray recordIds = new JSONArray();
        recordIds.put("testAppId");
        content.put("recordIds", recordIds);

        mockedOpenAIUtils.when(OpenAIUtils::getOpenaiApiKey).thenReturn(null);
        when(obDal.get(CopilotApp.class, "testAppId")).thenReturn(mockApp);

        OBError error = new OBError();
        error.setMessage("No ApiKey Found");
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ApiKeyNotFound"))
                .thenReturn("No ApiKey Found");
        mockedOBMessageUtils.when(() -> OBMessageUtils.translateError(anyString()))
                .thenReturn(error);

        // When
        JSONObject result = syncAssistant.doExecute(parameters, content.toString());

        // Then
        assertNotNull("Result should not be null", result);
        JSONObject message = result.getJSONObject("message");
        assertEquals("Should have error severity", "error", message.getString("severity"));
        assertEquals("Should have connection error message", "No ApiKey Found", message.getString("text"));
    }

    @Test
    public void testDoExecute_UnsupportedAppType() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        JSONObject content = new JSONObject();
        JSONArray recordIds = new JSONArray();
        recordIds.put("testAppId");
        content.put("recordIds", recordIds);

        when(mockApp.getAppType()).thenReturn("UNSUPPORTED_TYPE");
        when(obDal.get(CopilotApp.class, "testAppId")).thenReturn(mockApp);
        when(criteria.uniqueResult()).thenReturn(null);

        // When
        JSONObject result = syncAssistant.doExecute(parameters, content.toString());

        // Then
        assertNotNull("Result should not be null", result);
        // Verify that no synchronization methods were called
        mockedOpenAIUtils.verify(() -> OpenAIUtils.syncAppSource(any(), any()), never());
        mockedCopilotUtils.verify(() -> CopilotUtils.syncAppLangchainSource(any()), never());
    }
}