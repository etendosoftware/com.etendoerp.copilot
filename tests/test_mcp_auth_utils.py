"""
Tests for MCP auth utilities module.

This module tests the authentication utilities for MCP server.
"""

import logging
from unittest.mock import MagicMock, patch

from copilot.core.mcp.auth_utils import (
    extract_etendo_token_from_mcp_context,
    extract_etendo_token_from_request,
)


class TestExtractEtendoTokenFromRequest:
    """Test cases for extract_etendo_token_from_request function."""

    def create_mock_request(self, headers: dict = None) -> MagicMock:
        """Create a mock request object with given headers."""
        mock_request = MagicMock()
        mock_request.headers = headers or {}
        return mock_request

    def test_extract_token_from_etendo_token_header(self):
        """Test extracting token from etendo-token header."""
        mock_request = self.create_mock_request({"etendo-token": "test-token-123"})

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer test-token-123"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer test-token-123"
            mock_normalize.assert_called_once_with("test-token-123")

    def test_extract_token_from_authorization_header(self):
        """Test extracting token from Authorization header."""
        mock_request = self.create_mock_request({"authorization": "Bearer auth-token-456"})

        result = extract_etendo_token_from_request(mock_request)
        assert result == "Bearer auth-token-456"

    def test_extract_token_from_authorization_header_uppercase(self):
        """Test extracting token from Authorization header (uppercase)."""
        mock_request = self.create_mock_request({"Authorization": "Bearer auth-token-789"})

        result = extract_etendo_token_from_request(mock_request)
        assert result == "Bearer auth-token-789"

    def test_extract_token_from_x_etendo_token_header(self):
        """Test extracting token from X-Etendo-Token header."""
        mock_request = self.create_mock_request({"x-etendo-token": "x-token-123"})

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer x-token-123"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer x-token-123"
            mock_normalize.assert_called_once_with("x-token-123")

    def test_extract_token_from_x_etendo_token_header_uppercase(self):
        """Test extracting token from X-Etendo-Token header (uppercase)."""
        mock_request = self.create_mock_request({"X-Etendo-Token": "x-token-456"})

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer x-token-456"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer x-token-456"
            mock_normalize.assert_called_once_with("x-token-456")

    def test_extract_token_priority_etendo_token_first(self):
        """Test that etendo-token header has priority over other headers."""
        mock_request = self.create_mock_request(
            {
                "etendo-token": "priority-token",
                "authorization": "Bearer auth-token",
                "x-etendo-token": "x-token",
            }
        )

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer priority-token"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer priority-token"
            mock_normalize.assert_called_once_with("priority-token")

    def test_extract_token_fallback_to_authorization(self):
        """Test fallback to authorization header when etendo-token is not present."""
        mock_request = self.create_mock_request(
            {"authorization": "Bearer fallback-token", "x-etendo-token": "x-token"}
        )

        result = extract_etendo_token_from_request(mock_request)
        assert result == "Bearer fallback-token"

    def test_extract_token_fallback_to_x_etendo_token(self):
        """Test fallback to x-etendo-token when other headers are not present."""
        mock_request = self.create_mock_request({"x-etendo-token": "last-resort-token"})

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer last-resort-token"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer last-resort-token"
            mock_normalize.assert_called_once_with("last-resort-token")

    def test_extract_token_empty_headers(self):
        """Test behavior when no relevant headers are present."""
        mock_request = self.create_mock_request(
            {"content-type": "application/json", "user-agent": "test-agent"}
        )

        result = extract_etendo_token_from_request(mock_request)
        assert result is None

    def test_extract_token_empty_values(self):
        """Test behavior when headers have empty values."""
        mock_request = self.create_mock_request(
            {"etendo-token": "", "authorization": "", "x-etendo-token": ""}
        )

        result = extract_etendo_token_from_request(mock_request)
        assert result is None

    def test_extract_token_whitespace_values(self):
        """Test behavior when headers have only whitespace."""
        mock_request = self.create_mock_request(
            {"etendo-token": "   ", "authorization": "Bearer auth-token", "x-etendo-token": "  "}
        )

        result = extract_etendo_token_from_request(mock_request)
        assert result == "Bearer auth-token"

    def test_extract_token_invalid_authorization_format(self):
        """Test behavior when authorization header doesn't start with Bearer."""
        mock_request = self.create_mock_request(
            {"authorization": "Basic dXNlcjpwYXNz", "x-etendo-token": "valid-token"}
        )

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer valid-token"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer valid-token"
            mock_normalize.assert_called_once_with("valid-token")

    def test_extract_token_exception_handling(self):
        """Test exception handling in token extraction."""
        mock_request = MagicMock()
        mock_request.headers.get.side_effect = Exception("Header access error")

        with patch("copilot.core.mcp.auth_utils.logger") as mock_logger:
            result = extract_etendo_token_from_request(mock_request)

            assert result is None
            mock_logger.error.assert_called_once()
            assert "Error extracting Etendo token from request" in str(mock_logger.error.call_args)


