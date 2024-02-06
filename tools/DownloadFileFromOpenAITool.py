import base64
import os
import requests

from pathlib import Path
from pydantic import BaseModel, Field
from typing import Dict, Final, Type

from copilot.core.exceptions import OpenAIApiKeyNotFound
from copilot.core.tool_wrapper import ToolWrapper

OPENAI_API_KEY: Final[str] = os.getenv("OPENAI_API_KEY")


class DownloadFileFromOpenAIToolInput(BaseModel):
    file_openai_id: str = Field(description="OpenAI Id of the file to be downloaded")


class DownloadFileFromOpenAITool(ToolWrapper):
    ''' This tool receives a File ID of OpenAI and downloads the file. The Tool return the local path of the downloaded file'''
    name = "DownloadFileFromOpenAITool"
    description = "This tool receives a File ID of OpenAI and downloads the file. The Tool return the local path of the downloaded file in the local file system."
    args_schema: Type[BaseModel] = DownloadFileFromOpenAIToolInput

    def run(self, input_params, *args, **kwargs):
        if not OPENAI_API_KEY:
            raise OpenAIApiKeyNotFound()

        openai_file_id = input_params.get('file_openai_id')

        # use the openai library to download the file
        # imprimir un pwd de donde estas, luego hacer pwd+/copilotTempFiles/openai_file_id/ y ver que archivo hay ahi, deberia ser uno.
        # checkear si existe, y devolver el path absoluto de ese archivo
        path = os.getcwd() + "/copilotTempFiles/" + openai_file_id + "/"
        # check file inside the folder
        file = [f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f))]
        response = {'message': "File downloaded successfully", 'file_path': path + file[0]}

        return response
