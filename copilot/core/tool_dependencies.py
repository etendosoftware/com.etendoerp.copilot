import re
from dataclasses import dataclass
from typing import ClassVar, Dict, List, Optional, TypeAlias

from langsmith import traceable

import pkg_resources


@dataclass
class Dependency:
    """Represents a tool dependency."""

    LATEST_VERSION: ClassVar[str] = "latest"

    name: str

    # when version is not provided, it represents latest
    version: Optional[str] = None

    # when import_name is not provided, it represents the name for import
    import_name: Optional[str] = None

    @traceable
    def fullname(self) -> str:
        """Returns dependency fullname which will be installed via pip."""
        return f"{self.name}{self.version or ''}"

    @traceable
    def required_version(self) -> str:
        if not self.version:
            return self._get_latest_version()

        pattern = re.compile(r"\d+")
        matches = pattern.findall(self.version)
        return ".".join(matches)

    @traceable
    def _get_latest_version(self) -> str:
        """Returns latest version available on PyPI."""
        distribution = pkg_resources.get_distribution(self.name)
        latest_version = distribution.version
        return latest_version

    @traceable
    def get_import_name(self) -> str:
        """Returns dependency import name. If not provided, it returns the name."""
        return self.import_name or self.name


Dependencies: TypeAlias = List[Dependency]

ToolsDependencies: TypeAlias = Dict[str, Dependencies]
