import abc
from typing import Optional

from langchain.callbacks.manager import (
    AsyncCallbackManagerForToolRun,
    CallbackManagerForToolRun,
)
from langchain.tools import BaseTool


class ToolWrapper(BaseTool, metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def run(self, query: str, *args, **kwargs) -> str:
        raise NotImplementedError

    def _run(self, query: str, run_manager: Optional[CallbackManagerForToolRun] = None) -> str:
        self.run(query=query)

    async def _arun(self, query: str, run_manager: Optional[AsyncCallbackManagerForToolRun] = None) -> str:
        """Use the tool asynchronously."""
        raise NotImplementedError("custom_search does not support async")
