"""
Tests for query parameter token authentication in MCP auth utilities.

This module tests the fallback mechanism that allows authentication
via the ?token= query parameter for MCP clients that cannot send custom headers.
"""

from unittest.mock import MagicMock, patch

from copilot.core.mcp.auth_utils import extract_etendo_token_from_request
from fastapi import Request


class TestQueryParamTokenExtraction:
    """Test cases for token extraction via query parameter."""

    def create_mock_request(self, headers: dict = None, query_params: dict = None) -> MagicMock:
        """Create a mock request object with given headers and query params."""
        mock_request = MagicMock()
        mock_request.headers = headers or {}
        mock_request.query_params = query_params or {}
        return mock_request

    def test_token_from_query_param_when_no_headers(self):
        """Test that token is extracted from query param when no auth headers are present."""
        mock_request = self.create_mock_request(query_params={"token": "qp-token-123"})

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer qp-token-123"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer qp-token-123"
            mock_normalize.assert_called_once_with("qp-token-123")

    def test_etendo_token_header_has_priority_over_query_param(self):
        """Test that etendo-token header takes priority over query param."""
        mock_request = self.create_mock_request(
            headers={"etendo-token": "header-token"},
            query_params={"token": "qp-token"},
        )

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer header-token"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer header-token"
            mock_normalize.assert_called_once_with("header-token")

    def test_authorization_header_has_priority_over_query_param(self):
        """Test that Authorization header takes priority over query param."""
        mock_request = self.create_mock_request(
            headers={"authorization": "Bearer auth-token"},
            query_params={"token": "qp-token"},
        )

        result = extract_etendo_token_from_request(mock_request)
        assert result == "Bearer auth-token"

    def test_x_etendo_token_header_has_priority_over_query_param(self):
        """Test that X-Etendo-Token header takes priority over query param."""
        mock_request = self.create_mock_request(
            headers={"x-etendo-token": "x-token"},
            query_params={"token": "qp-token"},
        )

        with patch(
            "copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer x-token"
        ) as mock_normalize:
            result = extract_etendo_token_from_request(mock_request)

            assert result == "Bearer x-token"
            mock_normalize.assert_called_once_with("x-token")

    def test_empty_query_param_is_ignored(self):
        """Test that an empty token query param is ignored."""
        mock_request = self.create_mock_request(query_params={"token": ""})

        result = extract_etendo_token_from_request(mock_request)
        assert result is None

    def test_whitespace_query_param_is_ignored(self):
        """Test that a whitespace-only token query param is ignored."""
        mock_request = self.create_mock_request(query_params={"token": "   "})

        result = extract_etendo_token_from_request(mock_request)
        assert result is None

    def test_no_token_query_param_returns_none(self):
        """Test that absence of token query param returns None when no headers."""
        mock_request = self.create_mock_request(query_params={"other_param": "value"})

        result = extract_etendo_token_from_request(mock_request)
        assert result is None

    def test_real_fastapi_request_with_query_param(self):
        """Test with a real FastAPI Request object containing a token query param."""
        scope = {
            "type": "http",
            "method": "GET",
            "path": "/test",
            "headers": [],
            "query_string": b"token=real-qp-token",
        }

        request = Request(scope)

        with patch("copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer real-qp-token"):
            result = extract_etendo_token_from_request(request)
            assert result == "Bearer real-qp-token"

    def test_real_fastapi_request_header_priority_over_query_param(self):
        """Test that headers have priority over query param with real FastAPI Request."""
        scope = {
            "type": "http",
            "method": "GET",
            "path": "/test",
            "headers": [(b"etendo-token", b"header-token")],
            "query_string": b"token=qp-token",
        }

        request = Request(scope)

        with patch("copilot.core.mcp.auth_utils.normalize_etendo_token", return_value="Bearer header-token"):
            result = extract_etendo_token_from_request(request)
            assert result == "Bearer header-token"
