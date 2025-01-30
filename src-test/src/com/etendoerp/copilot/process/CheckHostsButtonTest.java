package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

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
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;

import com.etendoerp.copilot.util.CopilotUtils;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.openbravo.model.ad.system.Language;

/**
 * Check hosts button test.
 */
public class CheckHostsButtonTest extends WeldBaseTest {
    
    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MockedStatic<SecureWebServicesUtils> mockedSecureWebServicesUtils;
    private MockedStatic<CopilotUtils> mockedCopilotUtils;
    private MockedStatic<OBContext> mockedOBContext;
    private MockedStatic<OBDal> mockedOBDal;

    @Mock
    private OBContext mockContext;
    
    @Mock
    private OBDal mockOBDal;
    
    @Mock
    private User mockUser;
    
    @Mock
    private Role mockRole;

    private CheckHostsButton checkHostsButton;
    private AutoCloseable mocks;
    
    private static final String ETENDO_HOST = "ETENDO_HOST";
    private static final String RESPONSE_ACTIONS= "responseActions";
    private static final String SHOW_MSG_IN_PROC_VIEW = "showMsgInProcessView";
    private static final String MSG_TYPE = "msgType";

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        
        // Initialize the test class with a custom createConnection method
        checkHostsButton = new TestCheckHostsButton();

        // Setup static mocks
        mockedSecureWebServicesUtils = mockStatic(SecureWebServicesUtils.class);
        mockedCopilotUtils = mockStatic(CopilotUtils.class);
        mockedOBContext = mockStatic(OBContext.class);
        mockedOBDal = mockStatic(OBDal.class);

