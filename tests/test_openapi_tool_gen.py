"""Tests for OpenAPI tool generation functionality."""

from typing import Any, Dict
from unittest.mock import MagicMock, Mock, patch

import pytest
from copilot.core.tool_wrapper import CopilotTool
from copilot.core.toolgen.openapi_tool_gen import (
    _generate_function_code,
    _generate_tool_name,
    _get_type_mapping,
    _process_openapi_parameters,
    _process_request_body,
    _process_single_operation,
    generate_tools_from_openapi,
    replace_base64_filepaths,
    sanitize_tool_name,
)


class TestSanitizeToolName:
    """Test cases for tool name sanitization."""

    def test_sanitize_valid_name(self):
        """Test that valid names are not changed."""
        assert sanitize_tool_name("valid_name") == "valid_name"
        assert sanitize_tool_name("ValidName123") == "ValidName123"
        assert sanitize_tool_name("valid-name") == "valid-name"

    def test_sanitize_invalid_characters(self):
        """Test that invalid characters are replaced with underscores."""
        assert sanitize_tool_name("invalid@name") == "invalid_name"
        assert sanitize_tool_name("name.with.dots") == "name_with_dots"
        assert sanitize_tool_name("name with spaces") == "name_with_spaces"

    def test_sanitize_multiple_underscores(self):
        """Test that multiple consecutive underscores are reduced."""
        assert sanitize_tool_name("name___with___many") == "name_with_many"
        assert sanitize_tool_name("name@@@@test") == "name_test"

    def test_sanitize_edge_cases(self):
        """Test edge cases for name sanitization."""
        assert sanitize_tool_name("_name_") == "name"
        assert sanitize_tool_name("___") == "generated_tool"
        assert sanitize_tool_name("") == "generated_tool"
        assert sanitize_tool_name("@@@") == "generated_tool"


class TestReplaceBase64Filepaths:
    """Test cases for base64 filepath replacement."""

    def test_replace_base64_with_mock_file(self):
        """Test base64 replacement with mock file content."""
        with patch("builtins.open", create=True) as mock_open:
            mock_file = MagicMock()
            mock_file.read.return_value = b"test content"
            mock_open.return_value.__enter__.return_value = mock_file

            result = replace_base64_filepaths("prefix @BASE64_test.txt@ suffix")
            expected = "prefix dGVzdCBjb250ZW50 suffix"  # base64 encoded "test content"
            assert result == expected

    def test_replace_base64_no_markers(self):
        """Test that strings without base64 markers are unchanged."""
        test_string = "no base64 markers here"
        result = replace_base64_filepaths(test_string)
        assert result == test_string

    def test_replace_base64_multiple_markers(self):
        """Test replacement of multiple base64 markers."""
        with patch("builtins.open", create=True) as mock_open:
            mock_file = MagicMock()
            mock_file.read.return_value = b"test"
            mock_open.return_value.__enter__.return_value = mock_file

            result = replace_base64_filepaths("@BASE64_file1.txt@ and @BASE64_file2.txt@")
            expected = "dGVzdA== and dGVzdA=="  # base64 encoded "test"
            assert result == expected


