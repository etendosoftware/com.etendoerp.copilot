import abc
from typing import Dict

from langchain.tools import BaseTool

from copilot.core.utils import copilot_debug


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


class ToolWrapper(BaseTool, metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def run(self, input_params: Dict = None, *args, **kwarg):
        raise NotImplementedError

    def _run(self, input_params: Dict, *args, **kwarg):
        copilot_debug("Running tool synchronously")
        input_params = accum_params(input_params, k_args=kwarg)
        self.run(input_params, *args, **kwarg)

    async def _arun(self, input_params: Dict = None, *args, **kwarg):
        """Use the tool asynchronously."""
        copilot_debug("Running tool asynchronously")
        input_params = accum_params(input_params, k_args=kwarg)

        return self.run(input_params, *args, **kwarg)
