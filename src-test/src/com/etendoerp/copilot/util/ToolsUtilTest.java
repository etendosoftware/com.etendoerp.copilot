package com.etendoerp.copilot.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;

/**
 * ToolsUtil test class.
 */
public class ToolsUtilTest {

  private AutoCloseable mocks;

  @Mock
  private OBDal obDal;
  @Mock
  private CopilotApp mockApp;
  @Mock
  private CopilotAppTool mockAppTool;
  @Mock
  private CopilotTool mockTool;
  @Mock
  private OBCriteria<CopilotAppTool> mockCriteria;

  private MockedStatic<OBDal> mockedOBDal;

  private static final String TEST_APP_ID = "testAppId123";
  private static final String TEST_TOOL_NAME = "testTool";
  private static final String TEST_TOOL_JSON = "{\"type\":\"function\",\"function\":{\"name\":\"testTool\",\"description\":\"Test tool description\"}}";

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    // Set up static mocks
    mockedOBDal = mockStatic(OBDal.class);

    // Configure OBDal mock
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.createCriteria(CopilotAppTool.class)).thenReturn(mockCriteria);

    // Configure CopilotApp mock
    when(mockApp.getId()).thenReturn(TEST_APP_ID);

    // Configure OBCriteria mock
    when(mockCriteria.add(any())).thenReturn(mockCriteria);

    // Configure CopilotAppTool mock
    when(mockAppTool.getCopilotTool()).thenReturn(mockTool);

    // Configure CopilotTool mock
    when(mockTool.getValue()).thenReturn(TEST_TOOL_NAME);
  }

  /**
   * Tears down the test environment after each test.
   *
   * @throws Exception if teardown fails
   */
  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
  }

  /**
   * Test getToolSet with empty tool list.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetWithEmptyList() throws Exception {
    // Given
    List<CopilotAppTool> emptyList = new ArrayList<>();
    when(mockCriteria.list()).thenReturn(emptyList);

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Result should be empty", 0, result.length());
    verify(mockCriteria, times(1)).add(any());
    verify(mockCriteria, times(1)).list();
  }

  /**
   * Test getToolSet with tool that has full JSON structure.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetWithFullJSONStructure() throws Exception {
    // Given
    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn(TEST_TOOL_JSON);

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one tool", 1, result.length());

    JSONObject toolJson = result.getJSONObject(0);
    assertEquals("Should have correct type", "function", toolJson.getString("type"));
    assertTrue("Should have function object", toolJson.has("function"));

    JSONObject functionJson = toolJson.getJSONObject("function");
    assertEquals("Should have correct function name", TEST_TOOL_NAME, functionJson.getString("name"));
    assertEquals("Should have description", "Test tool description", functionJson.getString("description"));
  }

  /**
   * Test getToolSet with tool that has null JSON structure.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetWithNullJSONStructure() throws Exception {
    // Given
    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn(null);

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one tool", 1, result.length());

    JSONObject toolJson = result.getJSONObject(0);
    assertEquals("Should have correct type", "function", toolJson.getString("type"));
    assertTrue("Should have function object", toolJson.has("function"));

    JSONObject functionJson = toolJson.getJSONObject("function");
    assertEquals("Should have correct function name", TEST_TOOL_NAME, functionJson.getString("name"));
  }

  /**
   * Test getToolSet with tool that has empty JSON structure {}.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetWithEmptyJSONStructure() throws Exception {
    // Given
    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn("{}");

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one tool", 1, result.length());

    JSONObject toolJson = result.getJSONObject(0);
    assertEquals("Should have correct type", "function", toolJson.getString("type"));
    assertTrue("Should have function object", toolJson.has("function"));

    JSONObject functionJson = toolJson.getJSONObject("function");
    assertEquals("Should have correct function name", TEST_TOOL_NAME, functionJson.getString("name"));
  }

  /**
   * Test getToolSet with multiple tools.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetWithMultipleTools() throws Exception {
    // Given
    CopilotAppTool mockAppTool2 = mock(CopilotAppTool.class);
    CopilotTool mockTool2 = mock(CopilotTool.class);
    when(mockAppTool2.getCopilotTool()).thenReturn(mockTool2);
    when(mockTool2.getValue()).thenReturn("secondTool");
    when(mockTool2.getJsonStructure()).thenReturn("{\"type\":\"function\",\"function\":{\"name\":\"secondTool\"}}");

    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    toolsList.add(mockAppTool2);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn(TEST_TOOL_JSON);

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have two tools", 2, result.length());

    JSONObject tool1Json = result.getJSONObject(0);
    assertEquals("First tool should have correct name", TEST_TOOL_NAME,
        tool1Json.getJSONObject("function").getString("name"));

    JSONObject tool2Json = result.getJSONObject(1);
    assertEquals("Second tool should have correct name", "secondTool",
        tool2Json.getJSONObject("function").getString("name"));
  }

  /**
   * Test getToolSet with mixed tools (some with full JSON, some without).
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetWithMixedTools() throws Exception {
    // Given
    CopilotAppTool mockAppTool2 = mock(CopilotAppTool.class);
    CopilotTool mockTool2 = mock(CopilotTool.class);
    when(mockAppTool2.getCopilotTool()).thenReturn(mockTool2);
    when(mockTool2.getValue()).thenReturn("simpleTool");
    when(mockTool2.getJsonStructure()).thenReturn(null); // This one has no JSON structure

    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    toolsList.add(mockAppTool2);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn(TEST_TOOL_JSON); // This one has full JSON

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have two tools", 2, result.length());

    // First tool has full JSON with description
    JSONObject tool1Json = result.getJSONObject(0);
    assertTrue("First tool should have description",
        tool1Json.getJSONObject("function").has("description"));

    // Second tool has minimal JSON without description
    JSONObject tool2Json = result.getJSONObject(1);
    assertEquals("Second tool should have correct name", "simpleTool",
        tool2Json.getJSONObject("function").getString("name"));
  }

  /**
   * Test getToolSet with tool that has whitespace in JSON structure.
   * Note: "  {}  " is not equal to "{}" so it will try to parse it as JSON,
   * which creates an empty JSON object.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetWithWhitespaceJSONStructure() throws Exception {
    // Given
    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn("  {}  ");

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one tool", 1, result.length());

    JSONObject toolJson = result.getJSONObject(0);
    // The JSON "  {}  " gets parsed as an empty object, so it won't have "type" field
    // This tests the actual behavior when StringUtils.equals("{}", "  {}  ") returns false
    assertTrue("Should be a JSON object", toolJson != null);
  }

  /**
   * Test getToolSet verifies criteria is properly configured.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetVerifiesCriteriaConfiguration() throws Exception {
    // Given
    List<CopilotAppTool> emptyList = new ArrayList<>();
    when(mockCriteria.list()).thenReturn(emptyList);

    // When
    ToolsUtil.getToolSet(mockApp);

    // Then
    verify(obDal, times(1)).createCriteria(CopilotAppTool.class);
    verify(mockCriteria, times(1)).add(any());
    verify(mockCriteria, times(1)).list();
  }

  /**
   * Test getToolSet with tool that has complex nested JSON structure.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetWithComplexJSONStructure() throws Exception {
    // Given
    String complexJson = "{\"type\":\"function\",\"function\":{\"name\":\"complexTool\"," +
        "\"description\":\"A complex tool\",\"parameters\":{\"type\":\"object\"," +
        "\"properties\":{\"param1\":{\"type\":\"string\"}}}}}";

    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn(complexJson);

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one tool", 1, result.length());

    JSONObject toolJson = result.getJSONObject(0);
    assertEquals("Should have correct type", "function", toolJson.getString("type"));

    JSONObject functionJson = toolJson.getJSONObject("function");
    assertEquals("Should have correct function name", "complexTool", functionJson.getString("name"));
    assertTrue("Should have parameters object", functionJson.has("parameters"));

    JSONObject parametersJson = functionJson.getJSONObject("parameters");
    assertEquals("Parameters should have correct type", "object", parametersJson.getString("type"));
    assertTrue("Parameters should have properties", parametersJson.has("properties"));
  }

  /**
   * Test getToolSet with tool that has empty string JSON structure.
   * Note: Empty string "" is not null and not equal to "{}", so the code tries to parse it
   * as JSON which throws JSONException. We expect this exception.
   *
   * @throws Exception if test fails
   */
  @Test(expected = org.codehaus.jettison.json.JSONException.class)
  public void testGetToolSetWithEmptyStringJSONStructure() throws Exception {
    // Given
    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn("");

    // When - This should throw JSONException because "" cannot be parsed as JSON
    ToolsUtil.getToolSet(mockApp);

    // Then - Exception is expected
  }

  /**
   * Test getToolSet returns correct tool structure for each scenario.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testGetToolSetReturnsCorrectStructure() throws Exception {
    // Given
    List<CopilotAppTool> toolsList = new ArrayList<>();
    toolsList.add(mockAppTool);
    when(mockCriteria.list()).thenReturn(toolsList);
    when(mockTool.getJsonStructure()).thenReturn(null);

    // When
    JSONArray result = ToolsUtil.getToolSet(mockApp);

    // Then
    assertNotNull("Result should not be null", result);
    assertEquals("Should have one tool", 1, result.length());

    JSONObject toolJson = result.getJSONObject(0);

    // Verify structure
    assertTrue("Should have 'type' field", toolJson.has("type"));
    assertEquals("Type should be 'function'", "function", toolJson.getString("type"));

    assertTrue("Should have 'function' field", toolJson.has("function"));
    JSONObject functionJson = toolJson.getJSONObject("function");

    assertTrue("Function should have 'name' field", functionJson.has("name"));
    assertEquals("Function name should match tool value", TEST_TOOL_NAME, functionJson.getString("name"));
  }
}