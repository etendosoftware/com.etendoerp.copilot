import json
from typing import Dict, Final, List

# tools need to be imported so they can be inferred from globals
from .tool_wrapper import ToolWrapper

CONFIGURED_TOOLS_FILENAME: Final[str] = "tools_config.json"


def load_configured_tools(config_filename: str = CONFIGURED_TOOLS_FILENAME) -> List[ToolWrapper]:
    configured_tools: List[ToolWrapper] = []
    with open(config_filename, "r") as config_tool_file:
        tool_config: Dict = json.load(config_tool_file)

        for tool_implementation in ToolWrapper.__subclasses__():
            if tool_implementation.__name__ in tool_config:
                class_name = globals()[tool_implementation.__name__]
                configured_tools.append(class_name())

    return configured_tools


configured_tools: List[ToolWrapper] = load_configured_tools()
