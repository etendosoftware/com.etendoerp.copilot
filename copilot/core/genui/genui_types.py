"""
Types and data models for the Genui interface.

This module defines Pydantic models for representing structured data in a tool-calling
and messaging system. Used for serializing and deserializing GenUI API payloads.
"""
from typing import List, Any, Dict

from pydantic import BaseModel, Field


class GenUIToolCallFunction(BaseModel):
    """
    Represents a function called by a tool.

    Attributes:
        name: The name of the function being called.
        arguments: JSON string containing the arguments passed to the function.
    """
    name: str
    arguments: str


class GenUIToolCall(BaseModel):
    """
    Represents a call to a tool.

    Attributes:
        id: Unique identifier for the tool call.
        type: Type of the tool being called, typically "function".
        function_: The function being called, containing its name and arguments.
    """
    id: str
    type: str
    function_: GenUIToolCallFunction = Field(..., alias="function")


class GenUIMessage(BaseModel):
    """
    Represents a message in a conversation.

    Attributes:
        role: Role of the message sender (e.g., "user", "assistant").
        content: Text content of the message.
        tool_call_id: Optional identifier linking this message to a tool call.
        tool_calls: List of tool calls associated with this message.
    """
    role: str
    content: str
    tool_call_id: str = ''
    tool_calls: List[GenUIToolCall] = []


class GenUIToolFunctionParameters(BaseModel):
    """
    Defines the parameters schema for a function in a tool.

    Attributes:
        type: The type of the parameter schema, typically "object".
        properties: Dictionary mapping parameter names to their specifications.
        required: List of names of required parameters.
        additionalProperties: Whether additional properties not in schema are allowed.
        schema_: URI identifier for the JSON Schema standard.
    """
    type: str
    properties: Dict[str, Any]
    required: List[str] = []
    additionalProperties: bool = False


class GenUIToolFunction(BaseModel):
    """
    Represents a function that can be called as a tool.

    Attributes:
        name: Name of the function.
        description: Human-readable description of what the function does.
        parameters: Schema defining the parameters this function accepts.
    """
    name: str
    description: str
    parameters: GenUIToolFunctionParameters


class GenUITool(BaseModel):
    """
    Represents a tool that can be used by the system.

    Attributes:
        type: Type of the tool, typically "function".
        function: The function specification for this tool.
    """
    type: str
    function: GenUIToolFunction


class GenuiPayload(BaseModel):
    """
    Represents the payload for a GenUI API request.

    Attributes:
        model: The model identifier to use for processing.
        messages: List of messages in the conversation.
        tools: List of tools available for the model to use.
        tool_choice: Strategy for choosing tools, defaults to "auto".
        stream: Whether to stream the response or return it all at once.
    """
    model: str
    messages: List[GenUIMessage]
    tools: List[GenUITool]
    tool_choice: str = "auto"
    stream: bool = False