import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.test.base.OBBaseTest;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.process.SyncGraphImg;

public class SyncGraphImgTest extends OBBaseTest {

    private AutoCloseable mocks;
    private MockedStatic<OBDal> mockedOBDal;

    @Mock
    private OBDal obDal;

    private SyncGraphImg syncGraphImg;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockedOBDal = mockStatic(OBDal.class);
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);

        syncGraphImg = new SyncGraphImg();
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
    public void testDoExecute_Success() throws Exception {
        // Given
        JSONObject parameters = new JSONObject();
        String content = "{\"recordIds\": [\"id1\", \"id2\"]}";

        // Mock behavior
        CopilotApp mockApp = mock(CopilotApp.class);
        when(obDal.createCriteria(CopilotApp.class).list()).thenReturn(Collections.singletonList(mockApp));
        when(mockApp.getGraphImg()).thenReturn("base64Image");

        // When
        JSONObject result = syncGraphImg.doExecute(parameters, content);

        // Then
        assertEquals("Success", result.getString("messageType"));
        verify(obDal, times(1)).save(mockApp);
    }

    @Test(expected = OBException.class)
    public void testDoExecute_NoSelectedRecords() throws Exception {
        // Given
        JSONObject parameters = new JSONObject();
        String content = "{}";

        // When
        syncGraphImg.doExecute(parameters, content);

        // Then - exception is expected
    }

    @Test
    public void testDoExecute_ConnectionError() throws Exception {
        // Given
        JSONObject parameters = new JSONObject();
        String content = "{\"recordIds\": [\"id1\"]}";

        // Mock behavior
        when(obDal.createCriteria(CopilotApp.class).list()).thenThrow(new ConnectException());

        // When
        JSONObject result = syncGraphImg.doExecute(parameters, content);

        // Then
        assertEquals("Error", result.getString("messageType"));
    }
}
