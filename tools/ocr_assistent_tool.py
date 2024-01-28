import base64
import filetype
import os
import requests

from pathlib import Path
from pdf2image import convert_from_path
from pydantic import BaseModel, Field
from typing import Dict, Final, Type

from copilot.core.exceptions import OpenAIApiKeyNotFound
from copilot.core.tool_wrapper import ToolWrapper

OPENAI_API_KEY: Final[str] = os.getenv("OPENAI_API_KEY")

GET_JSON_PROMPT: Final[str] = "Give me an appropriate JSON object for the data in the image, I want a dictionary instead of a raw string"

class DummyInput(BaseModel):
    query: str = Field(description="query to look up")


class OcrAssistentTool(ToolWrapper):
    """OCR (Optical Character Recognition) implementation using Vision
    Given an image it will extract the text andd return as JSON
    """
    name = "custom_ocr_tool"
    description = "This is a custom OCR tool implementation that returns an appropriate JSON object for the data in the image"
    args_schema: Type[BaseModel] = DummyInput
    return_direct: bool = True

    @staticmethod
    def image_to_base64(image_path):
        with open(image_path, "rb") as image_file:
            image_binary_data = image_file.read()
            base64_encoded = base64.b64encode(image_binary_data).decode('utf-8')
            return base64_encoded

    @staticmethod
    def is_pdf(path_to_file):
        return filetype.guess(path_to_file).mime == 'application/pdf'

    @classmethod
    def get_openai_headers(cls) -> Dict:
        return {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {OPENAI_API_KEY}"
        }

    def run(self, query: str, *args, **kwargs) -> str:
        if not OPENAI_API_KEY:
            raise OpenAIApiKeyNotFound()

        ocr_image_url = kwargs['args'].ocr_image_url

        if not Path(ocr_image_url).is_file():
            raise Exception(f"Filename {ocr_image_url} doesn't exist")

        if self.is_pdf(ocr_image_url):
            images = convert_from_path(ocr_image_url)
            base64_images = []
            for idx, image in enumerate(images):
                temp_image_path = f"temp_image_{idx}.png"
                image.save(temp_image_path)
                # Convert the image to base64
                base64_images.append(self.image_to_base64(temp_image_path))

            # assuming pdf of 1 page
            base64_image = base64_images[0]

        else:
            base64_image = self.image_to_base64(temp_image_path)

        headers = self.get_openai_headers()
        payload = {
            "model": "gpt-4-vision-preview",
            "messages": [
                {
                  "role": "user",
                    "content": [
                        {
                            "type": "text",
                            "text": GET_JSON_PROMPT
                        },
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:image/jpeg;base64,{base64_image}"
                            }
                        }
                    ]
                }
            ],
            "max_tokens": 300
        }

        response = requests.post(
            "https://api.openai.com/v1/chat/completions", headers=headers, json=payload
        )
        response_json = response.json()
        response_content = response_json['choices'][0]['message']['content']
        response_content = response_content.replace('\n', '')
        response_content = response_content.split('```json')
        content = response_content[1] + '"}}}'
        return content
