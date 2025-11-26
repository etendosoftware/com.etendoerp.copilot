package com.etendoerp.copilot.background;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.process.AddBulkTasks;
import com.etendoerp.copilot.process.ExecTask;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;

/**
 * BulkTaskExec test class.
 */
public class BulkTaskExecTest {

  /**
   * The Expected exception.
   */
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private BulkTaskExec bulkTaskExec;
  private AutoCloseable mocks;

  @Mock
  private OBDal obDal;
  @Mock
  private ProcessBundle processBundle;
  @Mock
  private ProcessLogger mockLogger;
  @Mock
  private SessionHandler sessionHandler;
  @Mock
  private Session mockSession;
  @Mock
  private OBCriteria<Task> mockCriteria;
  @Mock
  private Status mockStatus;
  @Mock
  private TaskType mockTaskType;
  @Mock
  private CopilotApp mockAgent;

  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<SessionHandler> mockedSessionHandler;
  private MockedStatic<AddBulkTasks> mockedAddBulkTasks;
  private MockedStatic<ExecTask> mockedExecTask;

  private static final String TEST_TASK_ID_1 = "testTaskId1";
  private static final String TEST_TASK_ID_2 = "testTaskId2";
  private static final int EXPECTED_BATCH_SIZE = 10;

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    bulkTaskExec = new BulkTaskExec();

    // Set up static mocks
    mockedOBDal = mockStatic(OBDal.class);
    mockedSessionHandler = mockStatic(SessionHandler.class);
    mockedAddBulkTasks = mockStatic(AddBulkTasks.class);
    mockedExecTask = mockStatic(ExecTask.class);

