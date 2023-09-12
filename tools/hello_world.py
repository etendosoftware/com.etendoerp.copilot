from copilot.core.tool_wrapper import ToolWrapper


class HelloWorldTool(ToolWrapper):
    """A dummy hello world tool implementation

    Attributes:
        name (str): The name of the tool.
        description (str): A brief description of the tool.
        inputs (List[str]): The names of the input arguments for the tool.
        outputs (List[str]): The names of the output values for the tool.
    """

    name = "hello_world_tool"
    description = "This is the classic HelloWorld tool implementation."

    def __call__(self, *args, **kwargs):
        return """
            Create your custom tool by creating a Python class that extends the ToolWrapper class"
            from the copilot.core.tool_wrapper module. Here's an example of how to define a custom tool:"

            from copilot.core.tool_wrapper import ToolWrapper

            class HelloWorldTool(ToolWrapper):
                name = "hello_world_tool"
                description = "This is the classic HelloWorld tool implementation."

                def __call__(self, *args, **kwargs):
                    # Implement your tool's logic here
            """
