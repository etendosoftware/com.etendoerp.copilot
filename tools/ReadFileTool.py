import os

from copilot.core.tool_wrapper import ToolWrapper


class ReadFileTool(ToolWrapper):
    name = 'ReadFileTool'
    description = ('This is a tool for reading files. Receives "filepath" string parameter. The "filepath" parameter is the path of the file to read. The tool will return the content of the file. '
                   'Example of input: { "filepath": "/tmp/test.txt" }')
    inputs = ['filepath']
    outputs = ['message']

    def run(self, input, *args, **kwargs):
        import json

        # if json is a string, convert it to json, else, use the json
        if isinstance(input, str):
            try:
                json = json.loads(input)
            except:
                json = {"filepath": input}
        else:
            json = input
        p_filepath = json.get('filepath')
        # read the file
        file_content = open(p_filepath).read()

        return {"message": file_content }

