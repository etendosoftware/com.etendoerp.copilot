/*
 *************************************************************************
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.team;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class TeamTaskStateTest {
  @Test
  public void processesTasksInQueueOrderAndTracksCompletion() {
    TeamTaskState state = new TeamTaskState();
    assertEquals("Added 2 tasks.", state.apply("add_tasks", Arrays.asList("one", "two")));
    assertEquals("New Current Task is 'one'", state.apply("get_next", null));
    assertEquals("Tasks to process: 1. Current task: one. Done tasks: 0", state.apply("status", null));
    assertEquals("Task 'one' marked as done.", state.apply("mark_done", null));
    assertEquals("New Current Task is 'two'", state.apply("get_next", null));
  }

  @Test
  public void handlesEmptyQueueRepeatedCompletionAndInvalidMode() {
    TeamTaskState state = new TeamTaskState();
    assertEquals("There are no tasks to process.", state.apply("get_next", null));
    assertEquals("No current task to mark as done.", state.apply("mark_done", null));
    assertEquals("Invalid mode selected.", state.apply("unknown", null));
    assertEquals("No tasks added.", state.apply("add_tasks", Collections.emptyList()));
  }

  @Test
  public void rejectsInvalidOrExcessiveTasksAtomically() {
    TeamTaskState state = new TeamTaskState();
    assertTrue(state.apply("add_tasks", Arrays.asList("ok", " ")).contains("no tasks were added"));
    assertTrue(state.pendingTasks().isEmpty());
    assertTrue(state.apply("add_tasks", Collections.nCopies(TeamTaskState.MAX_PENDING_TASKS + 1, "x"))
        .contains("limit reached"));
    assertTrue(state.pendingTasks().isEmpty());
  }
}
