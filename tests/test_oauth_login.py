"""Unit tests for OAuth login routes."""

from unittest.mock import AsyncMock, patch

import pytest
from copilot.core.mcp.oauth_login import create_oauth_login_router
from copilot.core.mcp.oauth_provider import EtendoOAuthProvider, PendingAuth
from fastapi import FastAPI
from fastapi.testclient import TestClient
from mcp.server.auth.provider import AuthorizationParams
from pydantic import AnyHttpUrl


@pytest.fixture
def provider():
    return EtendoOAuthProvider(base_url="http://localhost:5006")


@pytest.fixture
def app(provider):
    app = FastAPI()
    router = create_oauth_login_router(provider)
    app.include_router(router)
    return app


@pytest.fixture
def client(app):
    return TestClient(app)


@pytest.fixture
def pending_session(provider):
    """Create a pending auth session and return its session_id."""
    import secrets

    session_id = secrets.token_urlsafe(16)
    provider._pending_auth[session_id] = PendingAuth(
        params=AuthorizationParams(
            state="test-state",
            scopes=["mcp"],
            code_challenge="test-challenge",
            redirect_uri=AnyHttpUrl("http://localhost:3000/callback"),
            redirect_uri_provided_explicitly=True,
        ),
        client_id="test-client",
    )
    return session_id


class TestLoginPage:
    def test_login_page_renders(self, client, pending_session):
        response = client.get(f"/oauth/login?session_id={pending_session}")
        assert response.status_code == 200
        assert "Etendo" in response.text
        assert "Username" in response.text or "username" in response.text

    def test_login_page_missing_session_shows_error(self, client):
        response = client.get("/oauth/login")
        assert response.status_code == 200
        assert "Missing session ID" in response.text

    def test_login_page_invalid_session_shows_error(self, client):
        response = client.get("/oauth/login?session_id=invalid-id")
        assert response.status_code == 200
        assert "expired" in response.text.lower() or "invalid" in response.text.lower()


class TestLoginSubmit:
    def test_submit_missing_credentials(self, client, pending_session):
        response = client.post(
            "/oauth/login",
            data={"session_id": pending_session, "username": "", "password": ""},
        )
        assert response.status_code == 200
        assert "required" in response.text.lower()

    @patch("copilot.core.mcp.oauth_login.httpx.AsyncClient")
    def test_submit_success_with_defaults(self, mock_client_cls, client, pending_session):
        """Test successful login with use_defaults=true (single role/org)."""
        mock_response = AsyncMock()
        mock_response.is_success = True
        mock_response.json.return_value = {
            "status": "success",
            "token": "jwt-token-abc",
            "roleList": [
                {
                    "id": "role1",
                    "name": "Admin",
                    "orgList": [{"id": "org1", "name": "Main Org"}],
                }
            ],
        }

        mock_client = AsyncMock()
        mock_client.post.return_value = mock_response
        mock_client.__aenter__ = AsyncMock(return_value=mock_client)
        mock_client.__aexit__ = AsyncMock(return_value=None)
        mock_client_cls.return_value = mock_client

        response = client.post(
            "/oauth/login",
            data={
                "session_id": pending_session,
                "username": "admin",
                "password": "admin",
                "use_defaults": "true",
            },
            follow_redirects=False,
        )
        # Should redirect to callback with code
        assert response.status_code == 302
        location = response.headers.get("location", "")
        assert "code=" in location
        assert "state=test-state" in location

    @patch("copilot.core.mcp.oauth_login.httpx.AsyncClient")
    def test_submit_invalid_credentials(self, mock_client_cls, client, pending_session):
        """Test login with invalid credentials shows error."""
        mock_response = AsyncMock()
        mock_response.is_success = False
        mock_response.json.return_value = {
            "status": "error",
            "message": "Invalid username or password",
        }

        mock_client = AsyncMock()
        mock_client.post.return_value = mock_response
        mock_client.__aenter__ = AsyncMock(return_value=mock_client)
        mock_client.__aexit__ = AsyncMock(return_value=None)
        mock_client_cls.return_value = mock_client

        response = client.post(
            "/oauth/login",
            data={
                "session_id": pending_session,
                "username": "wrong",
                "password": "wrong",
            },
        )
        assert response.status_code == 200
        assert "Invalid" in response.text

    @patch("copilot.core.mcp.oauth_login.httpx.AsyncClient")
    def test_submit_multiple_roles_shows_step2(self, mock_client_cls, client, pending_session):
        """Test that multiple roles show the role/org selection step."""
        mock_response = AsyncMock()
        mock_response.is_success = True
        mock_response.json.return_value = {
            "status": "success",
            "token": "jwt-multi",
            "roleList": [
                {
                    "id": "role1",
                    "name": "Admin",
                    "orgList": [
                        {"id": "org1", "name": "Org 1"},
                        {"id": "org2", "name": "Org 2"},
                    ],
                },
                {
                    "id": "role2",
                    "name": "User",
                    "orgList": [{"id": "org3", "name": "Org 3"}],
                },
            ],
        }

        mock_client = AsyncMock()
        mock_client.post.return_value = mock_response
        mock_client.__aenter__ = AsyncMock(return_value=mock_client)
        mock_client.__aexit__ = AsyncMock(return_value=None)
        mock_client_cls.return_value = mock_client

        response = client.post(
            "/oauth/login",
            data={
                "session_id": pending_session,
                "username": "admin",
                "password": "admin",
            },
        )
        assert response.status_code == 200
        # Should show step 2 with role/org options
        assert "role1" in response.text
        assert "role2" in response.text
        assert "Admin" in response.text


