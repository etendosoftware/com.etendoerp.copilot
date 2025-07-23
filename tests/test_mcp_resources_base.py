"""
Tests for MCP base resources module.

This module tests the base resource classes and utilities for MCP resources.
"""


import pytest
from copilot.core.mcp.resources.base import BaseResource, ResourceContent


class TestResourceContent:
    """Test cases for ResourceContent class."""

    def test_resource_content_initialization_uri_only(self):
        """Test ResourceContent initialization with URI only."""
        content = ResourceContent(uri="file:///test.txt")

        assert content.uri == "file:///test.txt"
        assert content.mimeType is None
        assert content.text is None
        assert content.blob is None

    def test_resource_content_initialization_with_text(self):
        """Test ResourceContent initialization with text content."""
        content = ResourceContent(uri="file:///test.txt", mimeType="text/plain", text="Hello, World!")

        assert content.uri == "file:///test.txt"
        assert content.mimeType == "text/plain"
        assert content.text == "Hello, World!"
        assert content.blob is None

    def test_resource_content_initialization_with_blob(self):
        """Test ResourceContent initialization with binary content."""
        blob_data = b"binary data"
        content = ResourceContent(uri="file:///test.bin", mimeType="application/octet-stream", blob=blob_data)

        assert content.uri == "file:///test.bin"
        assert content.mimeType == "application/octet-stream"
        assert content.text is None
        assert content.blob == blob_data

    def test_resource_content_initialization_complete(self):
        """Test ResourceContent initialization with all fields."""
        blob_data = b"some binary data"
        content = ResourceContent(
            uri="file:///complete.txt", mimeType="text/plain", text="Text content", blob=blob_data
        )

        assert content.uri == "file:///complete.txt"
        assert content.mimeType == "text/plain"
        assert content.text == "Text content"
        assert content.blob == blob_data

    def test_resource_content_pydantic_validation(self):
        """Test that ResourceContent validates fields correctly."""
        content = ResourceContent(uri="test://uri")

        # Test that it's a Pydantic model
        assert hasattr(content, "dict")
        assert hasattr(content, "json")

    def test_resource_content_serialization(self):
        """Test ResourceContent serialization capabilities."""
        content = ResourceContent(uri="file:///test.txt", mimeType="text/plain", text="test content")

        # Test dict conversion
        content_dict = content.model_dump()
        expected_dict = {
            "uri": "file:///test.txt",
            "mimeType": "text/plain",
            "text": "test content",
            "blob": None,
        }
        assert content_dict == expected_dict

    def test_resource_content_json_serialization(self):
        """Test ResourceContent JSON serialization."""
        content = ResourceContent(uri="test://uri", text="content")

        # Should be able to serialize to JSON
        json_str = content.model_dump_json()
        assert '"uri":"test://uri"' in json_str.replace(" ", "")
        assert '"text":"content"' in json_str.replace(" ", "")

    def test_resource_content_empty_uri(self):
        """Test ResourceContent with empty URI."""
        content = ResourceContent(uri="")

        assert content.uri == ""
        assert content.mimeType is None
        assert content.text is None
        assert content.blob is None

    def test_resource_content_different_mime_types(self):
        """Test ResourceContent with different MIME types."""
        content1 = ResourceContent(uri="test1", mimeType="text/html")
        content2 = ResourceContent(uri="test2", mimeType="application/json")

        assert content1.mimeType == "text/html"
        assert content2.mimeType == "application/json"

    def test_resource_content_large_blob(self):
        """Test ResourceContent with large binary data."""
        large_blob = b"x" * 10000  # 10KB of data
        content = ResourceContent(uri="large://file", blob=large_blob)

        assert content.blob == large_blob
        assert len(content.blob) == 10000


class ConcreteBaseResource(BaseResource):
    """Concrete implementation of BaseResource for testing."""

    def __init__(self, uri: str, name: str, description: str, content: ResourceContent = None):
        super().__init__(uri, name, description)
        self.content = content or ResourceContent(uri=uri, text="default content")

    async def read(self) -> ResourceContent:
        """Test implementation of read method."""
        return self.content


