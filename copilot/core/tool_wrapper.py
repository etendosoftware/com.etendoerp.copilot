import abc
from typing import Dict, TypedDict, Union

from langchain.tools import BaseTool
from langsmith import traceable

from copilot.core.utils import copilot_debug, copilot_info


def accum_params(input_params, k_args):
    """
    Accumulates parameters from input_params and k_args into a single dictionary.

    This function logs the keys of both input_params and k_args, merges them,
    and returns the combined result. If input_params is None, it initializes it as an empty dictionary.

    Parameters:
    - input_params (dict, optional): The initial dictionary of parameters. Defaults to None.
    - k_args (dict): Additional keyword arguments to be merged into input_params.

    Returns:
    - dict: The merged dictionary of parameters.
    """
    copilot_debug(
        "input_params has the following keys: " + str(input_params.keys() if input_params is not None else "-"))
    copilot_debug("kwarg has the following keys: " + str(k_args.keys()))
    if input_params is None:
        input_params = {}
    # get keys from kwargs
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
        copilot_info("Tool response is not a valid format, it will be parsed as a string. The recommended format is "
                     "an instance of ToolOutputMessage, ToolOutputError, or ToolOutputContent.")
        response = tool_response
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
    handle_validation_error = True

    @traceable
    @abc.abstractmethod
    def run(self, input_params: Dict = None, *args, **kwarg) -> ToolOutput:
        raise NotImplementedError

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
