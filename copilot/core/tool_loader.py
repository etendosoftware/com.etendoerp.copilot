import json
import threading
from pathlib import Path
from typing import Any, Dict, Final, List, Optional, TypeAlias

import toml
from copilot.baseutils.logging_envvar import (
    copilot_debug,
    copilot_info,
    print_green,
    print_yellow,
    read_optional_env_var,
)

from . import tool_installer, utils

# tools need to be imported so they can be inferred from globals
# ruff: noqa: F401
# fmt: off
from .exceptions import (
    ApplicationError,
    ToolDependenciesFileNotFound,
)
from .kb_utils import get_kb_tool
from .schemas import AssistantSchema, ToolSchema
from .tool_dependencies import Dependency, ToolsDependencies
from .tool_wrapper import CopilotTool, ToolWrapper
from .toolgen.ApiTool import (
    generate_tools_from_openapi as generate_tools_from_openapi_old,
)
from .toolgen.openapi_tool_gen import generate_tools_from_openapi

# fmt: on

LangChainTools: TypeAlias = List[ToolWrapper]

NATIVE_TOOL_IMPLEMENTATION: Final[str] = "copilot"  # noqa: F405
NATIVE_TOOLS_NODE_NAME: Final[str] = "native_tools"  # noqa: F405
THIRD_PARTY_TOOLS_NODE_NAME: Final[str] = "third_party_tools"  # noqa: F405
CONFIGURED_TOOLS_FILENAME: Optional[str] = read_optional_env_var(
    "CONFIGURED_TOOLS_FILENAME", "tools_config.json"
)
DEPENDENCIES_TOOLS_FILENAME: Optional[str] = read_optional_env_var(
    "DEPENDENCIES_TOOLS_FILENAME", "tools_deps.toml"
)


