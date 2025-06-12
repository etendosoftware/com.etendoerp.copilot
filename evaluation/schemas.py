import time
from typing import List, Optional, Union, Dict, Any

from pydantic import BaseModel


# Define the schema for the conversation
class Message(BaseModel):
    """
    Represents a single message in a conversation.

    Attributes:
        role (str): The role of the message sender (e.g., "AI", "USER", "Tool").
        content (str): The content of the message.
        tool_call_id (Optional[str]): The ID of the tool call, if applicable. Defaults to None.
        tool_calls (Optional[List[dict]]): A list of tool calls, if applicable. Defaults to None.
    """

    role: str  # "AI", "USER", "Tool"
    content: Union[str, List[Dict[str, Any]]]
    tool_call_id: Optional[str] = None  # ID of the tool call, if applicable
    tool_calls: Optional[List[dict]] = None  # List of tool calls, if applicable


class Conversation(BaseModel):
    """
    Represents a conversation consisting of multiple messages and an expected response.

    Attributes:
        run_id (Optional[str]): The ID of the LangSmith run. Defaults to None.
        messages (List[Message]): A list of messages in the conversation.
        expected_response (Message): The expected response for the conversation.
    """

    run_id: Optional[str] = None  # ID of the LangSmith run
    messages: List[Message]
    expected_response: Message
    considerations: Optional[str] = None  # Consideration for the evaluator.
    # Creation date of the conversation YYYY-MM-DD-HH:MM:SS
    creation_date: Optional[str] = time.strftime("%Y-%m-%d-%H:%M:%S")