class TestHelperFunctions:
    """Test cases for helper functions."""

    def test_get_type_mapping(self):
        """Test that type mapping is correctly returned."""
        type_map = _get_type_mapping()
        assert "string" in type_map
        assert "integer" in type_map
        assert "number" in type_map
        assert "boolean" in type_map
        assert "array" in type_map
        assert "object" in type_map

    def test_process_openapi_parameters(self):
        """Test processing of OpenAPI parameters."""
        type_map = _get_type_mapping()
        params = [
            {
                "name": "testparam",
                "in": "query",
                "schema": {"type": "string"},
                "description": "Test parameter",
                "required": True,
            },
            {
                "name": "optionalparam",
                "in": "query",
                "schema": {"type": "integer"},
                "description": "Optional parameter",
            },
            {
                "name": "pathparam",
                "in": "path",
                "schema": {"type": "string"},
                "description": "Path parameter",
            },
        ]

        model_fields, _ = _process_openapi_parameters(params, type_map)

        assert "testparam" in model_fields
        assert "optionalparam" in model_fields
        assert "pathparam" in model_fields

    def test_process_openapi_parameters_skip_underscore(self):
        """Test that parameters with underscores are skipped."""
        type_map = _get_type_mapping()
        params = [
            {
                "name": "validparam",
                "in": "query",
                "schema": {"type": "string"},
                "description": "Valid parameter",
            },
            {
                "name": "under_score",
                "in": "query",
                "schema": {"type": "string"},
                "description": "Parameter with underscore",
            },
        ]

        model_fields, _ = _process_openapi_parameters(params, type_map)

        assert "validparam" in model_fields  # Should be included
        assert "under_score" not in model_fields  # Has underscore, should be skipped

    def test_process_request_body_post(self):
        """Test processing of request body for POST method."""
        type_map = _get_type_mapping()
        operation = {
            "requestBody": {
                "description": "Test request body",
                "content": {
                    "application/json": {
                        "schema": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string", "description": "Name field"},
                                "age": {"type": "integer", "description": "Age field"},
                            },
                            "required": ["name"],
                        }
                    }
                },
            }
        }

        result = _process_request_body("post", operation, "/test", type_map)
        assert result is not None
        _, body_description = result
        assert body_description == "Test request body"

    def test_process_request_body_get(self):
        """Test that GET methods don't process request body."""
        type_map = _get_type_mapping()
        operation = {"requestBody": {"description": "Should be ignored"}}

        result = _process_request_body("get", operation, "/test", type_map)
        assert result is None

    def test_generate_tool_name_with_operation_id(self):
        """Test tool name generation with operationId."""
        operation = {"operationId": "testOperation"}
        result = _generate_tool_name(operation, "/test/path")
        assert result == "testOperation"

    def test_generate_tool_name_without_operation_id(self):
        """Test tool name generation without operationId."""
        operation = {}
        result = _generate_tool_name(operation, "/test/path")
        assert result == "test_path"

    def test_generate_tool_name_empty_path(self):
        """Test tool name generation with empty path."""
        operation = {}
        result = _generate_tool_name(operation, "/")
        assert result == "api_call"

    def test_generate_function_code(self):
        """Test generation of function code."""
        param_names = ["param1", "param2", "token"]
        param_locations = {"param1": "query", "param2": "path"}

        code = _generate_function_code("get", "http://api.test", "/test", param_names, param_locations)

        assert "def _run_dynamic(self, param1, param2, token):" in code
        assert "method='GET'" in code
        assert "url='http://api.test'" in code
        assert "endpoint='/test'" in code


