package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotTool;

public class SyncToolStructureTest extends WeldBaseTest {
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private SyncToolStructure syncToolStructure;
    private AutoCloseable mocks;

    @Mock
    private OBDal mockOBDal;
    @Mock
    private OBPropertiesProvider mockPropertiesProvider;
    @Mock
    private HttpClient mockHttpClient;
    @Mock
    private HttpResponse<String> mockHttpResponse;
    @Mock
    private OBCriteria<CopilotTool> mockCriteria;
    @Mock
    private CopilotTool mockCopilotTool;
    @Mock
    private Connection mockConnection;

    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBPropertiesProvider> mockedPropertiesProvider;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
    private MockedStatic<HttpClient> mockedHttpClient;
    private HttpClient.Builder mockBuilder;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        syncToolStructure = new SyncToolStructure();

        // Initialize static mocks
        mockedOBDal = mockStatic(OBDal.class);
        mockedPropertiesProvider = mockStatic(OBPropertiesProvider.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        // Configure static mocks
        mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);
        mockedPropertiesProvider.when(OBPropertiesProvider::getInstance).thenReturn(mockPropertiesProvider);
        mockedHttpClient = mockStatic(HttpClient.class);

        // Configure mock builder
        mockBuilder = mock(HttpClient.Builder.class);
        mockedHttpClient.when(HttpClient::newBuilder).thenReturn(mockBuilder);

        // Mock the getConnection() method to return our mockConnection
        when(mockOBDal.getConnection()).thenReturn(mockConnection);
        // Mock the rollback() method to do nothing
        doNothing().when(mockConnection).rollback();

        // Configure properties
        Properties properties = new Properties();
        properties.setProperty("COPILOT_PORT", "5005");
        properties.setProperty("COPILOT_HOST", "host");
        when(mockPropertiesProvider.getOpenbravoProperties()).thenReturn(properties);

        // Configure criteria
        when(mockOBDal.createCriteria(CopilotTool.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(mockCopilotTool));
    }

    @After
    public void tearDown() throws Exception {
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedPropertiesProvider != null) {
            mockedPropertiesProvider.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
        if (mockedHttpClient != null) {
            mockedHttpClient.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void testDoExecute_NoSelectedRecords() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        String content = new JSONObject().put("recordIds", new JSONArray()).toString();
        
        // Configure message
        OBError error = new OBError();
        error.setMessage("No records selected");
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_NoSelectedRecords"))
                           .thenReturn("No records selected");
        mockedOBMessageUtils.when(() -> OBMessageUtils.translateError(anyString()))
                .thenReturn(error);

        // When
        JSONObject result = syncToolStructure.doExecute(parameters, content);

        // Then
        assertNotNull("Result should not be null", result);
        JSONObject message = result.getJSONObject("message");
        assertEquals("Should have error severity", "error", message.getString("severity"));
        assertEquals("Should have connection error message", "No records selected", message.getString("text"));
    }

    @Test
    public void testDoExecute_SuccessfulSync() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        JSONArray recordIds = new JSONArray();
        recordIds.put("TEST_ID");
        String content = new JSONObject().put("recordIds", recordIds).toString();

        // Create mock HTTP client and response
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        // Mock successful response body
        String mockResponseBody = new JSONObject()
                .put("answer", new JSONObject()
                        .put("TEST_TOOL", new JSONObject()
                                .put("description", "Test Description")
                                .put("parameters", new JSONObject()
                                        .put("type", "object")
                                        .put("properties", new JSONObject())))).toString();
        when(mockResponse.body()).thenReturn(mockResponseBody);

        // Configure the mock chain
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Configure success messages
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("Success"))
                .thenReturn("Success");
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_SuccessSync"))
                .thenReturn("Successfully synced %s of %s tools");

        // When
        JSONObject result = syncToolStructure.doExecute(parameters, content);

        // Then
        assertNotNull("Result should not be null", result);
        JSONArray responseActions = result.getJSONArray("responseActions");
        assertNotNull("Response actions should not be null", responseActions);
        assertEquals("Should have one response action", 1, responseActions.length());
    }

    @Test
    public void testDoExecute_ConnectionError() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        JSONArray recordIds = new JSONArray();
        recordIds.put("TEST_ID");
        String content = new JSONObject().put("recordIds", recordIds).toString();

        // Create mock HTTP client for error case
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new ConnectException("Connection refused"));

        // Configure the builder to return our mock client
        when(mockBuilder.build()).thenReturn(mockClient);

        // Configure error messages
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ConnCopilotError"))
                .thenReturn("Connection error");
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("Error"))
                .thenReturn("Error");

        // When
        JSONObject result = syncToolStructure.doExecute(parameters, content);

        // Then
        assertNotNull("Result should not be null", result);
        JSONObject message = result.getJSONObject("message");
        assertEquals("Should have error severity", "error", message.getString("severity"));
        assertEquals("Should have error title", "Error", message.getString("title"));
        assertEquals("Should have connection error message", "Connection error", message.getString("text"));

        // Verify rollback was called
        verify(mockConnection, times(1)).rollback();
    }
}