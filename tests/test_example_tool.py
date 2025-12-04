"""Tests for ExampleTool to ensure 100% code coverage."""

import pytest
from pydantic import ValidationError

from tools.ExampleTool import ExampleTool, ExampleToolInput, People


class TestPeople:
    """Test cases for People model."""

    def test_people_valid(self):
        """Test People model with valid data."""
        person = People(name="John Doe", age=30)
        assert person.name == "John Doe"
        assert person.age == 30

    def test_people_empty_name(self):
        """Test People model with empty name."""
        person = People(name="", age=25)
        assert person.name == ""
        assert person.age == 25

    def test_people_zero_age(self):
        """Test People model with zero age."""
        person = People(name="Baby", age=0)
        assert person.name == "Baby"
        assert person.age == 0

    def test_people_negative_age(self):
        """Test People model with negative age."""
        person = People(name="TimeTraveler", age=-5)
        assert person.name == "TimeTraveler"
        assert person.age == -5

    def test_people_missing_name(self):
        """Test People model raises error when name is missing."""
        with pytest.raises(ValidationError):
            People(age=30)

    def test_people_missing_age(self):
        """Test People model raises error when age is missing."""
        with pytest.raises(ValidationError):
            People(name="John")

    def test_people_invalid_age_type(self):
        """Test People model raises error with invalid age type."""
        with pytest.raises(ValidationError):
            People(name="John", age="thirty")

    def test_people_invalid_name_type(self):
        """Test People model raises error with invalid name type."""
        with pytest.raises(ValidationError):
            People(name=123, age=30)


class TestExampleToolInput:
    """Test cases for ExampleToolInput schema."""

    def test_example_tool_input_all_params(self):
        """Test ExampleToolInput with all parameters."""
        person = People(name="Alice", age=28)
        input_data = ExampleToolInput(n=42, opt=10, obj=person)

        assert input_data.n == 42
        assert input_data.opt == 10
        assert input_data.obj.name == "Alice"
        assert input_data.obj.age == 28

    def test_example_tool_input_required_only(self):
        """Test ExampleToolInput with only required parameters."""
        person = People(name="Bob", age=35)
        input_data = ExampleToolInput(n=100, obj=person)

        assert input_data.n == 100
        assert input_data.opt is None
        assert input_data.obj.name == "Bob"

    def test_example_tool_input_opt_none(self):
        """Test ExampleToolInput with explicit None for opt."""
        person = People(name="Charlie", age=40)
        input_data = ExampleToolInput(n=5, opt=None, obj=person)

        assert input_data.n == 5
        assert input_data.opt is None
        assert input_data.obj is not None

    def test_example_tool_input_missing_n(self):
        """Test ExampleToolInput raises error when n is missing."""
        person = People(name="Dave", age=22)
        with pytest.raises(ValidationError):
            ExampleToolInput(obj=person)

    def test_example_tool_input_missing_obj(self):
        """Test ExampleToolInput raises error when obj is missing."""
        with pytest.raises(ValidationError):
            ExampleToolInput(n=50)

    def test_example_tool_input_invalid_n_type(self):
        """Test ExampleToolInput raises error with invalid n type."""
        person = People(name="Eve", age=29)
        with pytest.raises(ValidationError):
            ExampleToolInput(n="not_a_number", obj=person)

    def test_example_tool_input_negative_n(self):
        """Test ExampleToolInput with negative n."""
        person = People(name="Frank", age=31)
        input_data = ExampleToolInput(n=-999, obj=person)
        assert input_data.n == -999

    def test_example_tool_input_zero_n(self):
        """Test ExampleToolInput with zero n."""
        person = People(name="Grace", age=27)
        input_data = ExampleToolInput(n=0, obj=person)
        assert input_data.n == 0