class TestLoginSelect:
    @patch("copilot.core.mcp.oauth_login.httpx.AsyncClient")
    def test_select_success_redirects(self, mock_client_cls, client, pending_session):
        """Test successful role/org selection redirects with code."""
        mock_response = AsyncMock()
        mock_response.is_success = True
        mock_response.json.return_value = {
            "status": "success",
            "token": "jwt-final-token",
        }

        mock_client = AsyncMock()
        mock_client.post.return_value = mock_response
        mock_client.__aenter__ = AsyncMock(return_value=mock_client)
        mock_client.__aexit__ = AsyncMock(return_value=None)
        mock_client_cls.return_value = mock_client

        response = client.post(
            "/oauth/login/select",
            data={
                "session_id": pending_session,
                "token": "jwt-initial",
                "role": "role1",
                "organization": "org1",
            },
            follow_redirects=False,
        )
        assert response.status_code == 302
        location = response.headers.get("location", "")
        assert "code=" in location

    def test_select_missing_fields_shows_error(self, client, pending_session):
        response = client.post(
            "/oauth/login/select",
            data={
                "session_id": pending_session,
                "token": "jwt",
                "role": "",
                "organization": "",
            },
        )
        assert response.status_code == 200
        assert "select" in response.text.lower() or "required" in response.text.lower()

    @patch("copilot.core.mcp.oauth_login.httpx.AsyncClient")
    def test_select_etendo_failure(self, mock_client_cls, client, pending_session):
        """Test that Etendo login failure shows error."""
        mock_response = AsyncMock()
        mock_response.is_success = False
        mock_response.json.return_value = {
            "status": "error",
            "message": "Role not available",
        }

        mock_client = AsyncMock()
        mock_client.post.return_value = mock_response
        mock_client.__aenter__ = AsyncMock(return_value=mock_client)
        mock_client.__aexit__ = AsyncMock(return_value=None)
        mock_client_cls.return_value = mock_client

        response = client.post(
            "/oauth/login/select",
            data={
                "session_id": pending_session,
                "token": "jwt",
                "role": "role1",
                "organization": "org1",
            },
        )
        assert response.status_code == 200
        assert "Role not available" in response.text
