import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import com.etendoerp.copilot.util.ToolsUtil;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ToolsUtilTest {

    @Mock
    private CopilotApp mockApp;

    @Mock
    private CopilotAppTool mockAppTool;

    @Mock
    private CopilotTool mockTool;

    @Mock
    private OBCriteria<CopilotAppTool> mockCriteria;

    private MockedStatic<OBDal> mockedOBDal;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedOBDal = mockStatic(OBDal.class);
    }

    @Test
    public void testGetToolSet_NoTools() throws Exception {
        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
        when(OBDal.getInstance().createCriteria(CopilotAppTool.class)).thenReturn(mockCriteria);
        when(mockCriteria.add(Restrictions.eq(CopilotAppTool.PROPERTY_COPILOTAPP, mockApp))).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        JSONArray result = ToolsUtil.getToolSet(mockApp);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetToolSet_WithTools() throws Exception {
        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
        when(OBDal.getInstance().createCriteria(CopilotAppTool.class)).thenReturn(mockCriteria);
        when(mockCriteria.add(Restrictions.eq(CopilotAppTool.PROPERTY_COPILOTAPP, mockApp))).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(List.of(mockAppTool));
        when(mockAppTool.getCopilotTool()).thenReturn(mockTool);
        when(mockTool.getJsonStructure()).thenReturn("{\"name\":\"TestTool\"}");

        JSONArray result = ToolsUtil.getToolSet(mockApp);

        assertEquals(1, result.length());
        JSONObject toolJson = result.getJSONObject(0);
        assertEquals("TestTool", toolJson.getString("name"));
    }

    @Test(expected = JSONException.class)
    public void testGetToolSet_JSONException() throws Exception {
        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
        when(OBDal.getInstance().createCriteria(CopilotAppTool.class)).thenReturn(mockCriteria);
        when(mockCriteria.add(Restrictions.eq(CopilotAppTool.PROPERTY_COPILOTAPP, mockApp))).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(List.of(mockAppTool));
        when(mockAppTool.getCopilotTool()).thenReturn(mockTool);
        when(mockTool.getJsonStructure()).thenReturn("{invalidJson}");

        ToolsUtil.getToolSet(mockApp);
    }
}