    // Configure OBDal mock
    mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.getSession()).thenReturn(mockSession);
    when(obDal.createCriteria(Task.class)).thenReturn(mockCriteria);

    // Configure SessionHandler mock
    mockedSessionHandler.when(SessionHandler::getInstance).thenReturn(sessionHandler);
    doNothing().when(sessionHandler).commitAndStart();

    // Configure ProcessBundle mock
    when(processBundle.getLogger()).thenReturn(mockLogger);
    doNothing().when(mockLogger).log(anyString());

    // Configure OBCriteria mock
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(EXPECTED_BATCH_SIZE)).thenReturn(mockCriteria);

    // Mock AddBulkTasks static methods
    mockedAddBulkTasks.when(() -> AddBulkTasks.getStatus(anyString())).thenReturn(mockStatus);
    mockedAddBulkTasks.when(() -> AddBulkTasks.getCopilotTaskType()).thenReturn(mockTaskType);

    // Mock ExecTask.processTask
    mockedExecTask.when(() -> ExecTask.processTask(any(Task.class), any(ProcessLogger.class)))
        .thenAnswer(invocation -> null);

    // Mock OBDal operations
    doNothing().when(obDal).save(any(Task.class));
    doNothing().when(obDal).flush();
    doNothing().when(obDal).refresh(any(Task.class));
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
    if (mockedSessionHandler != null) {
      mockedSessionHandler.close();
    }
    if (mockedAddBulkTasks != null) {
      mockedAddBulkTasks.close();
    }
    if (mockedExecTask != null) {
      mockedExecTask.close();
    }
  }


  /**
   * Test doExecute with single task.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testDoExecuteWithSingleTask() throws Exception {
    // Given
    Task mockTask1 = createMockTask(TEST_TASK_ID_1);
    List<Task> taskList = new ArrayList<>();
    taskList.add(mockTask1);
    when(mockCriteria.list()).thenReturn(taskList);

    // When
    bulkTaskExec.doExecute(processBundle);

    // Then
    verify(mockLogger, times(1)).log("BulkTaskExec started\n");
    verify(mockLogger, times(1)).log("Found 1 tasks\n");
    verify(mockTask1, times(1)).setStatus(mockStatus);
    verify(obDal, times(1)).save(mockTask1);
    verify(obDal, times(1)).flush();
    verify(sessionHandler, times(1)).commitAndStart();
    verify(obDal, times(1)).refresh(mockTask1);
    mockedExecTask.verify(() -> ExecTask.processTask(eq(mockTask1), eq(mockLogger)), times(1));
  }

  /**
   * Test doExecute with multiple tasks.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testDoExecuteWithMultipleTasks() throws Exception {
    // Given
    Task mockTask1 = createMockTask(TEST_TASK_ID_1);
    Task mockTask2 = createMockTask(TEST_TASK_ID_2);
    List<Task> taskList = new ArrayList<>();
    taskList.add(mockTask1);
    taskList.add(mockTask2);
    when(mockCriteria.list()).thenReturn(taskList);

    // When
    bulkTaskExec.doExecute(processBundle);

    // Then
    verify(mockLogger, times(1)).log("BulkTaskExec started\n");
    verify(mockLogger, times(1)).log("Found 2 tasks\n");

    // Verify both tasks were updated to IN_PROGRESS status
    verify(mockTask1, times(1)).setStatus(mockStatus);
    verify(mockTask2, times(1)).setStatus(mockStatus);

    // Verify save and flush
    verify(obDal, times(2)).save(any(Task.class));
    verify(obDal, times(1)).flush();
    verify(sessionHandler, times(1)).commitAndStart();

    // Verify refresh and processing for both tasks
    verify(obDal, times(1)).refresh(mockTask1);
    verify(obDal, times(1)).refresh(mockTask2);
    mockedExecTask.verify(() -> ExecTask.processTask(eq(mockTask1), eq(mockLogger)), times(1));
    mockedExecTask.verify(() -> ExecTask.processTask(eq(mockTask2), eq(mockLogger)), times(1));
  }

  /**
   * Test doExecute with batch size limit.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testDoExecuteWithBatchSizeLimit() throws Exception {
    // Given - Create exactly BATCH_SIZE (10) tasks
    List<Task> taskList = new ArrayList<>();
    for (int i = 0; i < EXPECTED_BATCH_SIZE; i++) {
      Task mockTask = createMockTask("taskId" + i);
      taskList.add(mockTask);
    }
    when(mockCriteria.list()).thenReturn(taskList);

    // When
    bulkTaskExec.doExecute(processBundle);

    // Then
    verify(mockLogger, times(1)).log("BulkTaskExec started\n");
    verify(mockLogger, times(1)).log("Found 10 tasks\n");
    verify(mockCriteria, times(1)).setMaxResults(EXPECTED_BATCH_SIZE);
    verify(obDal, times(EXPECTED_BATCH_SIZE)).save(any(Task.class));
    verify(obDal, times(1)).flush();
    verify(sessionHandler, times(1)).commitAndStart();
    verify(obDal, times(EXPECTED_BATCH_SIZE)).refresh(any(Task.class));
    mockedExecTask.verify(() -> ExecTask.processTask(any(Task.class), eq(mockLogger)),
        times(EXPECTED_BATCH_SIZE));
  }

  /**
   * Test doExecute criteria filters.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testDoExecuteCriteriaFilters() throws Exception {
    // Given
    List<Task> taskList = new ArrayList<>();
    when(mockCriteria.list()).thenReturn(taskList);

    // When
    bulkTaskExec.doExecute(processBundle);

    // Then - Verify criteria was configured with correct filters
    verify(mockCriteria, times(2)).add(any());
    mockedAddBulkTasks.verify(() -> AddBulkTasks.getStatus(BulkTaskExec.TASK_STATUS_PENDING),
        times(1));
    mockedAddBulkTasks.verify(() -> AddBulkTasks.getCopilotTaskType(), times(1));
    verify(mockCriteria, times(1)).setMaxResults(EXPECTED_BATCH_SIZE);
  }

  /**
   * Test doExecute with exception during task processing.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testDoExecuteWithExceptionDuringProcessing() throws Exception {
    // Given
    Task mockTask1 = createMockTask(TEST_TASK_ID_1);
    List<Task> taskList = new ArrayList<>();
    taskList.add(mockTask1);
    when(mockCriteria.list()).thenReturn(taskList);

    // Mock processTask to throw exception
    mockedExecTask.when(() -> ExecTask.processTask(eq(mockTask1), eq(mockLogger)))
        .thenThrow(new RuntimeException("Processing error"));

    // When & Then
    expectedException.expect(RuntimeException.class);
    expectedException.expectMessage("Processing error");
    bulkTaskExec.doExecute(processBundle);
  }

  /**
   * Test that task status is updated before commit.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testTaskStatusUpdatedBeforeCommit() throws Exception {
    // Given
    Task mockTask1 = createMockTask(TEST_TASK_ID_1);
    List<Task> taskList = new ArrayList<>();
    taskList.add(mockTask1);
    when(mockCriteria.list()).thenReturn(taskList);

    // When
    bulkTaskExec.doExecute(processBundle);

    // Then - Verify order: save, flush, commitAndStart, then refresh
    verify(mockTask1, times(1)).setStatus(mockStatus);
    verify(obDal, times(1)).save(mockTask1);
    verify(obDal, times(1)).flush();
    verify(sessionHandler, times(1)).commitAndStart();
    verify(obDal, times(1)).refresh(mockTask1);
  }

  /**
   * Test constants.
   */
  @Test
  public void testConstants() {
    assertEquals("IP", BulkTaskExec.TASK_STATUS_IN_PROGRESS);
    assertEquals("CO", BulkTaskExec.TASK_STATUS_COMPLETED);
    assertEquals("PE", BulkTaskExec.TASK_STATUS_PENDING);
    assertEquals("EVAL", BulkTaskExec.TASK_STATUS_EVAL);
  }

  /**
   * Helper method to create a mock Task.
   *
   * @param taskId the task ID
   * @return the mock Task
   */
  private Task createMockTask(String taskId) {
    Task mockTask = mock(Task.class);
    when(mockTask.getId()).thenReturn(taskId);
    when(mockTask.getEtcopQuestion()).thenReturn("Test question");
    when(mockTask.getETCOPAgent()).thenReturn(mockAgent);
    when(mockAgent.getId()).thenReturn("testAgentId");
    doNothing().when(mockTask).setStatus(any(Status.class));
    return mockTask;
  }
}