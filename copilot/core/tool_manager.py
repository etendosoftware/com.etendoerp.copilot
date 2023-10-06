import json
from typing import Dict, Final, List

from tools import *  # noqa: F403

# tools need to be imported so they can be inferred from globals
# ruff: noqa: F401
# fmt: off
from .bastian_tool import BastianFetcher
from .retrieval_tool import RetrievalTool
from .tool_wrapper import ToolWrapper
from tools.XML_translation_tool import XML_translation_tool
# fmt: on


CONFIGURED_TOOLS_FILENAME: Final[str] = "tools_config.json"
NATIVE_TOOL_IMPLEMENTATION: Final[str] = "copilot"
NATIVE_TOOLS_NODE_NAME: Final[str] = "native_tools"
THIRD_PARTY_TOOLS_NODE_NAME: Final[str] = "third_party_tools"


def load_configured_tools(config_filename: str = CONFIGURED_TOOLS_FILENAME) -> List[ToolWrapper]:
    configured_tools: List[ToolWrapper] = []
    with open(config_filename, "r") as config_tool_file:
        try:
            tool_config: Dict = json.load(config_tool_file)
        except json.decoder.JSONDecodeError as ex:
            raise Exception("Unsupported tool configuration file format") from ex

        native_tool_config = tool_config.get(NATIVE_TOOLS_NODE_NAME, {})
        third_party_tool_config = tool_config.get(THIRD_PARTY_TOOLS_NODE_NAME, {})

        for tool_implementation in ToolWrapper.__subclasses__():
            is_native_tool = tool_implementation.__module__.split(".")[0] == NATIVE_TOOL_IMPLEMENTATION
            if (
                is_native_tool and native_tool_config.get(tool_implementation.__name__, False)
            ) or third_party_tool_config.get(tool_implementation.__name__, False):
                class_name = globals()[tool_implementation.__name__]
                configured_tools.append(class_name())

    return configured_tools


configured_tools: List[ToolWrapper] = load_configured_tools()
