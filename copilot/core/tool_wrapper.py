import abc
from typing import Dict

from langchain.tools import BaseTool


class ToolWrapper(BaseTool, metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def run(self,  input_params: Dict = None, *args, **kwarg) -> str:
        raise NotImplementedError

    def _run(self,  input_params: Dict, *args, **kwarg) -> str:
        self.run(*args, **kwarg)

    async def _arun(self,  input_params: Dict = None, *args, **kwarg) -> str:
        """Use the tool asynchronously."""
        if input_params == None:
            input_params = {}
            # get keys from kwargs
            for key in kwarg:
                input_params[key] = kwarg[key]

        return self.run( input_params, *args, **kwarg)