class TestGenerateToolsFromOpenapi:
    """Test cases for the main function."""

    def get_sample_openapi_spec(self) -> Dict[str, Any]:
        """Get a sample OpenAPI specification for testing."""
        return {
            "openapi": "3.0.0",
            "info": {"title": "Test API", "version": "1.0.0"},
            "servers": [{"url": "https://api.test.com"}],
            "paths": {
                "/users/{id}": {
                    "get": {
                        "operationId": "getUser",
                        "summary": "Get a user by ID",
                        "parameters": [
                            {
                                "name": "id",
                                "in": "path",
                                "required": True,
                                "schema": {"type": "string"},
                                "description": "User ID",
                            },
                            {
                                "name": "include",
                                "in": "query",
                                "schema": {"type": "string"},
                                "description": "Fields to include",
                            },
                        ],
                    }
                },
                "/users": {
                    "post": {
                        "operationId": "createUser",
                        "summary": "Create a new user",
                        "requestBody": {
                            "description": "User data",
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "type": "object",
                                        "properties": {
                                            "name": {"type": "string", "description": "User name"},
                                            "email": {"type": "string", "description": "User email"},
                                        },
                                        "required": ["name", "email"],
                                    }
                                }
                            },
                        },
                    }
                },
            },
        }

    @patch("copilot.core.toolgen.openapi_tool_gen.do_request")
    def test_generate_tools_basic(self, mock_do_request):
        """Test basic tool generation from OpenAPI spec."""
        mock_do_request.return_value = {"status": "success"}

        spec = self.get_sample_openapi_spec()
        tools = generate_tools_from_openapi(spec)

        assert len(tools) == 2
        tool_names = [tool.name for tool in tools]
        assert "GETGetuser" in tool_names
        assert "POSTCreateuser" in tool_names

    @patch("copilot.core.toolgen.openapi_tool_gen.do_request")
    def test_generate_tools_empty_spec(self, mock_do_request):
        """Test tool generation with empty spec."""
        spec = {"openapi": "3.0.0", "info": {"title": "Empty", "version": "1.0.0"}}
        tools = generate_tools_from_openapi(spec)
        assert len(tools) == 0

    @patch("copilot.core.toolgen.openapi_tool_gen.do_request")
    def test_generate_tools_unsupported_methods(self, mock_do_request):
        """Test that unsupported HTTP methods are ignored."""
        spec = {
            "openapi": "3.0.0",
            "info": {"title": "Test", "version": "1.0.0"},
            "servers": [{"url": "https://api.test.com"}],
            "paths": {
                "/test": {
                    "options": {"summary": "Options request"},
                    "head": {"summary": "Head request"},
                    "trace": {"summary": "Trace request"},
                    "get": {"summary": "Get request"},
                }
            },
        }

        tools = generate_tools_from_openapi(spec)
        assert len(tools) == 1  # Only GET should be processed

    @patch("copilot.core.toolgen.openapi_tool_gen.do_request")
    def test_tool_execution(self, mock_do_request):
        """Test that generated tools can be executed."""
        mock_do_request.return_value = {"result": "success"}

        spec = {
            "openapi": "3.0.0",
            "info": {"title": "Test", "version": "1.0.0"},
            "servers": [{"url": "https://api.test.com"}],
            "paths": {
                "/test": {
                    "get": {
                        "operationId": "testGet",
                        "summary": "Test GET",
                        "parameters": [
                            {
                                "name": "param1",
                                "in": "query",
                                "schema": {"type": "string"},
                                "description": "Test parameter",
                            }
                        ],
                    }
                }
            },
        }

        tools = generate_tools_from_openapi(spec)
        tool = tools[0]

        # Execute the tool
        result = tool._run(param1="test_value", token=None)

        # Verify the request was made correctly
        mock_do_request.assert_called_once()
        call_args = mock_do_request.call_args[1]
        assert call_args["method"] == "GET"
        assert call_args["url"] == "https://api.test.com"
        assert call_args["endpoint"] == "/test"
        assert call_args["query_params"] == {"param1": "test_value"}
        assert result == {"result": "success"}

    def test_process_single_operation(self):
        """Test processing of a single operation."""
        type_map = _get_type_mapping()
        operation = {
            "operationId": "testOp",
            "summary": "Test operation",
            "parameters": [
                {
                    "name": "testparam",
                    "in": "query",
                    "schema": {"type": "string"},
                    "description": "Test parameter",
                }
            ],
        }

        with patch("copilot.core.toolgen.openapi_tool_gen.exec") as mock_exec, patch(
            "copilot.core.toolgen.openapi_tool_gen._create_tool_instance"
        ) as mock_create_tool:
            # Mock the exec function to simulate successful function creation
            mock_namespace = {"_run_dynamic": Mock()}
            mock_exec.side_effect = lambda code, global_ns, local_ns: local_ns.update(mock_namespace)

            # Mock tool creation
            mock_tool = Mock()
            mock_create_tool.return_value = mock_tool

            result = _process_single_operation(
                "/test", "get", operation, "https://api.test.com", type_map, CopilotTool
            )

            assert result == mock_tool
            mock_create_tool.assert_called_once()


if __name__ == "__main__":
    pytest.main([__file__])