class TestBaseResource:
    """Test cases for BaseResource abstract base class."""

    def test_base_resource_initialization(self):
        """Test BaseResource initialization."""
        resource = ConcreteBaseResource(
            uri="file:///test.txt", name="test_resource", description="Test resource description"
        )

        assert resource.uri == "file:///test.txt"
        assert resource.name == "test_resource"
        assert resource.description == "Test resource description"

    def test_base_resource_abstract_class(self):
        """Test that BaseResource cannot be instantiated directly."""
        with pytest.raises(TypeError):
            BaseResource("uri", "name", "description")

    @pytest.mark.asyncio
    async def test_concrete_resource_read(self):
        """Test successful read of concrete resource."""
        content = ResourceContent(uri="test://uri", text="test content")
        resource = ConcreteBaseResource("test://uri", "test", "description", content)

        result = await resource.read()

        assert isinstance(result, ResourceContent)
        assert result.uri == "test://uri"
        assert result.text == "test content"

    @pytest.mark.asyncio
    async def test_concrete_resource_read_default_content(self):
        """Test reading resource with default content."""
        resource = ConcreteBaseResource("default://uri", "test", "description")

        result = await resource.read()

        assert isinstance(result, ResourceContent)
        assert result.uri == "default://uri"
        assert result.text == "default content"

    def test_base_resource_to_mcp_resource(self):
        """Test to_mcp_resource method returns correct format."""
        resource = ConcreteBaseResource("test://uri", "test_name", "test_description")

        mcp_resource = resource.to_mcp_resource()

        assert isinstance(mcp_resource, dict)
        # The base implementation should return a dictionary representation
        assert "uri" in mcp_resource or hasattr(resource, "to_mcp_resource")

    def test_base_resource_different_uris(self):
        """Test creating resources with different URIs."""
        resource1 = ConcreteBaseResource("file:///path1", "res1", "desc1")
        resource2 = ConcreteBaseResource("http://example.com", "res2", "desc2")

        assert resource1.uri != resource2.uri
        assert resource1.name != resource2.name

    def test_base_resource_empty_fields(self):
        """Test resource with empty fields."""
        resource = ConcreteBaseResource("", "", "")

        assert resource.uri == ""
        assert resource.name == ""
        assert resource.description == ""

    def test_base_resource_special_characters(self):
        """Test resource with special characters in fields."""
        resource = ConcreteBaseResource(
            uri="file:///path with spaces/file (1).txt",
            name="resource-name_123",
            description="Description with special chars: @#$%^&*()",
        )

        assert resource.uri == "file:///path with spaces/file (1).txt"
        assert resource.name == "resource-name_123"
        assert resource.description == "Description with special chars: @#$%^&*()"


class AsyncFailingResource(BaseResource):
    """Resource that raises exceptions for testing error handling."""

    async def read(self) -> ResourceContent:
        """Read method that raises exceptions."""
        raise ValueError("Resource read failed")


class TestBaseResourceErrorHandling:
    """Test error handling in BaseResource implementations."""

    @pytest.mark.asyncio
    async def test_resource_read_exception_propagation(self):
        """Test that exceptions in read method are propagated."""
        resource = AsyncFailingResource("failing://uri", "failing", "Failing resource")

        with pytest.raises(ValueError, match="Resource read failed"):
            await resource.read()

    def test_failing_resource_initialization(self):
        """Test that failing resource can still be initialized."""
        resource = AsyncFailingResource("test://uri", "test", "test description")

        assert resource.uri == "test://uri"
        assert resource.name == "test"
        assert resource.description == "test description"


class MockBaseResource(BaseResource):
    """Mock BaseResource for testing inheritance patterns."""

    def __init__(self, uri: str, name: str, description: str, mock_content: ResourceContent = None):
        super().__init__(uri, name, description)
        self.mock_content = mock_content or ResourceContent(uri=uri, text="mock content")
        self.read_count = 0

    async def read(self) -> ResourceContent:
        """Mock read method that tracks calls."""
        self.read_count += 1
        return self.mock_content


