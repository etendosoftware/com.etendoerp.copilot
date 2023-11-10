from dataclasses import dataclass
from typing import Dict, List, Optional, TypeAlias


@dataclass
class Dependency:
    """Represents a tool dependency
    """
    name: str

    # when version is not provided, it represents latest
    version: Optional[str] = None

    def fullname(self) -> str:
        """Returns dependency fullname which will be installed via pip
        """
        return f"{self.name}{self.version or ''}"


Dependencies: TypeAlias = List[Dependency]

ToolsDependencies: TypeAlias = Dict[str, Dependencies]
