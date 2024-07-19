import abc
from typing import Dict

from langchain.tools import BaseTool
from langsmith import traceable


class ToolWrapper(BaseTool, metaclass=abc.ABCMeta):
    handle_validation_error = True

    @traceable
    @abc.abstractmethod
    def run(self, input_params: Dict = None, *args, **kwarg) -> str:
        raise NotImplementedError

    @traceable
    def _run(self, input_params: Dict, *args, **kwarg) -> str:
        self.run(*args, **kwarg)

    @traceable
    async def _arun(self, input_params: Dict = None, *args, **kwarg) -> str:
        """Use the tool asynchronously."""
        if input_params == None:
            input_params = {}
            # get keys from kwargs
            for key in kwarg:
                input_params[key] = kwarg[key]

        return self.run(input_params, *args, **kwarg)
