"""
Test suite for evaluation package - schemas module

Tests to validate schema models and their validation logic before refactoring.
"""

import os
import sys

import pytest

# Add evaluation directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "evaluation"))

from schemas import Conversation, Message


class TestMessage:
    """Test cases for Message schema"""

    def test_message_creation_basic(self):
        """Test basic message creation"""
        message = Message(role="USER", content="Hello, world!")
        assert message.role == "USER"
        assert message.content == "Hello, world!"
        assert message.tool_call_id is None
        assert message.tool_calls is None

    def test_message_creation_with_tool_call_id(self):
        """Test message creation with tool call ID"""
        message = Message(role="Tool", content="Tool response", tool_call_id="call_123")
        assert message.role == "Tool"
        assert message.content == "Tool response"
        assert message.tool_call_id == "call_123"

    def test_message_creation_with_tool_calls(self):
        """Test message creation with tool calls"""
        tool_calls = [{"id": "call_1", "function": {"name": "test_func", "arguments": "{}"}}]
        message = Message(role="AI", content="Using tools", tool_calls=tool_calls)
        assert message.role == "AI"
        assert message.tool_calls == tool_calls

    def test_message_with_list_content(self):
        """Test message with list content (for multimodal messages)"""
        content = [
            {"type": "text", "text": "Hello"},
            {"type": "image", "url": "http://example.com/image.jpg"},
        ]
        message = Message(role="USER", content=content)
        assert message.role == "USER"
        assert message.content == content

    def test_message_validation_empty_role(self):
        """Test message validation with empty role"""
        with pytest.raises(ValueError):
            Message(role="", content="test")

    def test_message_validation_missing_content(self):
        """Test message validation with missing content"""
        with pytest.raises(ValueError):
            Message(role="USER")


class TestConversation:
    """Test cases for Conversation schema"""

    def setup_method(self):
        """Setup test data"""
        self.sample_messages = [
            Message(role="USER", content="What is 2+2?"),
            Message(role="AI", content="2+2 equals 4"),
        ]
        self.expected_response = Message(role="AI", content="The correct answer is 4")

    def test_conversation_creation_basic(self):
        """Test basic conversation creation"""
        conversation = Conversation(messages=self.sample_messages, expected_response=self.expected_response)
        assert len(conversation.messages) == 2
        assert conversation.expected_response.content == "The correct answer is 4"
        assert conversation.run_id is None
        assert conversation.considerations is None
        assert conversation.creation_date is not None

    def test_conversation_creation_with_run_id(self):
        """Test conversation creation with run ID"""
        conversation = Conversation(
            run_id="test_run_123", messages=self.sample_messages, expected_response=self.expected_response
        )
        assert conversation.run_id == "test_run_123"

    def test_conversation_creation_with_considerations(self):
        """Test conversation creation with considerations"""
        considerations = "This test should focus on mathematical accuracy"
        conversation = Conversation(
            messages=self.sample_messages,
            expected_response=self.expected_response,
            considerations=considerations,
        )
        assert conversation.considerations == considerations

    def test_conversation_creation_date_format(self):
        """Test conversation creation date format"""
        conversation = Conversation(messages=self.sample_messages, expected_response=self.expected_response)
        # Check if creation_date follows YYYY-MM-DD-HH:MM:SS format
        assert len(conversation.creation_date) == 19
        assert conversation.creation_date[4] == "-"
        assert conversation.creation_date[7] == "-"
        assert conversation.creation_date[10] == "-"
        assert conversation.creation_date[13] == ":"
        assert conversation.creation_date[16] == ":"

    def test_conversation_validation_empty_messages(self):
        """Test conversation validation with empty messages"""
        with pytest.raises(ValueError):
            Conversation(messages=[], expected_response=self.expected_response)

    def test_conversation_validation_missing_expected_response(self):
        """Test conversation validation with missing expected response"""
        with pytest.raises(ValueError):
            Conversation(messages=self.sample_messages)

    def test_conversation_serialization(self):
        """Test conversation can be serialized/deserialized"""
        conversation = Conversation(
            run_id="test_123",
            messages=self.sample_messages,
            expected_response=self.expected_response,
            considerations="Test serialization",
        )

        # Test to_dict (if available) or json serialization
        data = conversation.model_dump()
        assert data["run_id"] == "test_123"
        assert len(data["messages"]) == 2
        assert data["considerations"] == "Test serialization"

    def test_conversation_with_complex_messages(self):
        """Test conversation with complex messages including tool calls"""
        complex_messages = [
            Message(role="USER", content="Calculate 5*6"),
            Message(
                role="AI",
                content="I'll use the calculator tool",
                tool_calls=[
                    {"id": "call_1", "function": {"name": "calculate", "arguments": '{"operation": "5*6"}'}}
                ],
            ),
            Message(role="Tool", content="30", tool_call_id="call_1"),
            Message(role="AI", content="The result of 5*6 is 30"),
        ]

        conversation = Conversation(
            messages=complex_messages, expected_response=Message(role="AI", content="30")
        )
        assert len(conversation.messages) == 4
        assert conversation.messages[1].tool_calls is not None
        assert conversation.messages[2].tool_call_id == "call_1"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