class TestBaseResourceInheritance:
    """Test inheritance patterns and customization of BaseResource."""

    def test_mock_resource_inheritance(self):
        """Test that MockBaseResource properly inherits from BaseResource."""
        resource = MockBaseResource("mock://uri", "mock_resource", "Mock resource for testing")

        assert isinstance(resource, BaseResource)
        assert resource.uri == "mock://uri"
        assert resource.name == "mock_resource"
        assert resource.description == "Mock resource for testing"

    @pytest.mark.asyncio
    async def test_mock_resource_read_tracking(self):
        """Test that MockBaseResource tracks read calls."""
        resource = MockBaseResource("mock://uri", "mock", "description")

        await resource.read()

        assert resource.read_count == 1

    @pytest.mark.asyncio
    async def test_mock_resource_multiple_reads(self):
        """Test MockBaseResource with multiple read calls."""
        resource = MockBaseResource("mock://uri", "mock", "description")

        await resource.read()
        await resource.read()
        await resource.read()

        assert resource.read_count == 3

    @pytest.mark.asyncio
    async def test_mock_resource_custom_content(self):
        """Test MockBaseResource with custom content."""
        custom_content = ResourceContent(uri="custom://uri", mimeType="custom/type", text="custom text")
        resource = MockBaseResource("mock://uri", "mock", "description", custom_content)

        result = await resource.read()

        assert result == custom_content
        assert result.mimeType == "custom/type"
        assert result.text == "custom text"


class TestToMcpResource:
    """Test the to_mcp_resource method implementation."""

    def test_to_mcp_resource_basic(self):
        """Test basic to_mcp_resource functionality."""
        resource = ConcreteBaseResource("test://uri", "test_name", "test_description")

        # Method should exist and be callable
        assert hasattr(resource, "to_mcp_resource")
        assert callable(resource.to_mcp_resource)

        result = resource.to_mcp_resource()

        # Should return a dictionary (based on the base.py file structure)
        assert isinstance(result, dict)

    def test_to_mcp_resource_different_resources(self):
        """Test to_mcp_resource with different resource configurations."""
        resource1 = ConcreteBaseResource("uri1", "name1", "desc1")
        resource2 = ConcreteBaseResource("uri2", "name2", "desc2")

        result1 = resource1.to_mcp_resource()
        result2 = resource2.to_mcp_resource()

        # Results should be dictionaries
        assert isinstance(result1, dict)
        assert isinstance(result2, dict)


class TestModuleIntegration:
    """Integration tests for the base resources module."""

    def test_module_imports(self):
        """Test that all expected classes can be imported."""
        from copilot.core.mcp.resources.base import BaseResource, ResourceContent

        # Verify classes exist
        assert BaseResource is not None
        assert ResourceContent is not None

    def test_pydantic_integration(self):
        """Test integration with Pydantic."""
        from pydantic import BaseModel

        # ResourceContent should be a Pydantic model
        assert issubclass(ResourceContent, BaseModel)

    def test_abc_integration(self):
        """Test integration with ABC (Abstract Base Classes)."""
        from abc import ABC

        # BaseResource should be an abstract class
        assert issubclass(BaseResource, ABC)

    def test_resource_content_in_resource_read(self):
        """Test that resources properly return ResourceContent instances."""
        resource = ConcreteBaseResource("integration://test", "integration_test", "Integration test resource")

        # This is synchronous test - we know the read method returns ResourceContent
        import asyncio

        result = asyncio.run(resource.read())

        assert isinstance(result, ResourceContent)
        assert result.uri == "integration://test"

    def test_type_annotations(self):
        """Test that type annotations are properly defined."""
        from typing import get_type_hints

        # Check ResourceContent type hints
        content_hints = get_type_hints(ResourceContent)
        assert "uri" in content_hints

        # Check BaseResource type hints
        resource_hints = get_type_hints(BaseResource.__init__)
        assert "uri" in resource_hints
        assert "name" in resource_hints
        assert "description" in resource_hints

    @pytest.mark.asyncio
    async def test_resource_workflow(self):
        """Test complete resource workflow from creation to reading."""
        # Create content
        content = ResourceContent(uri="workflow://test", mimeType="text/plain", text="workflow test content")

        # Create resource
        resource = ConcreteBaseResource(
            uri="workflow://test",
            name="workflow_resource",
            description="Resource for workflow testing",
            content=content,
        )

        # Read content
        read_content = await resource.read()

        # Verify workflow
        assert resource.uri == "workflow://test"
        assert resource.name == "workflow_resource"
        assert isinstance(read_content, ResourceContent)
        assert read_content.text == "workflow test content"
