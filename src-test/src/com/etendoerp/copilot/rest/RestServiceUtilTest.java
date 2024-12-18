import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.rest.RestServiceUtil;

@RunWith(MockitoJUnitRunner.class)
public class RestServiceUtilTest {

    private AutoCloseable mocks;

    @Mock
    private CopilotApp mockCopilotApp;

    private MockedStatic<OBDal> mockedOBDal;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockedOBDal = mockStatic(OBDal.class);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void testGetJSONLabels() {
        // Given
        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));

        // When
        JSONObject jsonLabels = RestServiceUtil.getJSONLabels();

        // Then
        assertNotNull("JSON Labels should not be null", jsonLabels);
    }

    @Test
    public void testHandleFile() throws Exception {
        // Given
        List<FileItem> items = List.of(mock(FileItem.class));

        // When
        JSONObject response = RestServiceUtil.handleFile(items);

        // Then
        assertNotNull("Response should not be null", response);
    }

    @Test
    public void testSendMsg() throws Exception {
        // Given
        TransferQueue<String> queue = mock(TransferQueue.class);
        String role = "testRole";
        String msg = "testMessage";

        // When
        RestServiceUtil.sendMsg(queue, role, msg);

        // Then
        verify(queue, times(1)).transfer(anyString());
    }

    @Test
    public void testHandleQuestion() throws Exception {
        // Given
        HttpServletResponse response = mock(HttpServletResponse.class);
        JSONObject jsonRequest = new JSONObject();
        jsonRequest.put("app_id", "testAppId");
        jsonRequest.put("question", "testQuestion");

        // When
        JSONObject result = RestServiceUtil.handleQuestion(false, response, jsonRequest);

        // Then
        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testGetGraphImg() throws Exception {
        // Given
        when(mockCopilotApp.getAppType()).thenReturn("testType");

        // When
        String result = RestServiceUtil.getGraphImg(mockCopilotApp);

        // Then
        assertNotNull("Result should not be null", result);
    }
}
