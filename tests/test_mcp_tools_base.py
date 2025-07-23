"""
Tests for MCP base tools module.

This module tests the base tool classes and utilities for MCP tools.
"""


import pytest
from copilot.core.mcp.tools.base import BaseTool, ToolResult


class TestToolResult:
    """Test cases for ToolResult class."""

    def test_tool_result_success_initialization(self):
        """Test ToolResult initialization with success."""
        result = ToolResult(success=True, data="test_data")

        assert result.success is True
        assert result.data == "test_data"
        assert result.error is None
        assert result.metadata is None

    def test_tool_result_failure_initialization(self):
        """Test ToolResult initialization with failure."""
        result = ToolResult(success=False, error="test_error")

        assert result.success is False
        assert result.data is None
        assert result.error == "test_error"
        assert result.metadata is None

    def test_tool_result_with_metadata(self):
        """Test ToolResult initialization with metadata."""
        metadata = {"execution_time": 1.5, "source": "test"}
        result = ToolResult(success=True, data="test", metadata=metadata)

        assert result.success is True
        assert result.data == "test"
        assert result.error is None
        assert result.metadata == metadata

    def test_tool_result_complete_initialization(self):
        """Test ToolResult initialization with all fields."""
        metadata = {"timestamp": "2024-01-01"}
        result = ToolResult(success=True, data={"key": "value"}, error="warning", metadata=metadata)

        assert result.success is True
        assert result.data == {"key": "value"}
        assert result.error == "warning"
        assert result.metadata == metadata

    def test_tool_result_default_values(self):
        """Test ToolResult with only required field."""
        result = ToolResult(success=False)

        assert result.success is False
        assert result.data is None
        assert result.error is None
        assert result.metadata is None

    def test_tool_result_pydantic_validation(self):
        """Test that ToolResult validates fields correctly."""
        # Valid cases
        result1 = ToolResult(success=True)
        ToolResult(success=False)

        # Test that it's a Pydantic model
        assert hasattr(result1, "model_dump")
        assert hasattr(result1, "model_dump_json")

    def test_tool_result_serialization(self):
        """Test ToolResult serialization capabilities."""
        metadata = {"test": "value"}
        result = ToolResult(success=True, data="test_data", error="test_error", metadata=metadata)

        # Test dict conversion
        result_dict = result.model_dump()
        expected_dict = {"success": True, "data": "test_data", "error": "test_error", "metadata": metadata}
        assert result_dict == expected_dict

    def test_tool_result_json_serialization(self):
        """Test ToolResult JSON serialization."""
        result = ToolResult(success=True, data="test")

        # Should be able to serialize to JSON
        json_str = result.model_dump_json()
        assert '"success":true' in json_str.replace(" ", "")
        assert '"data":"test"' in json_str.replace(" ", "")


class ConcreteBaseTool(BaseTool):
    """Concrete implementation of BaseTool for testing."""

    async def execute(self, **kwargs) -> ToolResult:
        """Test implementation of execute method."""
        if kwargs.get("should_fail"):
            return ToolResult(success=False, error="Test error")
        return ToolResult(success=True, data=kwargs)

    def get_schema(self) -> dict:
        """Test implementation of get_schema method."""
        return {
            "type": "object",
            "properties": {"should_fail": {"type": "boolean", "description": "Whether to simulate failure"}},
        }


class TestBaseTool:
    """Test cases for BaseTool abstract base class."""

    def test_base_tool_initialization(self):
        """Test BaseTool initialization."""
        tool = ConcreteBaseTool("test_tool", "Test tool description")

        assert tool.name == "test_tool"
        assert tool.description == "Test tool description"

    def test_base_tool_abstract_class(self):
        """Test that BaseTool cannot be instantiated directly."""
        with pytest.raises(TypeError):
            BaseTool("test", "test")

    @pytest.mark.asyncio
    async def test_concrete_tool_execute_success(self):
        """Test successful execution of concrete tool."""
        tool = ConcreteBaseTool("test_tool", "Test tool")

        result = await tool.execute(param1="value1", param2="value2")

        assert isinstance(result, ToolResult)
        assert result.success is True
        assert result.data == {"param1": "value1", "param2": "value2"}
        assert result.error is None

    @pytest.mark.asyncio
    async def test_concrete_tool_execute_failure(self):
        """Test failed execution of concrete tool."""
        tool = ConcreteBaseTool("test_tool", "Test tool")

        result = await tool.execute(should_fail=True)

        assert isinstance(result, ToolResult)
        assert result.success is False
        assert result.error == "Test error"
        assert result.data is None

    @pytest.mark.asyncio
    async def test_concrete_tool_execute_no_params(self):
        """Test tool execution with no parameters."""
        tool = ConcreteBaseTool("test_tool", "Test tool")

        result = await tool.execute()

        assert isinstance(result, ToolResult)
        assert result.success is True
        assert result.data == {}

    def test_base_tool_name_property(self):
        """Test that tool name is properly stored and accessible."""
        tool = ConcreteBaseTool("my_special_tool", "Description")

        assert tool.name == "my_special_tool"

    def test_base_tool_description_property(self):
        """Test that tool description is properly stored and accessible."""
        description = "This is a detailed tool description"
        tool = ConcreteBaseTool("tool", description)

        assert tool.description == description

    def test_base_tool_different_names(self):
        """Test creating tools with different names."""
        tool1 = ConcreteBaseTool("tool1", "First tool")
        tool2 = ConcreteBaseTool("tool2", "Second tool")

        assert tool1.name != tool2.name
        assert tool1.description != tool2.description

    def test_base_tool_empty_name(self):
        """Test tool with empty name."""
        tool = ConcreteBaseTool("", "Empty name tool")

        assert tool.name == ""
        assert tool.description == "Empty name tool"

    def test_base_tool_empty_description(self):
        """Test tool with empty description."""
        tool = ConcreteBaseTool("tool", "")

        assert tool.name == "tool"
        assert tool.description == ""


