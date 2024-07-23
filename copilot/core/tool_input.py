from langchain_core.pydantic_v1 import BaseModel, Field


class ToolInput(BaseModel):
    """ Intermediate class for tool inputs in Tools. This allows for better maintainability and readability. """
    pass


def ToolField(*args, **kwargs):
    Field(*args, **kwargs)