class TestExtractEtendoTokenFromMcpContext:
    """Test cases for extract_etendo_token_from_mcp_context function."""

    def test_extract_token_success(self):
        """Test successful token extraction from MCP context."""
        mock_context = MagicMock()
        mock_request = MagicMock()
        mock_context.get_http_request.return_value = mock_request

        with patch("fastmcp.server.dependencies.get_context", return_value=mock_context), patch(
            "copilot.core.mcp.auth_utils.extract_etendo_token_from_request", return_value="Bearer mcp-token"
        ) as mock_extract:
            result = extract_etendo_token_from_mcp_context()

            assert result == "Bearer mcp-token"
            mock_context.get_http_request.assert_called_once()
            mock_extract.assert_called_once_with(mock_request)

    def test_extract_token_context_error(self):
        """Test handling of context retrieval error."""
        with patch(
            "fastmcp.server.dependencies.get_context", side_effect=ImportError("FastMCP not available")
        ), patch("copilot.core.mcp.auth_utils.logger") as mock_logger:
            result = extract_etendo_token_from_mcp_context()

            assert result is None
            # ImportError is logged as debug, not error
            mock_logger.debug.assert_called_once()
            assert "FastMCP dependencies not available" in str(mock_logger.debug.call_args)

    def test_extract_token_request_error(self):
        """Test handling of request retrieval error."""
        mock_context = MagicMock()
        mock_context.get_http_request.side_effect = Exception("Request error")

        with patch("fastmcp.server.dependencies.get_context", return_value=mock_context), patch(
            "copilot.core.mcp.auth_utils.logger"
        ) as mock_logger:
            result = extract_etendo_token_from_mcp_context()

            assert result is None
            mock_logger.error.assert_called_once()
            assert "Error extracting Etendo token from MCP context" in str(mock_logger.error.call_args)

    def test_extract_token_extract_function_error(self):
        """Test handling of extract function error."""
        mock_context = MagicMock()
        mock_request = MagicMock()
        mock_context.get_http_request.return_value = mock_request

        with patch("fastmcp.server.dependencies.get_context", return_value=mock_context), patch(
            "copilot.core.mcp.auth_utils.extract_etendo_token_from_request",
            side_effect=Exception("Extract error"),
        ), patch("copilot.core.mcp.auth_utils.logger") as mock_logger:
            result = extract_etendo_token_from_mcp_context()

            assert result is None
            mock_logger.error.assert_called_once()
            assert "Error extracting Etendo token from MCP context" in str(mock_logger.error.call_args)


class TestModuleIntegration:
    """Integration tests for the auth_utils module."""

    def test_module_imports(self):
        """Test that all expected functions can be imported."""
        from copilot.core.mcp.auth_utils import (
            extract_etendo_token_from_mcp_context,
            extract_etendo_token_from_request,
        )

        # Verify functions exist and are callable
        assert callable(extract_etendo_token_from_request)
        assert callable(extract_etendo_token_from_mcp_context)

    def test_logger_configuration(self):
        """Test that logger is properly configured."""
        import copilot.core.mcp.auth_utils as auth_module

        assert hasattr(auth_module, "logger")
        assert isinstance(auth_module.logger, logging.Logger)
        assert auth_module.logger.name == "copilot.core.mcp.auth_utils"

    def test_real_request_object_compatibility(self):
        """Test compatibility with real FastAPI Request objects."""
        from fastapi import Request

        # Create a mock ASGI scope
        scope = {
            "type": "http",
            "method": "GET",
            "path": "/test",
            "headers": [(b"etendo-token", b"test-token"), (b"content-type", b"application/json")],
        }

        # Create a real Request object (without actually sending HTTP)
        request = Request(scope)

        with patch("copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer test-token"):
            result = extract_etendo_token_from_request(request)
            assert result == "Bearer test-token"
