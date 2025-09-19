package com.etendoerp.copilot.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.scheduling.ProcessLogger;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * ExecTask test.
 */
public class ExecTaskTest extends WeldBaseTest {
  /**
   * The Expected exception.
   */
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private ExecTask execTask;
  private AutoCloseable mocks;

  @Mock
  private OBDal obDal;
  @Mock
  private Task mockTask;
  @Mock
  private CopilotApp mockAgent;
  @Mock
  private Session mockSession;
  @Mock
  private SessionHandler sessionHandler;
  @Mock
  private ProcessLogger mockLogger;
  @Mock
  private OBPropertiesProvider propertiesProvider;
  @Mock
  private Status mockStatus;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<SessionHandler> mockedSessionHandler;
  private MockedStatic<RestServiceUtil> mockedRestServiceUtil;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private MockedStatic<OBPropertiesProvider> mockedPropertiesProvider;
  private MockedStatic<AddBulkTasks> mockedAddBulkTasks;

  private static final String TEST_TASK_ID = "testTaskId";
  private static final String RECORD_IDS_KEY = "recordIds";
  private static final String RESULT_NOT_NULL_MSG = "Result should not be null";
  private static final String TASK_LIST_NOT_NULL_MSG = "Task list should not be null";
  private static final String SHOULD_HAVE_ONE_TASK_MSG = "Should have one task";
  private static final String SHOULD_BE_CORRECT_TASK_MSG = "Should be the correct task";

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    execTask = new ExecTask();

    // Set up static mocks
    mockedOBDal = mockStatic(OBDal.class);
    mockedSessionHandler = mockStatic(SessionHandler.class);
    mockedRestServiceUtil = mockStatic(RestServiceUtil.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedPropertiesProvider = mockStatic(OBPropertiesProvider.class);
    mockedAddBulkTasks = mockStatic(AddBulkTasks.class);

    // Configure OBDal mock
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.getSession()).thenReturn(mockSession);

    // Configure SessionHandler mock
    mockedSessionHandler.when(SessionHandler::getInstance).thenReturn(sessionHandler);
    doNothing().when(sessionHandler).commitAndStart();

    // Set up basic task mock
    when(mockTask.getId()).thenReturn(TEST_TASK_ID);
    when(mockTask.getEtcopQuestion()).thenReturn("Test question");
    when(mockTask.getETCOPAgent()).thenReturn(mockAgent);
    when(mockAgent.getId()).thenReturn("testAgentId");

    // Mock OBDal operations
    doNothing().when(obDal).save(any(Task.class));
    doNothing().when(obDal).flush();
    doNothing().when(obDal).refresh(any(Task.class));

    // Mock AddBulkTasks.getStatus
    mockedAddBulkTasks.when(() -> AddBulkTasks.getStatus(anyString())).thenReturn(mockStatus);

    // Mock success message
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOP_ExecTask_Success"))
        .thenReturn("Task execution completed successfully for %d tasks");

