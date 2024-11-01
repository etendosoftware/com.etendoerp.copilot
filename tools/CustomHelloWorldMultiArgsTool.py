from typing import Dict, Type

from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolOutputMessage, ToolWrapper


class CalculatorInput(ToolInput):
    a: int = ToolField(description="first number")
    b: int = ToolField(description="second number")


class CustomHelloWorldMultiArgsTool(ToolWrapper):
    name: str = "CustomHelloWorldMultiArgsTool"
    description: str = "This is the CustomHelloWorldMultiArgsTool tool implementation."

    args_schema: Type[ToolInput] = CalculatorInput
    return_direct: bool = False

    def run(self, input_params: Dict = None, *args, **kwargs) -> ToolOutputMessage:
        result_message = f"Input params {input_params}, args={args}, kwargs={kwargs}"
        import pyfiglet

        result_message += pyfiglet.figlet_format("Hello World!")
        # Implement your tool's logic here
        return ToolOutputMessage(message=result_message)
