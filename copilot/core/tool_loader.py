import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Final, List, Optional, TypeAlias

import toml

from tools import *  # noqa: F403

from . import tool_installer, utils

# tools need to be imported so they can be inferred from globals
# ruff: noqa: F401
# fmt: off
from .exceptions import (
    ApplicationError,
    ToolConfigFileNotFound,
    ToolDependenciesFileNotFound,
)
from .retrieval_tool import RetrievalTool
from .tool_dependencies import Dependency, ToolsDependencies
from .tool_wrapper import ToolWrapper

# fmt: on
from .utils import SUCCESS_CODE, print_green, print_yellow

LangChainTools: TypeAlias = List[ToolWrapper]

NATIVE_TOOL_IMPLEMENTATION: Final[str] = "copilot"
NATIVE_TOOLS_NODE_NAME: Final[str] = "native_tools"
THIRD_PARTY_TOOLS_NODE_NAME: Final[str] = "third_party_tools"
CONFIGURED_TOOLS_FILENAME: Optional[str] = utils.read_optional_env_var("CONFIGURED_TOOLS_FILENAME", "tools_config.json")
DEPENDENCIES_TOOLS_FILENAME: Optional[str] = utils.read_optional_env_var("DEPENDENCIES_TOOLS_FILENAME", "tools_deps.toml")


class ToolLoader:
    """Responsible for loading the user tools and making them available to the copilot agent."""
    installed_deps = False

    def __init__(
            self,
            config_filename: Optional[str] = CONFIGURED_TOOLS_FILENAME,
            tools_deps_filename: Optional[str] = DEPENDENCIES_TOOLS_FILENAME,
    ):
        self._tools_config = self._get_tool_config(filepath=config_filename)
        self._tools_dependencies = self._get_tool_dependencies(filepath=tools_deps_filename)

    @property
    def native_tool_config(self) -> Dict:
        return self._tools_config.get(NATIVE_TOOLS_NODE_NAME, {})

    @property
    def third_party_tool_config(self) -> Dict:
        return self._tools_config.get(THIRD_PARTY_TOOLS_NODE_NAME, {})

    def _get_tool_config(self, filepath: Optional[str]) -> Dict:
        """Returs the content of the tools configuration file."""
        if not filepath or not Path(filepath).is_file():
            raise ToolConfigFileNotFound()

        with open(filepath, "r") as config_tools_file:
            try:
                return json.load(config_tools_file)
            except json.decoder.JSONDecodeError as ex:
                raise ApplicationError("Unsupported tool configuration file format") from ex

    def _get_tool_dependencies(self, filepath: Optional[str]) -> ToolsDependencies:
        """Returs the content of the tools dependencies formatted."""
        if not filepath or not Path(filepath).is_file():
            raise ToolDependenciesFileNotFound()

        with open(filepath, "r") as toml_file:
            try:
                tools_deps = toml.load(toml_file)
            except toml.TomlDecodeError as ex:
                raise ApplicationError("Unsupported tool dependencies file format") from ex

            tools_dependencies: ToolsDependencies = {}
            for key, value in tools_deps.items():
                tools_dependencies[key] = [
                    # the depencies are defined as depency_name = "version". If the dependency_name has a |, it means
                    # that the name for install is different than the name for import. In this case, the left side of
                    # the | is the name for install and the right side is the name for import.
                    Dependency(name=self.left_side(dependency_name), version=None if version == "*" else version,
                               import_name=self.rigth_side(dependency_name)) for dependency_name, version in value.items()
                ]
            return tools_dependencies

    def rigth_side(self, k):
        return self.split_string(k, '|', False)

    def left_side(self, k):
        return self.split_string(k, '|', True)

    def split_string(self, s: str, delimiter: str, left: bool):
        # amount of times that delimiter appears in string
        delimiter_amount = s.count(delimiter)
        if delimiter_amount <= 0:
            # delimiter is not in string, so left side is the string itself and right side is None
            return s if left else None
        # if delimiter is in string more than once, raise error
        if delimiter_amount > 1:
            raise ValueError("Delimiter appears more than once in string")
        # delimiter is in string once, so split string and return left or right side
        parts = s.split(delimiter)
        return parts[0] if left else parts[1]

    def _is_tool_implemented(self, tool_name: str) -> bool:
        return tool_name in {tool.__name__ for tool in ToolWrapper.__subclasses__()}

    def load_configured_tools(self) -> LangChainTools:
        """Loads the configured tools. If a tool has dependencies, they will be installed dinamically."""
        configured_tools: LangChainTools = []

        # load native tools implemented by copilot
        for tool_name, enabled in self.native_tool_config.items():
            print_green(f"Loading native tool {tool_name}")
            if enabled and self._is_tool_implemented(tool_name):
                class_name = globals()[tool_name]
                configured_tools.append(class_name())
            # nothing todo, tool_name is disabled from config
            print_green(SUCCESS_CODE)

        # load third party tools implemented by users
        for tool_name, enabled in self.third_party_tool_config.items():
            print_green(f"Loading third party tool {tool_name}")
            if enabled and self._is_tool_implemented(tool_name):
                class_name = globals()[tool_name]
                configured_tools.append(class_name())

                if ToolLoader.installed_deps is False:
                    if tool_name in self._tools_dependencies.keys():
                        print_yellow(f"Installing dependencies for {tool_name} tool: ...")
                        tool_installer.install_dependencies(dependencies=self._tools_dependencies[tool_name])
                    ToolLoader.installed_deps = True
                # nothing todo, tool_name has not dependendies defined

            # nothing todo, tool_name is disabled from config
            print_green(SUCCESS_CODE)

        return configured_tools
