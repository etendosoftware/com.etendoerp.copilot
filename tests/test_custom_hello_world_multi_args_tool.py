"""Tests for CustomHelloWorldMultiArgsTool to ensure 100% code coverage."""

from unittest.mock import patch

import pytest
from pydantic import ValidationError

from tools.CustomHelloWorldMultiArgsTool import (
    CalculatorInput,
    CustomHelloWorldMultiArgsTool,
)


class TestCalculatorInput:
    """Test cases for CalculatorInput schema."""

    def test_calculator_input_valid(self):
        """Test CalculatorInput with valid integers."""
        input_data = CalculatorInput(a=5, b=10)
        assert input_data.a == 5
        assert input_data.b == 10

    def test_calculator_input_negative_numbers(self):
        """Test CalculatorInput with negative integers."""
        input_data = CalculatorInput(a=-5, b=-10)
        assert input_data.a == -5
        assert input_data.b == -10

    def test_calculator_input_zero(self):
        """Test CalculatorInput with zero values."""
        input_data = CalculatorInput(a=0, b=0)
        assert input_data.a == 0
        assert input_data.b == 0

    def test_calculator_input_missing_a(self):
        """Test CalculatorInput raises error when 'a' is missing."""
        with pytest.raises(ValidationError):
            CalculatorInput(b=10)

    def test_calculator_input_missing_b(self):
        """Test CalculatorInput raises error when 'b' is missing."""
        with pytest.raises(ValidationError):
            CalculatorInput(a=5)

    def test_calculator_input_invalid_type(self):
        """Test CalculatorInput raises error with invalid types."""
        with pytest.raises(ValidationError):
            CalculatorInput(a="not_an_int", b=10)

    def test_calculator_input_float_coercion(self):
        """Test CalculatorInput can coerce compatible types."""
        # Pydantic may coerce floats to ints if they're whole numbers
        # This tests the behavior
        try:
            input_data = CalculatorInput(a=5.0, b=10.0)
            assert input_data.a == 5
            assert input_data.b == 10
        except ValidationError:
            # If strict mode prevents this, that's also valid
            pass


