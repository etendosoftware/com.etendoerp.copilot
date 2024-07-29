from copilot.core.tool_wrapper import ToolWrapper, ToolOutputMessage
from typing import Type, Dict

from copilot.core.tool_input import ToolInput, ToolField
from copilot.core.tool_wrapper import ToolWrapper


class CalculatorInput(ToolInput):
    a: int = ToolField(description="first number")
    b: int = ToolField(description="second number")


class CustomHelloWorldMultiArgsTool(ToolWrapper):
    name = "CustomHelloWorldMultiArgsTool"
    description = "This is the CustomHelloWorldMultiArgsTool tool implementation."

    args_schema: Type[ToolInput] = CalculatorInput
    return_direct: bool = False

    def run(self, input_params: Dict = None, *args, **kwargs) -> ToolOutputMessage:
        result_message = f"Input params {input_params}, args={args}, kwargs={kwargs}"
        #Implement your tool's logic here
        return ToolOutputMessage(message=result_message)
