from typing import Annotated, List

from langchain_core.messages import ToolMessage
from langchain_core.tools import InjectedToolCallId, tool
from langgraph.prebuilt import InjectedState
from langgraph.types import Command


def new_toolmessage(next_tasks, tool_call_id):
    """
    Creates a new ToolMessage.

    This function generates a list containing a single ToolMessage object,
    which includes the provided tasks and tool call ID.

    Args:
        next_tasks (Any): The tasks to be included in the message content.
        tool_call_id (str): The unique identifier for the tool call.

    Returns:
        list[ToolMessage]: A list containing one ToolMessage object.
    """
    return [ToolMessage(content=str(next_tasks), tool_call_id=tool_call_id)]


@tool()
def task_management_tool(
    mode: Annotated[str, "Mode of operation: 'get_next', 'add_tasks', 'status', 'mark_done'"],
    state: Annotated[dict, InjectedState],
    tool_call_id: Annotated[str, InjectedToolCallId],
    new_tasks: List[str] = None,
):
    """
    Unified task management tool.

    Provides task management capabilities with multiple modes of operation:
    - 'get_next': Retrieve and set the next task to process.
    - 'add_tasks': Add a list of tasks to the processing queue.
    - 'status': Retrieve the current status of all tasks.
    - 'mark_done': Mark the current task as completed.

    Args:
        mode (str): Mode of operation. One of 'get_next', 'add_tasks', 'status', 'mark_done'.
        state (dict): The injected state dictionary containing task queues and statuses.
        tool_call_id (str): Unique identifier for the tool call.
        new_tasks (List[str], optional): List of new tasks to add when mode is 'add_tasks'.

    Returns:
        Command: An object representing the state updates and generated tool messages.
    """
    if "tasks_to_process" not in state or state["tasks_to_process"] is None:
        state["tasks_to_process"] = []
    if "done_tasks" not in state or state["done_tasks"] is None:
        state["done_tasks"] = []
    if "current_task" not in state:
        state["current_task"] = None

    if mode == "get_next":
        return get_next(state, tool_call_id)

    elif mode == "add_tasks":
        return add_tasks(new_tasks, state, tool_call_id)

    elif mode == "status":
        return status(state, tool_call_id)

    elif mode == "mark_done":
        return mark_done(state, tool_call_id)

    else:
        return Command(
            update={
                "messages": new_toolmessage("Invalid mode selected.", tool_call_id),
            }
        )


def mark_done(state, tool_call_id):
    """
    Marks the current task as completed.

    Moves the current task to the done_tasks list and clears the current task.

    Args:
        state (dict): The state dictionary containing tasks and status information.
        tool_call_id (str): Unique identifier for the tool call.

    Returns:
        Command: The state update with the task marked as done and a confirmation message.
    """
    if state["current_task"]:
        state["done_tasks"].append(state["current_task"])
        state["current_task"] = None
    return Command(
        update={
            "tasks_to_process": state["tasks_to_process"],
            "current_task": None,
            "done_tasks": state["done_tasks"],
            "messages": new_toolmessage(
                (
                    f"Task '{state['done_tasks'][-1]}' marked as done."
                    if state["done_tasks"]
                    else "No current task to mark as done."
                ),
                tool_call_id,
            ),
        }
    )


def status(state, tool_call_id):
    """
    Retrieves the current status of task queues.

    Provides a summary of the number of pending, current, and completed tasks.

    Args:
        state (dict): The state dictionary containing tasks and status information.
        tool_call_id (str): Unique identifier for the tool call.

    Returns:
        Command: The state update with the current status and a status message.
    """
    status_message = (
        f"Tasks to process: {len(state['tasks_to_process'])}. "
        f"Current task: {state['current_task']}. "
        f"Done tasks: {len(state['done_tasks'])}"
    )
    return Command(
        update={
            "tasks_to_process": state["tasks_to_process"],
            "current_task": state["current_task"],
            "done_tasks": state["done_tasks"],
            "messages": new_toolmessage(status_message, tool_call_id),
        }
    )


def add_tasks(new_tasks, state, tool_call_id):
    """
    Adds new tasks to the processing queue.

    Args:
        new_tasks (List[str]): List of new tasks to add.
        state (dict): The state dictionary containing tasks and status information.
        tool_call_id (str): Unique identifier for the tool call.

    Returns:
        Command: The state update with the updated task list and a confirmation message.
    """
    if new_tasks:
        state["tasks_to_process"].extend(new_tasks)
    return Command(
        update={
            "tasks_to_process": state["tasks_to_process"],
            "current_task": state["current_task"],
            "done_tasks": state["done_tasks"],
            "messages": new_toolmessage(
                f"Added {len(new_tasks)} tasks." if new_tasks else "No tasks added.",
                tool_call_id,
            ),
        }
    )


def get_next(state, tool_call_id):
    """
    Retrieves and sets the next task to process.

    If there are tasks in the queue, the next task is moved to 'current_task'.
    If there are no tasks, a message is returned indicating no tasks are pending.

    Args:
        state (dict): The state dictionary containing tasks and status information.
        tool_call_id (str): Unique identifier for the tool call.

    Returns:
        Command: The state update with the next task set as current, or a message indicating no tasks.
    """
    if not state["tasks_to_process"]:
        return Command(
            update={
                "task_to_process": [],
                "current_task": state["current_task"],
                "done_tasks": state["done_tasks"],
                "messages": new_toolmessage("There are no tasks to process.", tool_call_id),
            }
        )
    next_task = state["tasks_to_process"].pop(0)
    state["current_task"] = next_task
    return Command(
        update={
            "tasks_to_process": state["tasks_to_process"],
            "current_task": next_task,
            "done_tasks": state["done_tasks"],
            "messages": new_toolmessage(f"New Current Task is '{next_task}'", tool_call_id),
        }
    )