class TestCustomHelloWorldMultiArgsTool:
    """Test cases for CustomHelloWorldMultiArgsTool."""

    def test_tool_attributes(self):
        """Test that tool has correct attributes."""
        tool = CustomHelloWorldMultiArgsTool()
        assert tool.name == "CustomHelloWorldMultiArgsTool"
        assert tool.description == "This is the CustomHelloWorldMultiArgsTool tool implementation."
        assert tool.args_schema == CalculatorInput
        assert tool.return_direct is False

    @patch("pyfiglet.figlet_format")
    def test_run_with_input_params(self, mock_figlet_format):
        """Test run method with input_params."""
        mock_figlet_format.return_value = "\n HELLO WORLD! \n"

        tool = CustomHelloWorldMultiArgsTool()
        input_params = {"a": 5, "b": 10}
        result = tool.run(input_params=input_params)

        assert isinstance(result, dict)
        assert "message" in result
        assert "Input params" in result["message"]
        assert "{'a': 5, 'b': 10}" in result["message"]
        assert "HELLO WORLD!" in result["message"]
        mock_figlet_format.assert_called_once_with("Hello World!")

    @patch("pyfiglet.figlet_format")
    def test_run_with_none_input_params(self, mock_figlet_format):
        """Test run method with None input_params."""
        mock_figlet_format.return_value = "\n HELLO WORLD! \n"

        tool = CustomHelloWorldMultiArgsTool()
        result = tool.run(input_params=None)

        assert isinstance(result, dict)
        assert "message" in result
        assert "Input params None" in result["message"]
        assert "HELLO WORLD!" in result["message"]

    @patch("pyfiglet.figlet_format")
    def test_run_with_empty_dict(self, mock_figlet_format):
        """Test run method with empty dictionary."""
        mock_figlet_format.return_value = "\n HELLO WORLD! \n"

        tool = CustomHelloWorldMultiArgsTool()
        result = tool.run(input_params={})

        assert isinstance(result, dict)
        assert "message" in result
        assert "Input params {}" in result["message"]

    @patch("pyfiglet.figlet_format")
    def test_run_with_args(self, mock_figlet_format):
        """Test run method with positional args."""
        mock_figlet_format.return_value = "\n HELLO WORLD! \n"

        tool = CustomHelloWorldMultiArgsTool()
        input_params = {"a": 1, "b": 2}
        result = tool.run(input_params, "arg1", "arg2", "arg3")

        assert isinstance(result, dict)
        assert "message" in result
        assert "args=('arg1', 'arg2', 'arg3')" in result["message"]

    @patch("pyfiglet.figlet_format")
    def test_run_with_kwargs(self, mock_figlet_format):
        """Test run method with keyword arguments."""
        mock_figlet_format.return_value = "\n HELLO WORLD! \n"

        tool = CustomHelloWorldMultiArgsTool()
        input_params = {"a": 3, "b": 7}
        result = tool.run(input_params, key1="value1", key2="value2")

        assert isinstance(result, dict)
        assert "message" in result
        assert "kwargs=" in result["message"]
        assert "key1" in result["message"] or "'key1'" in result["message"]
        assert "key2" in result["message"] or "'key2'" in result["message"]

    @patch("pyfiglet.figlet_format")
    def test_run_with_args_and_kwargs(self, mock_figlet_format):
        """Test run method with both args and kwargs."""
        mock_figlet_format.return_value = "\n HELLO WORLD! \n"

        tool = CustomHelloWorldMultiArgsTool()
        input_params = {"a": 99, "b": 1}
        result = tool.run(input_params, "positional1", "positional2", keyword1="kwvalue1", keyword2=42)

        assert isinstance(result, dict)
        assert "message" in result
        assert "Input params" in result["message"]
        assert "args=" in result["message"]
        assert "kwargs=" in result["message"]
        assert "positional1" in result["message"]
        assert "HELLO WORLD!" in result["message"]

    @patch("pyfiglet.figlet_format")
    def test_run_pyfiglet_integration(self, mock_figlet_format):
        """Test that pyfiglet.figlet_format is called correctly."""
        mock_figlet_format.return_value = "ASCII ART"

        tool = CustomHelloWorldMultiArgsTool()
        result = tool.run(input_params={"a": 1, "b": 2})

        mock_figlet_format.assert_called_once_with("Hello World!")
        assert isinstance(result, dict)
        assert "message" in result
        assert "ASCII ART" in result["message"]

    @patch("pyfiglet.figlet_format")
    def test_run_with_complex_input_params(self, mock_figlet_format):
        """Test run method with complex input_params dictionary."""
        mock_figlet_format.return_value = "\n HELLO! \n"

        tool = CustomHelloWorldMultiArgsTool()
        input_params = {"a": 123, "b": 456, "extra_key": "extra_value", "nested": {"inner": "data"}}
        result = tool.run(input_params=input_params)

        assert isinstance(result, dict)
        assert "message" in result
        assert "123" in result["message"]
        assert "456" in result["message"]

    @patch("pyfiglet.figlet_format")
    def test_run_message_format(self, mock_figlet_format):
        """Test that the returned message has the expected format."""
        mock_figlet_format.return_value = "FIGLET_OUTPUT"

        tool = CustomHelloWorldMultiArgsTool()
        input_params = {"a": 10, "b": 20}
        result = tool.run(input_params, extra_arg="test")

        # Message should contain input_params, args, and kwargs info
        assert isinstance(result, dict)
        assert "message" in result
        assert "Input params" in result["message"]
        assert "args=" in result["message"]
        assert "kwargs=" in result["message"]
        assert "FIGLET_OUTPUT" in result["message"]

    def test_tool_return_type(self):
        """Test that run method returns ToolOutputMessage."""
        with patch("pyfiglet.figlet_format") as mock_figlet_format:
            mock_figlet_format.return_value = "TEST"
            tool = CustomHelloWorldMultiArgsTool()
            result = tool.run(input_params={"a": 1, "b": 1})
            assert isinstance(result, dict)
            assert "message" in result
