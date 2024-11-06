import abc
import uuid
from typing import Any, Dict, List, Optional, TypedDict, Union

from copilot.core.utils import copilot_debug, copilot_info
from langchain.tools import BaseTool
from langchain_core.runnables import RunnableConfig
from langchain_core.runnables.config import Callbacks
from langsmith import traceable


def accum_params(input_params: Optional[Dict] = None, k_args: Dict = None) -> Dict:
    """
    Accumulates parameters from `input_params` and `k_args` into a single dictionary.

    This function merges the parameters from `input_params` and `k_args` into a single
    dictionary and returns the result. If `input_params` is None, it initializes it
    as an empty dictionary before merging.

    Parameters:
    - input_params (dict, optional): The initial dictionary of parameters, which will
      be updated with values from `k_args`. If None, a new empty dictionary is created.
    - k_args (dict): A dictionary of additional keyword arguments to be merged into `input_params`.

    Returns:
    - dict: The merged dictionary containing values from both `input_params` and `k_args`.
    """
    copilot_debug(
        "input_params has the following keys: "
        + str(input_params.keys() if input_params is not None else "-")
    )
    copilot_debug("kwarg has the following keys: " + str(k_args.keys()))
    if input_params is None:
        input_params = {}
    if k_args is None:
        return input_params
    for key in k_args:
        input_params[key] = k_args[key]
    return input_params


def parse_response(tool_response):
    """
    Parses the tool response and returns the appropriate message.

    Args:
        tool_response: The response received from the tool.

    Returns:
        The parsed message from the tool response.

    Raises:
        None.
    """
    is_error = False
    if isinstance(tool_response, str):
        response = tool_response
        return response
    if "error" in tool_response:
        response = tool_response["error"]
    elif "message" in tool_response:
        response = tool_response["message"]
    elif "content" in tool_response:
        response = tool_response["content"]
    else:
        copilot_info(
            "Tool response is not a valid format, it will be parsed as a string. The recommended format is "
            "an instance of ToolOutputMessage, ToolOutputError, or ToolOutputContent."
        )
        response = tool_response
    copilot_debug(f"Tool response: {str(response)}")
    return ("ERROR: " if is_error else "") + str(response)


class ToolOutputMessage(TypedDict):
    """
    Typed dictionary for tool output messages.

    This class defines the expected structure of a successful message response from a tool.
    It contains a single key 'message' which holds the message string.

    Attributes:
    - message (str): The success message from the tool.
    """

    message: str


class ToolOutputError(TypedDict):
    """
    Typed dictionary for tool output errors.

    This class defines the expected structure of an error response from a tool.
    It contains a single key 'error' which holds the error message string.

    Attributes:
    - error (str): The error message from the tool.
    """

    error: str


class ToolOutputContent(TypedDict):
    """
    Typed dictionary for tool output content.

    This class defines the expected structure of a content response from a tool.
    It contains a single key 'content' which holds the content string.

    Attributes:
    - content (str): The content provided by the tool.
    """

    content: str


ToolOutput = Union[ToolOutputMessage, ToolOutputError, ToolOutputContent]
"""
Union type for tool output.

This type is a union of ToolOutputMessage, ToolOutputError, and ToolOutputContent,
allowing for a standardized way to handle different types of responses from tools.
"""


class ToolWrapper(BaseTool, metaclass=abc.ABCMeta):
    handle_validation_error: bool = True

    @traceable
    @abc.abstractmethod
    def run(self, input_params: Dict = None, *args, **kwarg) -> ToolOutput:
        raise NotImplementedError

    @traceable
    async def arun(
        self,
        tool_input: Union[str, Dict],
        verbose: Optional[bool] = None,
        start_color: Optional[str] = "green",
        color: Optional[str] = "green",
        callbacks: Callbacks = None,
        *,
        tags: Optional[List[str]] = None,
        metadata: Optional[Dict[str, Any]] = None,
        run_name: Optional[str] = None,
        run_id: Optional[uuid.UUID] = None,
        config: Optional[RunnableConfig] = None,
        tool_call_id: Optional[str] = None,
        **kwargs: Any,
    ) -> Any:
        if self.input_schema is not None:
            copilot_debug("Parsing input with schema to check for errors")
            try:
                self._parse_input(tool_input)
                self.args_schema.model_validate(tool_input)
            except Exception as e:
                copilot_debug(f"Error parsing input: {str(e)}")
                return parse_response(ToolOutputError(error=str(e)))
        else:
            copilot_debug("No input schema provided, skipping input validation")

        result = await super().arun(
            tool_input=tool_input,
            verbose=verbose,
            start_color=start_color,
            color=color,
            callbacks=callbacks,
            tags=tags,
            metadata=metadata,
            run_name=run_name,
            run_id=run_id,
            config=config,
            tool_call_id=tool_call_id,
            **kwargs,
        )
        return result

    @traceable
    def _run(self, input_params: Dict, *args, **kwarg):
        copilot_debug("Running tool synchronously")
        input_params = accum_params(input_params, k_args=kwarg)
        try:
            tool_response = self.run(input_params, *args, **kwarg)
            return parse_response(tool_response)
        except Exception as e:
            copilot_debug(f"Error executing tool {self.name}: " + str(e))
            return parse_response(ToolOutputError(error=str(e)))

    @traceable
    async def _arun(self, input_params: Dict = None, *args, **kwarg):
        """Use the tool asynchronously."""
        copilot_debug("Running tool asynchronously")
        input_params = accum_params(input_params, k_args=kwarg)
        try:
            tool_response = self.run(input_params, *args, **kwarg)
            return parse_response(tool_response)
        except Exception as e:
            copilot_debug(f"Error executing tool {self.name}: " + str(e))
            return parse_response(ToolOutputError(error=str(e)))
