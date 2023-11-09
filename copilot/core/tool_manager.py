import importlib
import json
import os
import toml

from pip._internal import main as pip_main
from typing import Dict, Final, List, Optional, TypeAlias


from tools import *  # noqa: F403

# tools need to be imported so they can be inferred from globals
# ruff: noqa: F401
# fmt: off
from .bastian_tool import BastianFetcher
from .exceptions import ToolConfigFileNotFound, ToolDependenciesFileNotFound
from .retrieval_tool import RetrievalTool
from .tool_wrapper import ToolWrapper

# fmt: on

LangChainTools: TypeAlias = List[ToolWrapper]


NATIVE_TOOL_IMPLEMENTATION: Final[str] = "copilot"
NATIVE_TOOLS_NODE_NAME: Final[str] = "native_tools"
THIRD_PARTY_TOOLS_NODE_NAME: Final[str] = "third_party_tools"
CONFIGURED_TOOLS_FILENAME: Optional[str] = os.getenv("CONFIGURED_TOOLS_FILENAME")
DEPENDENCIES_TOOLS_FILENAME: Optional[str] = os.getenv("DEPENDENCIES_TOOLS_FILENAME")


# docker: https://stackoverflow.com/questions/26153686/how-do-i-run-a-command-on-an-already-existing-docker-container

from dataclasses import dataclass

@dataclass
class Dependency:
    name: str
    version: Optional[str] = None

    def fullname(self) -> str:
        return f"{self.name}{self.version or ''}"

Dependencies: TypeAlias = List[Dependency]
ToolsDependencies: TypeAlias = Dict[str, Dependencies]

def install_deps(tools_deps: Dependencies):
    def install(package):
        print(f"Running pip install {dependency_lib.fullname()}")
        pip_main(['install', package])

    for dependency_lib in tools_deps:
        print(f"Importing {dependency_lib.fullname()}")
        try:
            importlib.import_module(dependency_lib.name)
        except Exception as e:
            print(str(e))
            try:
                install(dependency_lib.fullname())
            except Exception as e:
                print(f"{dependency_lib.fullname()} installation fails, please try manually and rerun copilot")


def get_tool_dependencies(tools_deps_filename: str) -> ToolsDependencies:
    with open(tools_deps_filename, 'r') as toml_file:
        tools_deps = toml.load(toml_file)

    deps_mapping: ToolsDependencies = {}
    for key, value in tools_deps.items():
        deps_mapping[key] = [
            Dependency(name=k,version=None if v == '*' else v) for k, v in value.items()
        ]
    return deps_mapping

def load_configured_tools(
    config_filename: Optional[str] = CONFIGURED_TOOLS_FILENAME,
    tools_deps_filename: Optional[str] = DEPENDENCIES_TOOLS_FILENAME,
) -> LangChainTools:
    """_summary_

    Args:
        config_filename (Optional[str], optional): _description_. Defaults to CONFIGURED_TOOLS_FILENAME.
        tools_deps_filename (Optional[str], optional): _description_. Defaults to DEPENDENCIES_TOOLS_FILENAME.

    Raises:
        ToolConfigFileNotFound: _description_
        ToolDependenciesFileNotFound: _description_
        Exception: _description_

    Returns:
        LangChainTools: _description_
    """
    if not config_filename:
        # TODO: check if file exist
        raise ToolConfigFileNotFound()

    if not tools_deps_filename:
        # TODO: check if file exist
        raise ToolDependenciesFileNotFound()

    configured_tools: List[ToolWrapper] = []
    with open(config_filename, "r") as config_tool_file:
        try:
            tool_config: Dict = json.load(config_tool_file)
        except json.decoder.JSONDecodeError as ex:
            raise Exception("Unsupported tool configuration file format") from ex

        native_tool_config = tool_config.get(NATIVE_TOOLS_NODE_NAME, {})
        third_party_tool_config = tool_config.get(THIRD_PARTY_TOOLS_NODE_NAME, {})

        third_party_tool_deps_mapping: Dict = get_tool_dependencies(tools_deps_filename)
        for tool_implementation in ToolWrapper.__subclasses__():
            is_native_tool = tool_implementation.__module__.split(".")[0] == NATIVE_TOOL_IMPLEMENTATION
            is_third_party_tool_config = third_party_tool_config.get(tool_implementation.__name__, False)
            if (
                is_native_tool and native_tool_config.get(tool_implementation.__name__, False)
            ) or is_third_party_tool_config:
                class_name = globals()[tool_implementation.__name__]
                configured_tools.append(class_name())
            if is_third_party_tool_config:
                tools_deps = third_party_tool_deps_mapping.get(tool_implementation.__name__, None)
                if tools_deps:
                    install_deps(tools_deps=tools_deps)

    return configured_tools


configured_tools: LangChainTools = load_configured_tools()