class AsyncFailingTool(BaseTool):
    """Tool that raises exceptions for testing error handling."""

    async def execute(self, **kwargs) -> ToolResult:
        """Execute method that raises exceptions."""
        if kwargs.get("raise_exception"):
            raise ValueError("Test exception")
        return ToolResult(success=True, data="success")

    def get_schema(self) -> dict:
        """Test implementation of get_schema method."""
        return {
            "type": "object",
            "properties": {
                "raise_exception": {"type": "boolean", "description": "Whether to raise exception"}
            },
        }


class TestBaseToolErrorHandling:
    """Test error handling in BaseTool implementations."""

    @pytest.mark.asyncio
    async def test_tool_exception_propagation(self):
        """Test that exceptions in execute method are propagated."""
        tool = AsyncFailingTool("failing_tool", "Tool that fails")

        with pytest.raises(ValueError, match="Test exception"):
            await tool.execute(raise_exception=True)

    @pytest.mark.asyncio
    async def test_tool_normal_execution_after_exception(self):
        """Test that tool can execute normally after an exception."""
        tool = AsyncFailingTool("failing_tool", "Tool that fails")

        # First call raises exception
        with pytest.raises(ValueError):
            await tool.execute(raise_exception=True)

        # Second call should work normally
        result = await tool.execute(raise_exception=False)
        assert result.success is True
        assert result.data == "success"


class MockBaseTool(BaseTool):
    """Mock BaseTool for testing inheritance patterns."""

    def __init__(self, name: str, description: str, mock_result: ToolResult = None):
        super().__init__(name, description)
        self.mock_result = mock_result or ToolResult(success=True, data="mock_data")
        self.call_count = 0
        self.last_kwargs = None

    async def execute(self, **kwargs) -> ToolResult:
        """Mock execute method that tracks calls."""
        self.call_count += 1
        self.last_kwargs = kwargs
        return self.mock_result

    def get_schema(self) -> dict:
        """Test implementation of get_schema method."""
        return {"type": "object", "properties": {}}


class TestBaseToolInheritance:
    """Test inheritance patterns and customization of BaseTool."""

    def test_mock_tool_inheritance(self):
        """Test that MockBaseTool properly inherits from BaseTool."""
        tool = MockBaseTool("mock_tool", "Mock tool for testing")

        assert isinstance(tool, BaseTool)
        assert tool.name == "mock_tool"
        assert tool.description == "Mock tool for testing"

    @pytest.mark.asyncio
    async def test_mock_tool_call_tracking(self):
        """Test that MockBaseTool tracks method calls."""
        tool = MockBaseTool("mock_tool", "Mock tool")

        await tool.execute(param1="value1")

        assert tool.call_count == 1
        assert tool.last_kwargs == {"param1": "value1"}

    @pytest.mark.asyncio
    async def test_mock_tool_multiple_calls(self):
        """Test MockBaseTool with multiple calls."""
        tool = MockBaseTool("mock_tool", "Mock tool")

        await tool.execute(first="call")
        await tool.execute(second="call")

        assert tool.call_count == 2
        assert tool.last_kwargs == {"second": "call"}

    @pytest.mark.asyncio
    async def test_mock_tool_custom_result(self):
        """Test MockBaseTool with custom result."""
        custom_result = ToolResult(success=False, error="Custom error")
        tool = MockBaseTool("mock_tool", "Mock tool", custom_result)

        result = await tool.execute()

        assert result == custom_result
        assert result.success is False
        assert result.error == "Custom error"


class TestModuleIntegration:
    """Integration tests for the base tools module."""

    def test_module_imports(self):
        """Test that all expected classes can be imported."""
        from copilot.core.mcp.tools.base import BaseTool, ToolResult

        # Verify classes exist
        assert BaseTool is not None
        assert ToolResult is not None

    def test_pydantic_integration(self):
        """Test integration with Pydantic."""
        from pydantic import BaseModel

        # ToolResult should be a Pydantic model
        assert issubclass(ToolResult, BaseModel)

    def test_abc_integration(self):
        """Test integration with ABC (Abstract Base Classes)."""
        from abc import ABC

        # BaseTool should be an abstract class
        assert issubclass(BaseTool, ABC)

    def test_tool_result_in_tool_execution(self):
        """Test that tools properly return ToolResult instances."""
        tool = ConcreteBaseTool("integration_test", "Integration test tool")

        # This is synchronous test - we know the execute method returns ToolResult
        import asyncio

        result = asyncio.run(tool.execute(test_param="test_value"))

        assert isinstance(result, ToolResult)
        assert result.success is True
        assert "test_param" in result.data
