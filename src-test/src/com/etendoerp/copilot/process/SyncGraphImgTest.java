package com.etendoerp.copilot.process;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
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
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.rest.RestServiceUtil;

/**
 * Sync graph img test.
 */
public class SyncGraphImgTest extends WeldBaseTest {

    public static final String HTML_EXAMPLE = "<!DOCTYPE html>%n<html>%n%n<head>%n    <title>Imagen en base64</title>%n</head>%n%n<body>%n    <img src=\"data:image/jpeg;base64,%s\" %n    %n  style=\"max-width: 100%%; height: auto;\"%n    \n     />\n</body>\n\n</html>";
    /**
     * The Expected exception.
     */
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private SyncGraphImg syncGraphImg;

    @Mock
    private OBCriteria<CopilotApp> mockCriteria;

    @Mock
    private CopilotApp mockCopilotApp;

    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBPropertiesProvider> mockedPropertiesProvider;
    private MockedStatic<RestServiceUtil> mockedRestServiceUtil;
    private MockedStatic<com.etendoerp.copilot.util.CopilotAppInfoUtils> mockedCopilotAppInfoUtils;
    private MockedStatic<OBMessageUtils> mockedMessageUtils;

    private static final String TEST_MSG = "Test Message";
    private static final String RECORD_1 = "record1";
    private static final String RECORD_IDS = "recordIds";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        syncGraphImg = spy(new SyncGraphImg());

        // Setup static mocks
        mockedOBDal = mockStatic(OBDal.class);
        mockedPropertiesProvider = mockStatic(OBPropertiesProvider.class);
        mockedRestServiceUtil = mockStatic(RestServiceUtil.class);
        mockedCopilotAppInfoUtils = mockStatic(com.etendoerp.copilot.util.CopilotAppInfoUtils.class);
        mockedMessageUtils = mockStatic(OBMessageUtils.class);

        // Configure OBDal mock
        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
        when(OBDal.getInstance().createCriteria(CopilotApp.class)).thenReturn(mockCriteria);

        // Configure Properties Provider
        Properties mockProperties = new Properties();
        mockedPropertiesProvider.when(OBPropertiesProvider::getInstance)
                .thenReturn(mock(OBPropertiesProvider.class));
        when(OBPropertiesProvider.getInstance().getOpenbravoProperties())
                .thenReturn(mockProperties);

        // Configure message utils
        mockedMessageUtils.when(() -> OBMessageUtils.messageBD(anyString())).thenReturn(TEST_MSG);
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedPropertiesProvider != null) {
            mockedPropertiesProvider.close();
        }
        if (mockedRestServiceUtil != null) {
            mockedRestServiceUtil.close();
        }
        if (mockedCopilotAppInfoUtils != null) {
            mockedCopilotAppInfoUtils.close();
        }
        if (mockedMessageUtils != null) {
            mockedMessageUtils.close();
        }
    }

    /**
     * Test do execute no selected records.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoExecuteNoSelectedRecords() throws Exception {
        // Given
        Map<String, Object> parameters = new HashMap<>();
        String content = "{}";

        // Expect
        expectedException.expect(OBException.class);
        expectedException.expectMessage(TEST_MSG);

        // When
        doThrow(new OBException(TEST_MSG))
                .when(syncGraphImg)
                .doExecute(anyMap(),anyString());

        // When
        syncGraphImg.doExecute(parameters, content);
    }

    /**
     * Test do execute successful sync.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoExecuteSuccessfulSync() throws Exception {
        // Given
        JSONObject request = new JSONObject();
        JSONArray selectedRecords = new JSONArray();
        selectedRecords.put(RECORD_1);
        request.put(RECORD_IDS, selectedRecords);

        List<CopilotApp> mockAppList = new ArrayList<>();
        mockCopilotApp = mock(CopilotApp.class);
        mockAppList.add(mockCopilotApp);

        // Configure mocks
        when(mockCriteria.list()).thenReturn(mockAppList);
        mockedRestServiceUtil.when(() -> RestServiceUtil.getGraphImg(any(CopilotApp.class)))
            .thenReturn("base64EncodedImage");

        // When
        JSONObject result = syncGraphImg.doExecute(new HashMap<>(), request.toString());

        // Then
        assertNotNull(result);
        // Verify AppInfo util was called to save the image
        String expectedData = String.format(
            HTML_EXAMPLE,
            "base64EncodedImage");
        mockedCopilotAppInfoUtils.verify(() -> com.etendoerp.copilot.util.CopilotAppInfoUtils.setGraphImg(mockCopilotApp, expectedData));
    }

    /**
     * Test do execute no image generated.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoExecuteNoImageGenerated() throws Exception {
        // Given
        JSONObject request = new JSONObject();
        JSONArray selectedRecords = new JSONArray();
        selectedRecords.put(RECORD_1);
        request.put(RECORD_IDS, selectedRecords);

        List<CopilotApp> mockAppList = new ArrayList<>();
        mockCopilotApp = mock(CopilotApp.class);
        mockAppList.add(mockCopilotApp);

        // Configure mocks
        when(mockCriteria.list()).thenReturn(mockAppList);
        mockedRestServiceUtil.when(() -> RestServiceUtil.getGraphImg(any(CopilotApp.class)))
            .thenReturn(StringUtils.EMPTY);

        // When
        JSONObject result = syncGraphImg.doExecute(new HashMap<>(), request.toString());

        // Then
        assertNotNull(result);
        verify(mockCopilotApp, never()).setGraphImg(anyString());
        verify(OBDal.getInstance(), never()).save(mockCopilotApp);
    }

    /**
     * Test do execute multiple records sync.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoExecuteMultipleRecordsSync() throws Exception {
        // Given
        JSONObject request = new JSONObject();
        JSONArray selectedRecords = new JSONArray();
        selectedRecords.put(RECORD_1);
        selectedRecords.put("record2");
        request.put(RECORD_IDS, selectedRecords);

        List<CopilotApp> mockAppList = new ArrayList<>();
        CopilotApp mockApp1 = mock(CopilotApp.class);
        CopilotApp mockApp2 = mock(CopilotApp.class);
        mockAppList.add(mockApp1);
        mockAppList.add(mockApp2);

        // Configure mocks
        when(mockCriteria.list()).thenReturn(mockAppList);
        mockedRestServiceUtil.when(() -> RestServiceUtil.getGraphImg(mockApp1))
            .thenReturn("base64Image1");
        mockedRestServiceUtil.when(() -> RestServiceUtil.getGraphImg(mockApp2))
            .thenReturn("base64Image2");

        // When
        JSONObject result = syncGraphImg.doExecute(new HashMap<>(), request.toString());

        // Then
        assertNotNull(result);
        // Verify AppInfo util was called for both apps
        String expectedData1 = String.format(
            HTML_EXAMPLE,
            "base64Image1");
        String expectedData2 = String.format(
            HTML_EXAMPLE,
            "base64Image2");
        mockedCopilotAppInfoUtils.verify(() -> com.etendoerp.copilot.util.CopilotAppInfoUtils.setGraphImg(mockApp1, expectedData1));
        mockedCopilotAppInfoUtils.verify(() -> com.etendoerp.copilot.util.CopilotAppInfoUtils.setGraphImg(mockApp2, expectedData2));
        // No direct OBDal.save interactions expected because CopilotAppInfoUtils handles persistence
    }
}
