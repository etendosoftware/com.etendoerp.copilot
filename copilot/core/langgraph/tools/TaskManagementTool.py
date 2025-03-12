import operator
from typing import List, Annotated, Optional
from colorama import Fore
from langchain_core.messages import ToolMessage
from langchain_core.runnables import RunnableConfig
from langchain_core.tools import tool, InjectedToolCallId
from langgraph.prebuilt import InjectedState
from langgraph.prebuilt.chat_agent_executor import AgentState
from langgraph.store.memory import InMemoryStore
from langgraph.types import Command
from copilot.core.utils import copilot_debug_custom

store = InMemoryStore()


class LangSupervisorState(AgentState):
    """State for the LangSupervisor tool."""
    tasks_to_process: Annotated[List[str], operator.add]
    current_task: str
    planning: str
    done_tasks: List[str]


class TaskManager:
    """Handles task management operations."""

    @staticmethod
    def initialize_state(config: RunnableConfig, state: dict) -> tuple:
        thread_id = config.get("metadata", {}).get("thread_id")
        namespace = (thread_id, "state")
        saved_state = store.search(namespace)
        if saved_state:
            for item in saved_state:
                state[item.key] = item.value
        state.setdefault("tasks_to_process", [])
        state.setdefault("done_tasks", [])
        state.setdefault("current_task", None)
        state.setdefault("planning", None)
        return namespace, state

    @staticmethod
    def save_state(namespace: tuple, state: dict) -> None:
        for key in ["tasks_to_process", "current_task", "done_tasks"]:
            store.put(namespace, key, state[key])

    @staticmethod
    def get_status(state: dict) -> str:
        return (
            "===================\n"
            f"Tasks to process: {len(state['tasks_to_process'])}\n"
            f"Done tasks: {len(state['done_tasks'])}\n"
            f"**Current task**: {state['current_task'] or 'None'}\n"
            f"**Planning**: {state['planning'] or 'None'}\n"
            "===================\n"
        )

    @staticmethod
    def create_tool_message(content: str, tool_call_id: str) -> List[ToolMessage]:
        return [ToolMessage(content=content, tool_call_id=tool_call_id)]

    def get_next(self, state: dict) -> str:
        if not state["tasks_to_process"]:
            return "There are no tasks to process."
        next_task = state["tasks_to_process"].pop(0)
        state["current_task"] = next_task
        return f"New Current Task is '{next_task}'."

    def add_tasks(self, state: dict, new_tasks: Optional[List[str]]) -> str:
        if not new_tasks:
            return "No tasks added."
        state["tasks_to_process"].extend(new_tasks)
        return f"Added {len(new_tasks)} tasks."

    def mark_done(self, state: dict, result: Optional[str]) -> str:
        if not state["current_task"]:
            return f"No current task to mark as done.\nHave {len(state['tasks_to_process'])} tasks pending."
        if not result:
            return f"Error: You must provide a result for the task.\nHave {len(state['tasks_to_process'])} tasks pending."

        completed = state["current_task"]
        if state["planning"]:
            completed += f"\n{state['planning']}"
        if result:
            completed += f"\n{result}"
        state["done_tasks"].append(completed)
        state["current_task"] = None
        state["planning"] = None
        return f"Task '{completed}' marked as done.\nHave {len(state['tasks_to_process'])} tasks pending."

    def plan_task(self, state: dict, planning: Optional[str]) -> str:
        if state["current_task"]:
            state["planning"] = planning
            return f"Planning task '{planning}' to do."
        state["planning"] = "Is needed a more detailed planning"
        state["current_task"] = planning
        return f"Planning task '{planning}' to do."

    def report(self, state: dict) -> str:
        return "Tasks done:\n" + "\n".join(state["done_tasks"]) + "\n" + self.get_status(state)


@tool()
def task_management_tool(
        mode: Annotated[str, "Mode: 'get_next', 'add_tasks', 'status', 'mark_done', 'planning', 'report'"],
        state: Annotated[dict, InjectedState],
        tool_call_id: Annotated[str, InjectedToolCallId],
        config: RunnableConfig,
        new_tasks: Optional[List[str]] = None,
        planning: Optional[str] = None,
        result: Optional[str] = None,
) -> Command:
    """
    Manages tasks with different modes. See TaskManager for operations.
    Important: No concurrent calls allowed in same response.
    """
    manager = TaskManager()
    namespace, state = manager.initialize_state(config, state)

    copilot_debug_custom(f"Task Mgmt: mode: {mode} - new: {new_tasks} - plan: {planning}", color=Fore.MAGENTA)

    # Map modes to their handler functions
    mode_handlers = {
        "status": lambda: manager.get_status(state),
        "get_next": lambda: manager.get_next(state),
        "add_tasks": lambda: manager.add_tasks(state, new_tasks),
        "mark_done": lambda: manager.mark_done(state, result),
        "planning": lambda: manager.plan_task(state, planning),
        "report": lambda: manager.report(state),
    }

    # Execute the appropriate handler or return error
    message = mode_handlers.get(mode, lambda: "Invalid mode selected.")()

    # Save and return
    manager.save_state(namespace, state)
    messages = manager.create_tool_message(message, tool_call_id)
    copilot_debug_custom(f"Task Result: {mode} - {message}", color=Fore.GREEN)

    return Command(update={
        "tasks_to_process": state["tasks_to_process"],
        "current_task": state["current_task"],
        "done_tasks": state["done_tasks"],
        "messages": messages,
    })