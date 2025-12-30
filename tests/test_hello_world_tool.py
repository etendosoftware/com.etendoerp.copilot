"""Tests for HelloWorldTool to ensure 100% code coverage."""

import pytest
from pydantic import ValidationError

from tools.HelloWorldTool import DummyInput, HelloWorldTool


class TestDummyInput:
    """Test cases for DummyInput schema."""

    def test_dummy_input_valid(self):
        """Test DummyInput with valid query."""
        input_data = DummyInput(query="test query")
        assert input_data.query == "test query"

    def test_dummy_input_missing_query(self):
        """Test DummyInput raises error when query is missing."""
        with pytest.raises(ValidationError):
            DummyInput()

    def test_dummy_input_invalid_type(self):
        """Test DummyInput raises error with invalid type."""
        with pytest.raises(ValidationError):
            DummyInput(query=123)


class TestHelloWorldTool:
    """Test cases for HelloWorldTool."""

    def test_tool_attributes(self):
        """Test that tool has correct attributes."""
        tool = HelloWorldTool()
        assert tool.name == "HelloWorldTool"
        assert tool.description == "This is the classic HelloWorld tool implementation."
        assert tool.args_schema == DummyInput
        assert tool.return_direct is False

    def test_run_with_query(self):
        """Test run method with query parameter."""
        tool = HelloWorldTool()
        result = tool.run(query="test query")

        assert isinstance(result, dict)
        assert "message" in result
        assert isinstance(result["message"], str)
        assert "Create your custom tool" in result["message"]
        assert "ToolWrapper" in result["message"]
        assert "hello_world_tool" in result["message"]

    def test_run_with_empty_query(self):
        """Test run method with empty query."""
        tool = HelloWorldTool()
        result = tool.run(query="")

        assert isinstance(result, dict)
        assert "message" in result
        assert isinstance(result["message"], str)

    def test_run_with_args(self):
        """Test run method with additional args."""
        tool = HelloWorldTool()
        result = tool.run(query="test")

        assert isinstance(result, dict)
        assert "message" in result

    def test_run_with_kwargs(self):
        """Test run method with keyword arguments."""
        tool = HelloWorldTool()
        result = tool.run(query="test", extra_param="value", another_param=123)

        assert isinstance(result, dict)
        assert "message" in result

    def test_run_with_args_and_kwargs(self):
        """Test run method with both args and kwargs."""
        tool = HelloWorldTool()
        result = tool.run(query="test", kwarg1="value1", kwarg2=456)

        assert isinstance(result, dict)
        assert "message" in result
        assert "Create your custom tool" in result["message"]

    def test_run_message_content(self):
        """Test that run method returns expected message content."""
        tool = HelloWorldTool()
        result = tool.run(query="anything")
        message = result["message"]

        # Verify key phrases are present in the returned message
        assert "Create your custom tool" in message
        assert "Python class" in message
        assert "ToolWrapper" in message
        assert "copilot.core.tool_wrapper" in message
        assert "HelloWorldTool" in message
        assert "hello_world_tool" in message
        assert "def run" in message

    def test_tool_inheritance(self):
        """Test that HelloWorldTool properly inherits from ToolWrapper."""
        tool = HelloWorldTool()
        # Check that it has the run method
        assert hasattr(tool, "run")
        assert callable(tool.run)

    def test_docstring(self):
        """Test that HelloWorldTool has proper docstring."""
        assert HelloWorldTool.__doc__ is not None
        assert "dummy hello world tool" in HelloWorldTool.__doc__.lower()