        // Configure default mock behavior
        mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
        mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);
        
        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockContext.getRole()).thenReturn(mockRole);
        when(mockUser.getId()).thenReturn("testUserId");
        when(mockRole.getId()).thenReturn("testRoleId");
        
        mockedSecureWebServicesUtils.when(() -> SecureWebServicesUtils.generateToken(any(), any()))
            .thenReturn("test-token");
        
        // Configure CopilotUtils default responses
        mockedCopilotUtils.when(CopilotUtils::getEtendoHost).thenReturn("http://localhost:8080");
        mockedCopilotUtils.when(CopilotUtils::getCopilotHost).thenReturn("localhost");
        mockedCopilotUtils.when(CopilotUtils::getCopilotPort).thenReturn("5000");

        // Mock OBContext.getLanguage() to return a valid Language object
        Language mockLanguage = mock(Language.class);
        when(mockLanguage.getId()).thenReturn("en_US");
        when(mockContext.getLanguage()).thenReturn(mockLanguage);
    }

    /**
     * Tear down.
     *
     * @throws Exception the exception
     */
    @After
    public void tearDown() throws Exception {
        if (mockedSecureWebServicesUtils != null) {
            mockedSecureWebServicesUtils.close();
        }
        if (mockedCopilotUtils != null) {
            mockedCopilotUtils.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test do execute all hosts successful.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoExecute_AllHostsSuccessful() throws Exception {
        // Given
        Map<String, Object> params = new HashMap<>();
        String content = "";
        
        // Configure mock responses for successful scenario
        ((TestCheckHostsButton) checkHostsButton).setMockResponseCode(HttpServletResponse.SC_OK);
        ((TestCheckHostsButton) checkHostsButton).setMockResponseBody("{\"success\":true}");

        // When
        JSONObject result = checkHostsButton.doExecute(params, content);

        // Then
        assertTrue(result.getBoolean(ETENDO_HOST));
        assertTrue(result.getBoolean("COPILOT_HOST"));
        assertTrue(result.getBoolean("ETENDO_HOST_DOCKER"));
        
        JSONArray actions = result.getJSONArray(RESPONSE_ACTIONS);
        JSONObject action = actions.getJSONObject(0);
        JSONObject showMsgInProcessView = action.getJSONObject(SHOW_MSG_IN_PROC_VIEW);
        assertEquals("success", showMsgInProcessView.getString(MSG_TYPE));
    }

    /**
     * Test do execute etendo host failure.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoExecute_EtendoHostFailure() throws Exception {
        // Given
        Map<String, Object> params = new HashMap<>();
        String content = "";
        
        // Configure mock responses for Etendo host failure
        ((TestCheckHostsButton) checkHostsButton).setMockResponseCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        // When
        JSONObject result = checkHostsButton.doExecute(params, content);

        // Then
        assertFalse(result.getBoolean(ETENDO_HOST));
        
        JSONArray actions = result.getJSONArray(RESPONSE_ACTIONS);
        JSONObject action = actions.getJSONObject(0);
        JSONObject showMsgInProcessView = action.getJSONObject(SHOW_MSG_IN_PROC_VIEW);
        assertEquals("error", showMsgInProcessView.getString(MSG_TYPE));
    }

    /**
     * Test do execute copilot host failure.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoExecute_CopilotHostFailure() throws Exception {
        // Given
        Map<String, Object> params = new HashMap<>();
        String content = "";
        
        // Configure mock responses for Copilot host failure
        ((TestCheckHostsButton) checkHostsButton).setMockResponseCode(HttpServletResponse.SC_OK);
        ((TestCheckHostsButton) checkHostsButton).setSecondResponseCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        // When
        JSONObject result = checkHostsButton.doExecute(params, content);

        // Then
        assertTrue(result.getBoolean(ETENDO_HOST));
        assertFalse(result.getBoolean("COPILOT_HOST"));
        
        JSONArray actions = result.getJSONArray(RESPONSE_ACTIONS);
        JSONObject action = actions.getJSONObject(0);
        JSONObject showMsgInProcessView = action.getJSONObject(SHOW_MSG_IN_PROC_VIEW);
        assertEquals("error", showMsgInProcessView.getString(MSG_TYPE));
    }

    /**
     * Test do execute null token.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoExecute_NullToken() throws Exception {
        // Given
        Map<String, Object> params = new HashMap<>();
        String content = "";
        
        // Configure SecureWebServicesUtils to return null token
        mockedSecureWebServicesUtils.when(() -> SecureWebServicesUtils.generateToken(any(), any()))
            .thenReturn(null);

        // Then
        expectedException.expect(OBException.class);
        expectedException.expectMessage("Error when generating token.");

        // When
        checkHostsButton.doExecute(params, content);
    }

    /**
     * Test implementation of CheckHostsButton that mocks the HTTP connection
     */
    private static class TestCheckHostsButton extends CheckHostsButton {
        private int mockResponseCode = HttpServletResponse.SC_OK;
        private int secondResponseCode = HttpServletResponse.SC_OK;
        private String mockResponseBody = "{\"success\":true}";
        private boolean isFirstCall = true;

        public void setMockResponseCode(int code) {
            this.mockResponseCode = code;
        }

        /**
         * Sets second response code.
         *
         * @param code the code
         */
        public void setSecondResponseCode(int code) {
            this.secondResponseCode = code;
        }

        /**
         * Sets mock response body.
         *
         * @param body the body
         */
        public void setMockResponseBody(String body) {
            this.mockResponseBody = body;
        }

        @Override
        protected HttpURLConnection createConnection(String urlString, String token) throws IOException {
            HttpURLConnection mockConnection = mock(HttpURLConnection.class);
            
            int responseCode = isFirstCall ? mockResponseCode : secondResponseCode;
            when(mockConnection.getResponseCode()).thenReturn(responseCode);
            
            // Create an InputStream with the mock response
            InputStream inputStream = new ByteArrayInputStream(mockResponseBody.getBytes());
            when(mockConnection.getInputStream()).thenReturn(inputStream);
            
            isFirstCall = false;
            return mockConnection;
        }
    }
}
