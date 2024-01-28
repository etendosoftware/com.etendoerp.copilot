from copilot.core.tool_wrapper import ToolWrapper
from typing import Type, Dict

from pydantic import BaseModel, Field


class CalculatorInput(BaseModel):
    a: int = Field(description="first number")
    b: int = Field(description="second number")


class CustomHelloWorldMultiArgsTool(ToolWrapper):
    name = "CustomHelloWorldMultiArgsTool"
    description = "This is the CustomHelloWorldMultiArgsTool tool implementation."

    args_schema: Type[BaseModel] = CalculatorInput
    return_direct: bool = True

    def run(self, input_params: Dict, *args, **kwargs):
        result_message = f"Input params {input_params}, args={args}, kwargs={kwargs}"
        #Implement your tool's logic here
        return {"message": result_message}
