from copilot.core.tool_wrapper import ToolWrapper
from pydantic import BaseModel, Field
from typing import Type


class DummyInput(BaseModel):
    query: str = Field(description="query to look up")


class OcrAssistentTool(ToolWrapper):
    """https://platform.openai.com/docs/assistants/tools/function-calling
    https://platform.openai.com/docs/assistants/tools/function-calling
    """
    name = "custom_ocr_tool"
    description = "This is a custom OCR tool implementation that returns an appropriate JSON object for the data in the image"

    args_schema: Type[BaseModel] = DummyInput
    return_direct: bool = True

    def run(self, query: str, *args, **kwargs) -> str:
        ocr_image_url = kwargs['args'].ocr_image_url
        from openai import OpenAI
        client = OpenAI()
        response = client.chat.completions.create(
            model="gpt-4-vision-preview",
            messages=[{
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": "Give me appropriate JSON object for the data in the image"
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": ocr_image_url
                        }
                    }
                ]
            }],
            max_tokens=300
        )
        return response.choices[0].message.content