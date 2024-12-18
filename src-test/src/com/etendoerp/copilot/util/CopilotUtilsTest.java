package com.etendoerp.copilot.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.data.CopilotModel;

@RunWith(MockitoJUnitRunner.class)
public class CopilotUtilsTest {

    @Mock
    private CopilotApp mockApp;

    @Mock
    private CopilotFile mockFile;

    @Mock
    private CopilotAppSource mockAppSource;

    @Mock
    private OBContext mockContext;

    @Mock
    private Properties mockProperties;

    private MockedStatic<OBContext> mockedOBContext;
    private MockedStatic<OBPropertiesProvider> mockedOBPropertiesProvider;
    private MockedStatic<Preferences> mockedPreferences;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedOBContext = mockStatic(OBContext.class);
        mockedOBPropertiesProvider = mockStatic(OBPropertiesProvider.class);
        mockedPreferences = mockStatic(Preferences.class);

        when(OBContext.getOBContext()).thenReturn(mockContext);
        when(OBPropertiesProvider.getInstance()).thenReturn(mock(OBPropertiesProvider.class));
    }

    @After
    public void tearDown() {
        mockedOBContext.close();
        mockedOBPropertiesProvider.close();
        mockedPreferences.close();
    }

    @Test
    public void testGetProvider_withValidApp() {
        when(mockApp.getModel()).thenReturn(mock(CopilotModel.class));
        when(mockApp.getModel().getProvider()).thenReturn("OpenAI");

        String provider = CopilotUtils.getProvider(mockApp);

        assertEquals("OpenAI", provider);
    }

    @Test(expected = OBException.class)
    public void testGetProvider_withNullApp() {
        CopilotUtils.getProvider(null);
    }

    @Test
    public void testGetAppModel_withValidApp() {
        when(mockApp.getModel()).thenReturn(mock(CopilotModel.class));
        when(mockApp.getModel().getSearchkey()).thenReturn("ModelKey");

        String model = CopilotUtils.getAppModel(mockApp);

        assertEquals("ModelKey", model);
    }

    @Test(expected = OBException.class)
    public void testToVectorDB_withInvalidResponse() throws JSONException {
        File mockFile = mock(File.class);
        when(mockFile.getName()).thenReturn("test.txt");

        CopilotUtils.toVectorDB("content", mockFile, "dbName", "txt", false, false);
    }

    @Test
    public void testReplaceCopilotPromptVariables() {
        String input = "This is a test with @ETENDO_HOST@ and @source.path@.";
        when(mockProperties.getProperty("ETENDO_HOST", "ETENDO_HOST_NOT_CONFIGURED")).thenReturn("localhost");
        when(mockProperties.getProperty("source.path")).thenReturn("/source/path");

        String result = CopilotUtils.replaceCopilotPromptVariables(input);

        assertTrue(result.contains("localhost"));
        assertTrue(result.contains("/source/path"));
    }

    @Test
    public void testGenerateEtendoToken() throws Exception {
        // Assuming SecureWebServicesUtils.generateToken is tested separately
        String token = CopilotUtils.generateEtendoToken();
        assertNotNull(token);
    }
}
