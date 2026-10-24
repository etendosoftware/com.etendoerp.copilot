/*
 *************************************************************************
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.team;

import java.util.ArrayList;
import java.util.List;

/** Request-scoped supervisor task state, stored only in one user's ToolExecutionContext. */
public final class TeamTaskState {
  public static final String CONTEXT_ATTRIBUTE = "copilot.team.task-state.v1";
  public static final int MAX_PENDING_TASKS = 1000;
  public static final int MAX_TASK_LENGTH = 4096;

  private final List<String> pending = new ArrayList<>();
  private final List<String> done = new ArrayList<>();
  private String current;

  public synchronized String apply(String mode, List<String> newTasks) {
    switch (mode == null ? "" : mode) {
      case "get_next":
        if (pending.isEmpty()) return "There are no tasks to process.";
        current = pending.remove(0);
        return "New Current Task is '" + current + "'";
      case "add_tasks":
        int requested = newTasks == null ? 0 : newTasks.size();
        if (requested == 0) return "No tasks added.";
        if (pending.size() + requested > MAX_PENDING_TASKS) {
          return "Task queue limit reached; no tasks were added.";
        }
        List<String> checked = new ArrayList<>(requested);
        for (String task : newTasks) {
          if (task == null || task.isBlank() || task.length() > MAX_TASK_LENGTH) {
            return "Tasks must be non-empty strings no longer than " + MAX_TASK_LENGTH + " characters; no tasks were added.";
          }
          checked.add(task);
        }
        pending.addAll(checked);
        return "Added " + checked.size() + " tasks.";
      case "status":
        return "Tasks to process: " + pending.size() + ". Current task: " + (current == null ? "None" : current)
            + ". Done tasks: " + done.size();
      case "mark_done":
        if (current == null) return "No current task to mark as done.";
        if (done.size() >= MAX_PENDING_TASKS) return "Completed task limit reached; current task was not marked done.";
        String completed = current;
        done.add(completed);
        current = null;
        return "Task '" + completed + "' marked as done.";
      default:
        return "Invalid mode selected.";
    }
  }

  public synchronized List<String> pendingTasks() {
    return List.copyOf(pending);
  }

  public synchronized List<String> doneTasks() {
    return List.copyOf(done);
  }

  public synchronized String currentTask() {
    return current;
  }
}