    // Mock RestServiceUtil response
    JSONObject mockResponse = new JSONObject();
    mockResponse.put("response", "Test response");
    mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(eq(false), eq(null), any(JSONObject.class)))
        .thenReturn(mockResponse);
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
    if (mockedSessionHandler != null) {
      mockedSessionHandler.close();
    }
    if (mockedRestServiceUtil != null) {
      mockedRestServiceUtil.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
    if (mockedPropertiesProvider != null) {
      mockedPropertiesProvider.close();
    }
    if (mockedAddBulkTasks != null) {
      mockedAddBulkTasks.close();
    }
  }

  /**
   * Test action success with single task.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testActionSuccess() throws Exception {
    // Given
    JSONObject parameters = new JSONObject();
    JSONArray recordIds = new JSONArray();
    recordIds.put(TEST_TASK_ID);
    parameters.put(RECORD_IDS_KEY, recordIds);

    when(obDal.get(Task.class, TEST_TASK_ID)).thenReturn(mockTask);
    MutableBoolean isStopped = new MutableBoolean(false);

    // When
    ActionResult result = execTask.action(parameters, isStopped);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertEquals("Should have success type", Result.Type.SUCCESS, result.getType());
    // Note: save() is called multiple times due to method chain, so we use atLeastOnce
    verify(obDal, atLeastOnce()).save(mockTask);
    verify(obDal, atLeastOnce()).flush();
    verify(sessionHandler, atLeastOnce()).commitAndStart();
  }

  /**
   * Test getTaskList with recordIds.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testGetTaskListWithRecordIds() throws Exception {
    // Given
    JSONObject parameters = new JSONObject();
    JSONArray recordIds = new JSONArray();
    recordIds.put(TEST_TASK_ID);
    parameters.put(RECORD_IDS_KEY, recordIds);

    when(obDal.get(Task.class, TEST_TASK_ID)).thenReturn(mockTask);

    // When
    List<Task> tasks = ExecTask.getTaskList(parameters);

    // Then
    assertNotNull(TASK_LIST_NOT_NULL_MSG, tasks);
    assertEquals(SHOULD_HAVE_ONE_TASK_MSG, 1, tasks.size());
    assertEquals(SHOULD_BE_CORRECT_TASK_MSG, mockTask, tasks.get(0));
  }

  /**
   * Test getTaskList with asyncProcessId.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testGetTaskListWithAsyncProcessId() throws Exception {
    // Given
    JSONObject afterParams = new JSONObject();
    afterParams.put("etask_task_id", TEST_TASK_ID);
    
    JSONObject paramsJson = new JSONObject();
    paramsJson.put("after", afterParams);
    
    JSONObject parameters = new JSONObject();
    parameters.put("asyncProcessId", "someProcessId");
    parameters.put("params", paramsJson.toString());

    when(obDal.get(Task.class, TEST_TASK_ID)).thenReturn(mockTask);

    // When
    List<Task> tasks = ExecTask.getTaskList(parameters);

    // Then
    assertNotNull(TASK_LIST_NOT_NULL_MSG, tasks);
    assertEquals(SHOULD_HAVE_ONE_TASK_MSG, 1, tasks.size());
    assertEquals(SHOULD_BE_CORRECT_TASK_MSG, mockTask, tasks.get(0));
  }

  /**
   * Test processTask success without async jobs.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testProcessTaskSuccessWithoutAsyncJobs() throws Exception {
    // Given
    mockPropertiesForAsyncJobs(false);
    doNothing().when(mockTask).setStatus(any());
    doNothing().when(mockTask).setEtcopResponse(anyString());

    // When
    ExecTask.processTask(mockTask, mockLogger);

    // Then
    verify(obDal, atLeastOnce()).save(mockTask);
    verify(obDal, atLeastOnce()).flush();
    verify(sessionHandler, atLeastOnce()).commitAndStart();
    verify(mockLogger, atLeastOnce()).log(anyString());
  }

  /**
   * Test execTask execution.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testExecTask() throws Exception {
    // Given
    doNothing().when(mockTask).setEtcopResponse(anyString());

    // When
    ExecTask.execTask(mockTask);

    // Then
    verify(mockTask).setEtcopResponse(anyString());
    verify(obDal).save(mockTask);
    verify(obDal).flush();
  }

  /**
   * Test getInputClass returns Task class.
   */
  @Test
  public void testGetInputClass() {
    // When
    Class<Task> inputClass = execTask.getInputClass();

    // Then
    assertEquals("Should return Task class", Task.class, inputClass);
  }

  /**
   * Test action with exception handling.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testActionWithException() throws Exception {
    // Given
    JSONObject parameters = new JSONObject();
    JSONArray recordIds = new JSONArray();
    recordIds.put(TEST_TASK_ID);
    parameters.put(RECORD_IDS_KEY, recordIds);

    when(obDal.get(Task.class, TEST_TASK_ID)).thenThrow(new RuntimeException("Database error"));
    MutableBoolean isStopped = new MutableBoolean(false);

    // When
    ActionResult result = execTask.action(parameters, isStopped);

    // Then
    assertNotNull(RESULT_NOT_NULL_MSG, result);
    assertEquals("Should have error type", Result.Type.ERROR, result.getType());
    assertTrue("Should contain error message", result.getMessage().contains("Error during process execution"));
  }

  /**
   * Test processTask with simple verification.
   *
   * @throws Exception
   *     the exception
   */
  @Test
  public void testProcessTaskSimple() throws Exception {
    // Given
    mockPropertiesForAsyncJobs(false);
    doNothing().when(mockTask).setStatus(any());
    doNothing().when(mockTask).setEtcopResponse(anyString());

    // When
    ExecTask.processTask(mockTask, null);

    // Then - Just verify the task was processed without specific call counts
    verify(mockTask, atLeastOnce()).setStatus(any());
  }

  /**
   * Helper method to mock properties for async jobs configuration.
   */
  private void mockPropertiesForAsyncJobs(boolean enabled) {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("kafka.enable", String.valueOf(enabled));
    
    mockedPropertiesProvider.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
    when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);
  }
}
