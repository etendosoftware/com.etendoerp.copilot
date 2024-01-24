from copilot.core.tool_wrapper import ToolWrapper
from pydantic import BaseModel, Field
from typing import Type


class DummyInput(BaseModel):
    query: str = Field(description="query to look up")


class HelloWorldTool(ToolWrapper):
    """A dummy hello world tool implementation.

    Attributes:
        name (str): The name of the tool.
        description (str): A brief description of the tool.
    """

    name = "HelloWorldTool"
    description = "This is the classic HelloWorld tool implementation."
    args_schema: Type[BaseModel] = DummyInput
    return_direct: bool = True

    def run(self, query: str, *args, **kwargs) -> str:
        result_message = """
            Create your custom tool by creating a Python class that extends the ToolWrapper class"
            from the copilot.core.tool_wrapper module. Here's an example of how to define a custom tool:"

            from copilot.core.tool_wrapper import ToolWrapper

            class HelloWorldTool(ToolWrapper):
                name = "hello_world_tool"
                description = "This is the classic HelloWorld tool implementation."

                def run(self, name: str, *args, **kwargs):
                    result_message = f"Hello {name}"
                    # Implement your tool's logic here
                    return {"message": result_message}
        """
        return {"message": result_message}
