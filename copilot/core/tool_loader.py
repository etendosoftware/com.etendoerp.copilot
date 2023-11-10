import json
import os
import toml

from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Final, List, Optional, TypeAlias


from tools import *  # noqa: F403

# tools need to be imported so they can be inferred from globals
# ruff: noqa: F401
# fmt: off
from .bastian_tool import BastianFetcher
from .exceptions import ApplicationError, ToolConfigFileNotFound, ToolDependenciesFileNotFound
from .retrieval_tool import RetrievalTool
from .tool_wrapper import ToolWrapper
from .tool_dependencies import Dependency, ToolsDependencies
from . import tool_installer
# fmt: on

from .utils import print_green, print_yellow, SUCCESS_CODE

LangChainTools: TypeAlias = List[ToolWrapper]


NATIVE_TOOL_IMPLEMENTATION: Final[str] = "copilot"
NATIVE_TOOLS_NODE_NAME: Final[str] = "native_tools"
THIRD_PARTY_TOOLS_NODE_NAME: Final[str] = "third_party_tools"
CONFIGURED_TOOLS_FILENAME: Optional[str] = os.getenv("CONFIGURED_TOOLS_FILENAME")
DEPENDENCIES_TOOLS_FILENAME: Optional[str] = os.getenv("DEPENDENCIES_TOOLS_FILENAME")



class ToolLoader:
    """Responsible for loading the user tools and making them available to the copilot agent
    """

    def __init__(
        self,
        config_filename: Optional[str] = CONFIGURED_TOOLS_FILENAME,
        tools_deps_filename: Optional[str] = DEPENDENCIES_TOOLS_FILENAME
    ):
        self._tools_config = self._get_tool_config(filepath=config_filename)
        self._tools_dependencies = self._get_tool_dependecies(filepath=tools_deps_filename)

    @property
    def native_tool_config(self) -> Dict:
        return self._tools_config.get(NATIVE_TOOLS_NODE_NAME, {})

    @property
    def third_party_tool_config(self) -> Dict:
        return self._tools_config.get(THIRD_PARTY_TOOLS_NODE_NAME, {})

    def _get_tool_config(self, filepath: Optional[str]) -> Dict:
        """Returs the content of the tools configuration file.
        """
        if not filepath or not Path(filepath).is_file():
            raise ToolConfigFileNotFound()

        with open(filepath, "r") as config_tools_file:
            try:
                return json.load(config_tools_file)
            except json.decoder.JSONDecodeError as ex:
                raise ApplicationError("Unsupported tool configuration file format") from ex

    def _get_tool_dependecies(self, filepath: Optional[str]) -> ToolsDependencies:
        """Returs the content of the tools dependencies formatted
        """
        if not filepath or not Path(filepath).is_file():
            raise ToolDependenciesFileNotFound()

        with open(filepath, 'r') as toml_file:
            try:
                tools_deps = toml.load(toml_file)
            except toml.TomlDecodeError as ex:
                raise ApplicationError("Unsupported tool dependencies file format") from ex

            tools_dependencies: ToolsDependencies = {}
            for key, value in tools_deps.items():
                tools_dependencies[key] = [
                    Dependency(name=k,version=None if v == '*' else v) for k, v in value.items()
                ]
            return tools_dependencies

    def _is_tool_implemented(self, tool_name: str) -> bool:
        return tool_name in {tool.__name__ for tool in ToolWrapper.__subclasses__()}

    def load_configured_tools(self) -> LangChainTools:
        """Loads the configured tools. If a tool has dependencies, they
        will be installed dinamically
        """
        configured_tools: LangChainTools = []

        # load native tools implemented by copilot
        for tool_name, enabled in self.native_tool_config.items():
            print_green(f'Loading native tool {tool_name}')
            if enabled and self._is_tool_implemented(tool_name):
                class_name = globals()[tool_name]
                configured_tools.append(class_name())
            # nothing todo, tool_name is disabled from config
            print_green(SUCCESS_CODE)

        # load third party tools implemented by users
        for tool_name, enabled in self.third_party_tool_config.items():
            print_green(f'Loading third party tool {tool_name}')
            if enabled and self._is_tool_implemented(tool_name):
                class_name = globals()[tool_name]
                configured_tools.append(class_name())

                if tool_name in self._tools_dependencies.keys():
                    print_yellow(f"Installing dependencies for {tool_name} tool: ...")
                    tool_installer.install_dependencies(dependencies=self._tools_dependencies[tool_name])
                # nothing todo, tool_name has not dependendies defined

            # nothing todo, tool_name is disabled from config
            print_green(SUCCESS_CODE)

        return configured_tools