class ToolLoader:
    """Responsible for loading the user tools and making them available to the copilot agent."""

    installed_deps = []  # Save tools that have already installed dependencies
    _tools_module = None  # Store the imported tools module
    _instance = None  # Singleton instance
    _instance_lock = threading.Lock()  # Lock for thread-safe singleton
    _configured_tools = None  # Cache for loaded tools

    def __new__(cls, *args, **kwargs):
        with cls._instance_lock:
            if cls._instance is None:
                cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(
        self,
        config_filename: Optional[str] = CONFIGURED_TOOLS_FILENAME,
        tools_deps_filename: Optional[str] = DEPENDENCIES_TOOLS_FILENAME,
    ):
        # Only initialize once
        if hasattr(self, "_initialized"):
            return

        self._tools_config = self._get_tool_config()
        self._tools_dependencies = self._get_tool_dependencies(filepath=tools_deps_filename)

        # Install dependencies for enabled tools BEFORE importing tools
        self._install_enabled_tool_dependencies()
        self._initialized = True

    @property
    def native_tool_config(self) -> Dict:
        return self._tools_config.get(NATIVE_TOOLS_NODE_NAME, {})

    @property
    def third_party_tool_config(self) -> Dict:
        return self._tools_config.get(THIRD_PARTY_TOOLS_NODE_NAME, {})

    def _install_enabled_tool_dependencies(self):
        """Install dependencies for all enabled tools before importing them."""
        # Get enabled third party tools that have dependencies
        enabled_tools_with_deps = []

        for tool_name, enabled in self.third_party_tool_config.items():
            if enabled and tool_name in self._tools_dependencies.keys():
                enabled_tools_with_deps.append(tool_name)

        # Install dependencies for enabled tools
        for tool_name in enabled_tools_with_deps:
            if tool_name not in ToolLoader.installed_deps:
                print_yellow(f"Installing dependencies for {tool_name} tool before loading...")
                tool_installer.install_dependencies(dependencies=self._tools_dependencies[tool_name])
                ToolLoader.installed_deps.append(tool_name)

        if enabled_tools_with_deps:
            copilot_info("Dependencies installed successfully")

    def _import_tools(self):
        """Import tools module after dependencies are installed."""
        if ToolLoader._tools_module is not None:
            return  # Already imported

        try:
            import importlib

            ToolLoader._tools_module = importlib.import_module("tools")
        except ImportError as e:
            print_yellow(f"Warning: Could not import tools module: {e}")
            ToolLoader._tools_module = None

    def _get_tool_config(self) -> Dict:
        """Generate dynamic tool configuration by scanning available tool classes."""
        print_yellow("Generating configuration dynamically from tools directory...")
        return self._generate_dynamic_tool_config()

    def _generate_dynamic_tool_config(self) -> Dict:
        """Generate tool configuration by scanning available concrete tool classes."""
        # Import tools module to discover available tools
        self._import_tools()

        config = {"native_tools": {}, "third_party_tools": {}}

        # Get all concrete ToolWrapper subclasses
        subclasses__: List[Any] = []
        subclasses__.extend(ToolWrapper.__subclasses__())  # Get all ToolWrapper subclasses
        subclasses__.extend(CopilotTool.__subclasses__())  # Include CopilotTool subclasses
        for tool_class in subclasses__:
            tool_name = tool_class.__name__

            # Skip abstract classes and ToolWrapper itself
            if hasattr(tool_class, "__abstractmethods__") and tool_class.__abstractmethods__:
                continue
            if tool_class.__name__ in {"ToolWrapper", "CopilotTool"}:
                continue

            config["third_party_tools"][tool_name] = True
            print_green(f"Auto-discovered tool: {tool_name}")

        print_green(f"Generated dynamic configuration with {len(config['third_party_tools'])} tools")
        return config

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
                    Dependency(
                        name=self.left_side(dependency_name),
                        version=None if version == "*" else version,
                        import_name=self.rigth_side(dependency_name),
                    )
                    for dependency_name, version in value.items()
                ]
            return tools_dependencies

    def rigth_side(self, k):
        return self.split_string(k, "|", False)

    def left_side(self, k):
        return self.split_string(k, "|", True)

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
        """Check if a tool is implemented either in tools module or as ToolWrapper subclass."""
        # Check in ToolWrapper subclasses
        if tool_name in {tool.__name__ for tool in ToolWrapper.__subclasses__()}:
            return True

        # Check in imported tools module
        if ToolLoader._tools_module and hasattr(ToolLoader._tools_module, tool_name):
            attr_value = getattr(ToolLoader._tools_module, tool_name)
            if hasattr(attr_value, "__bases__") and any(
                "ToolWrapper" in str(base) for base in attr_value.__mro__
            ):
                return True

        return False

    def _get_tool_class(self, tool_name: str):
        """Get tool class from either tools module or globals."""
        if ToolLoader._tools_module and hasattr(ToolLoader._tools_module, tool_name):
            return getattr(ToolLoader._tools_module, tool_name)
        return globals().get(tool_name)

    def load_configured_tools(self) -> LangChainTools:
        """Loads all available concrete tool implementations."""
        # Return cached tools if already loaded
        if ToolLoader._configured_tools is not None:
            return ToolLoader._configured_tools

        # Import tools module to ensure all tools are loaded
        self._import_tools()

        configured_tools: LangChainTools = []

        # Load all concrete ToolWrapper subclasses
        print_yellow("Loading all available tools...")
        subclasses__: List[Any] = []
        subclasses__.extend(ToolWrapper.__subclasses__())  # Get all ToolWrapper subclasses
        subclasses__.extend(CopilotTool.__subclasses__())  # Include BaseTool subclasses
        for tool_class in subclasses__:
            try:
                # Skip abstract classes and ToolWrapper itself
                if hasattr(tool_class, "__abstractmethods__") and tool_class.__abstractmethods__:
                    print_yellow(f"Skipping abstract tool class: {tool_class.__name__}")
                    continue

                # Skip ToolWrapper base class
                if tool_class.__name__ in {"ToolWrapper", "CopilotTool"}:
                    continue

                tool_instance = tool_class()
                configured_tools.append(tool_instance)
                print_green(f"Loaded tool: {tool_class.__name__}")
            except Exception as e:
                print_yellow(f"Warning: Could not instantiate tool {tool_class.__name__}: {e}")

        # Cache the loaded tools
        ToolLoader._configured_tools = configured_tools
        print_green(f"Successfully loaded {len(configured_tools)} tools")
        return configured_tools

    def _get_filtered_base_tools(self, enabled_tools: Optional[List[ToolSchema]]) -> LangChainTools:
        """Get base configured tools, filtered by enabled_tools."""
        base_tools = self.load_configured_tools()

        # If no enabled_tools specified, agent has no permission for base tools
        if not enabled_tools:
            return []

        enabled_tool_names = {tool.function.name for tool in enabled_tools}
        return [tool for tool in base_tools if tool.name in enabled_tool_names]

    def _add_kb_tool(self, tools: LangChainTools, agent_configuration: Optional[AssistantSchema]) -> None:
        """Add knowledge base tool if available and requested."""
        if not agent_configuration:
            return

        kb_tool = get_kb_tool(agent_configuration)
        if kb_tool is not None:
            tools.append(kb_tool)

    def _add_openapi_tools(
        self, tools: LangChainTools, agent_configuration: Optional[AssistantSchema]
    ) -> None:
        """Add OpenAPI generated tools if available and requested."""
        if not agent_configuration or not agent_configuration.specs:
            return

        for spec in agent_configuration.specs:
            if spec.type == "FLOW":
                try:
                    api_spec = json.loads(spec.spec)
                    if read_optional_env_var("COPILOT_OLD_OPENAPI_TOOLS", "false").lower() == "true":
                        # Use old OpenAPI tool generation logic
                        openapi_tools = generate_tools_from_openapi_old(api_spec)
                    else:
                        openapi_tools = generate_tools_from_openapi(api_spec)
                    tools.extend(openapi_tools)
                except Exception as e:
                    print_yellow(f"Warning: Could not generate tools from OpenAPI spec: {e}")

    def get_all_tools(
        self,
        agent_configuration: Optional[AssistantSchema] = None,
        enabled_tools: Optional[List[ToolSchema]] = None,
        include_kb_tool: bool = True,
        include_openapi_tools: bool = True,
    ) -> LangChainTools:
        """
        Get all tools including base configured tools plus dynamically generated ones.

        Args:
            agent_configuration: Assistant configuration containing KB and API specs
            enabled_tools: List of specific tools the agent has permission to use.
                          If None or empty, NO base tools will be included (agent has no permissions).
                          Only the tools specified here will be loaded from the base configuration.
            include_kb_tool: Whether to include knowledge base search tool
            include_openapi_tools: Whether to include OpenAPI generated tools

        Returns:
            Complete list of tools including filtered base + dynamic tools
        """
        # Start with base configured tools (filtered if needed)
        # Make a copy to avoid modifying cached/shared lists
        all_tools = self._get_filtered_base_tools(enabled_tools).copy()

        # Add knowledge base tool if requested
        if include_kb_tool:
            self._add_kb_tool(all_tools, agent_configuration)

        # Add OpenAPI generated tools if requested
        if include_openapi_tools:
            self._add_openapi_tools(all_tools, agent_configuration)

        # Set agent_id on all tools if provided from agent_configuration
        if agent_configuration and hasattr(agent_configuration, "assistant_id"):
            agent_id = agent_configuration.assistant_id
            for tool in all_tools:
                if hasattr(tool, "agent_id"):
                    tool.agent_id = agent_id
                    copilot_debug(f"Set agent_id={agent_id} on tool {tool.name}")

        return all_tools

    def get_enabled_tool_functions(
        self,
        enabled_tools: Optional[List[ToolSchema]] = None,
        agent_configuration: Optional[AssistantSchema] = None,
        include_kb_tool: bool = True,
        include_openapi_tools: bool = True,
    ) -> LangChainTools:
        """
        Get only the specific tools that are enabled, plus dynamic tools.
        This is useful for agents that need to filter tools based on configuration.

        Args:
            enabled_tools: List of specific tools to enable
            agent_configuration: Assistant configuration for dynamic tools
            include_kb_tool: Whether to include knowledge base search tool
            include_openapi_tools: Whether to include OpenAPI generated tools

        Returns:
            Filtered list of enabled tools plus dynamic tools
        """
        return self.get_all_tools(
            agent_configuration=agent_configuration,
            enabled_tools=enabled_tools,
            include_kb_tool=include_kb_tool,
            include_openapi_tools=include_openapi_tools,
        )
