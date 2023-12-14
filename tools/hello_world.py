from copilot.core.tool_wrapper import ToolWrapper

class HelloWorldTool(ToolWrapper):
    """A dummy hello world tool implementation.

    Attributes:
        name (str): The name of the tool.
        description (str): A brief description of the tool.
    """

    name = "hello_world_tool"
    description = "This is the classic HelloWorld tool implementation."

    def run(self, query: str, *args, **kwargs) -> str:
        return """
            Create your custom tool by creating a Python class that extends the ToolWrapper class"
            from the copilot.core.tool_wrapper module. Here's an example of how to define a custom tool:"

            from copilot.core.tool_wrapper import ToolWrapper

            class HelloWorldTool(ToolWrapper):
                name = "hello_world_tool"
                description = "This is the classic HelloWorld tool implementation."
                
                inputs = ['name', 'another_text']
                outputs = ['message']
                
                def run(self, name: str, another_text: str, *args, **kwargs):
                    result_message = f"Hello {name}, {another_text}"
                    # Implement your tool's logic here
                    return {"message": result_message}
            """
