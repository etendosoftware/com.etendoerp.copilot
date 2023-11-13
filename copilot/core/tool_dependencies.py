import re
import pkg_resources
from dataclasses import dataclass
from typing import ClassVar, Dict, List, Optional, TypeAlias


@dataclass
class Dependency:
    """Represents a tool dependency."""
    LATEST_VERSION: ClassVar[str] = 'latest'

    name: str

    # when version is not provided, it represents latest
    version: Optional[str] = None

    def fullname(self) -> str:
        """Returns dependency fullname which will be installed via pip."""
        return f"{self.name}{self.version or ''}"

    def required_version(self) -> str:
        if not self.version:
            return self._get_latest_version()

        pattern = re.compile(r'\d+')
        matches = pattern.findall(self.version)
        return ".".join(matches)

    def _get_latest_version(self) -> str:
        """Returns latest version available on PyPI."""
        distribution = pkg_resources.get_distribution(self.name)
        latest_version = distribution.version
        return latest_version


Dependencies: TypeAlias = List[Dependency]

ToolsDependencies: TypeAlias = Dict[str, Dependencies]
