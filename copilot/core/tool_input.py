from langchain.pydantic_v1 import BaseModel, Field


class ToolInput(BaseModel):
    """ Intermediate class for tool inputs in Tools. This allows for better maintainability and readability. """
    pass


def ToolField(*args, **kwargs):
    """ Helper function to create a field in a ToolInput class. """
    return Field(*args, **kwargs)   
