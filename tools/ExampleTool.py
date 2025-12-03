from typing import Dict, Optional, Type

from copilot.core.tool_wrapper import CopilotTool
from pydantic import BaseModel, Field


class People(BaseModel):
    name: str = Field(description="Name of the person")
    age: int = Field(description="Age of the person")


class ExampleToolInput(BaseModel):
    n: int = Field(description="a number")
    opt: Optional[int] = Field(
        default=None, description="an optional number"
    )  # Optional field with default value None
    obj: People = Field(description="a model object")  # Model object with default factory


class ExampleTool(CopilotTool):
    name: str = "ExampleTool"
    description: str = "This is the ExampleTool tool description."

    args_schema: Type[BaseModel] = ExampleToolInput

    def _run(self, n: int, opt: Optional[int] = None, obj: Optional[People] = None) -> Dict[str, str]:
        """
        Example tool implementation that takes a number, an optional number, and a model object.
        It's just an example Tool.
        """
        result_message = f"Received number: {n},\n optional number: {opt},\n object: {obj}\n"
        if obj:
            result_message += f"\n, object name: {obj.name},\n object age: {obj.age}"

        return {"message": result_message}