class TestExampleTool:
    """Test cases for ExampleTool."""

    def test_tool_attributes(self):
        """Test that tool has correct attributes."""
        tool = ExampleTool()
        assert tool.name == "ExampleTool"
        assert tool.description == "This is the ExampleTool tool description."
        assert tool.args_schema == ExampleToolInput

    def test_run_with_all_params(self):
        """Test _run method with all parameters provided."""
        tool = ExampleTool()
        person = People(name="John Smith", age=45)
        result = tool._run(n=100, opt=50, obj=person)

        assert isinstance(result, dict)
        assert "message" in result
        assert "100" in result["message"]
        assert "50" in result["message"]
        assert "John Smith" in result["message"]
        assert "45" in result["message"]

    def test_run_with_required_only(self):
        """Test _run method with only required parameters."""
        tool = ExampleTool()
        person = People(name="Jane Doe", age=32)
        result = tool._run(n=200, obj=person)

        assert isinstance(result, dict)
        assert "message" in result
        assert "200" in result["message"]
        assert "None" in result["message"]  # opt should be None
        assert "Jane Doe" in result["message"]
        assert "32" in result["message"]

    def test_run_with_opt_none_explicit(self):
        """Test _run method with explicit None for opt."""
        tool = ExampleTool()
        person = People(name="Bob", age=28)
        result = tool._run(n=300, opt=None, obj=person)

        assert isinstance(result, dict)
        assert "message" in result
        assert "300" in result["message"]
        assert "None" in result["message"]

    def test_run_with_zero_values(self):
        """Test _run method with zero values."""
        tool = ExampleTool()
        person = People(name="Zero", age=0)
        result = tool._run(n=0, opt=0, obj=person)

        assert isinstance(result, dict)
        assert "message" in result
        assert "Received number: 0" in result["message"]
        assert "optional number: 0" in result["message"]
        assert "Zero" in result["message"]

    def test_run_with_negative_numbers(self):
        """Test _run method with negative numbers."""
        tool = ExampleTool()
        person = People(name="Negative", age=-5)
        result = tool._run(n=-100, opt=-50, obj=person)

        assert isinstance(result, dict)
        assert "-100" in result["message"]
        assert "-50" in result["message"]
        assert "Negative" in result["message"]

    def test_run_with_obj_none(self):
        """Test _run method with obj=None."""
        tool = ExampleTool()
        result = tool._run(n=500, opt=25, obj=None)

        assert isinstance(result, dict)
        assert "message" in result
        assert "500" in result["message"]
        assert "25" in result["message"]
        assert "None" in result["message"]
        # When obj is None, the extra info about name/age should not be added
        assert "object name:" not in result["message"]

    def test_run_without_opt_with_obj(self):
        """Test _run method without opt but with obj."""
        tool = ExampleTool()
        person = People(name="Alice Wonder", age=33)
        result = tool._run(n=777, obj=person)

        assert isinstance(result, dict)
        assert "777" in result["message"]
        assert "Alice Wonder" in result["message"]
        assert "33" in result["message"]

    def test_run_message_format_with_obj(self):
        """Test message format when obj is provided."""
        tool = ExampleTool()
        person = People(name="Test User", age=99)
        result = tool._run(n=1, opt=2, obj=person)

        message = result["message"]
        assert "Received number:" in message
        assert "optional number:" in message
        assert "object:" in message
        assert "object name:" in message
        assert "object age:" in message

    def test_run_message_format_without_obj(self):
        """Test message format when obj is None."""
        tool = ExampleTool()
        result = tool._run(n=999, opt=888, obj=None)

        message = result["message"]
        assert "Received number: 999" in message
        assert "optional number: 888" in message
        # Should not have the object name/age lines
        assert "object name:" not in message
        assert "object age:" not in message

    def test_run_large_numbers(self):
        """Test _run method with large numbers."""
        tool = ExampleTool()
        person = People(name="BigNum", age=999999)
        result = tool._run(n=1000000, opt=5000000, obj=person)

        assert "1000000" in result["message"]
        assert "5000000" in result["message"]
        assert "999999" in result["message"]

    def test_tool_docstring(self):
        """Test that _run method has proper docstring."""
        tool = ExampleTool()
        assert tool._run.__doc__ is not None
        assert "Example tool implementation" in tool._run.__doc__
        assert "just an example Tool" in tool._run.__doc__

    def test_run_with_special_characters_in_name(self):
        """Test _run with special characters in person name."""
        tool = ExampleTool()
        person = People(name="João O'Brien-Smith", age=42)
        result = tool._run(n=123, opt=456, obj=person)

        assert "João O'Brien-Smith" in result["message"]
        assert "42" in result["message"]

    def test_run_return_structure(self):
        """Test that _run returns a dictionary with 'message' key."""
        tool = ExampleTool()
        person = People(name="Structure Test", age=50)
        result = tool._run(n=10, obj=person)

        assert isinstance(result, dict)
        assert len(result) == 1
        assert "message" in result
        assert isinstance(result["message"], str)
