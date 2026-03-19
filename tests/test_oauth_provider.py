"""Unit tests for EtendoOAuthProvider."""

import time

import pytest
from copilot.core.mcp.oauth_provider import (
    EtendoOAuthProvider,
    PendingAuth,
)
from mcp.server.auth.provider import (
    AuthorizationParams,
    TokenError,
)
from mcp.shared.auth import OAuthClientInformationFull
from pydantic import AnyHttpUrl


@pytest.fixture
def provider():
    return EtendoOAuthProvider(base_url="http://localhost:5006")


@pytest.fixture
def sample_client():
    return OAuthClientInformationFull(
        client_id="test-client-123",
        redirect_uris=[AnyHttpUrl("http://localhost:3000/callback")],
        grant_types=["authorization_code"],
        response_types=["code"],
        token_endpoint_auth_method="none",
    )


@pytest.fixture
def sample_params():
    return AuthorizationParams(
        state="test-state-abc",
        scopes=["mcp"],
        code_challenge="test-challenge-xyz",
        redirect_uri=AnyHttpUrl("http://localhost:3000/callback"),
        redirect_uri_provided_explicitly=True,
    )


class TestClientRegistration:
    @pytest.mark.asyncio
    async def test_register_and_get_client(self, provider, sample_client):
        await provider.register_client(sample_client)
        result = await provider.get_client("test-client-123")
        assert result is not None
        assert result.client_id == "test-client-123"

    @pytest.mark.asyncio
    async def test_get_unknown_client_returns_none(self, provider):
        result = await provider.get_client("nonexistent")
        assert result is None

    @pytest.mark.asyncio
    async def test_register_overwrites_existing(self, provider, sample_client):
        await provider.register_client(sample_client)
        updated = OAuthClientInformationFull(
            client_id="test-client-123",
            redirect_uris=[AnyHttpUrl("http://localhost:4000/callback")],
            grant_types=["authorization_code"],
            response_types=["code"],
            token_endpoint_auth_method="none",
        )
        await provider.register_client(updated)
        result = await provider.get_client("test-client-123")
        assert str(result.redirect_uris[0]) == "http://localhost:4000/callback"


class TestAuthorization:
    @pytest.mark.asyncio
    async def test_authorize_returns_login_url(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        assert "/oauth/login?session_id=" in url

    @pytest.mark.asyncio
    async def test_authorize_creates_pending_auth(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]
        pending = provider.get_pending_auth(session_id)
        assert pending is not None
        assert pending.client_id == "test-client-123"
        assert pending.params.state == "test-state-abc"


class TestCompleteAuth:
    @pytest.mark.asyncio
    async def test_complete_auth_returns_redirect_with_code(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]

        redirect_url = provider.complete_auth(session_id, "fake-jwt-token")
        assert redirect_url is not None
        assert "code=" in redirect_url
        assert "state=test-state-abc" in redirect_url
        assert "http://localhost:3000/callback" in redirect_url

    @pytest.mark.asyncio
    async def test_complete_auth_invalid_session_returns_none(self, provider):
        result = provider.complete_auth("invalid-session", "fake-jwt")
        assert result is None

    @pytest.mark.asyncio
    async def test_complete_auth_consumes_session(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]

        provider.complete_auth(session_id, "fake-jwt")
        # Second call should return None (session consumed)
        result = provider.complete_auth(session_id, "fake-jwt")
        assert result is None


class TestAuthorizationCodeExchange:
    @pytest.mark.asyncio
    async def test_exchange_code_returns_jwt_as_access_token(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]

        redirect_url = provider.complete_auth(session_id, "etendo-jwt-abc123")
        code = redirect_url.split("code=")[1].split("&")[0]

        auth_code = await provider.load_authorization_code(sample_client, code)
        assert auth_code is not None
        assert auth_code.client_id == "test-client-123"

        token = await provider.exchange_authorization_code(sample_client, auth_code)
        assert token.access_token == "etendo-jwt-abc123"
        assert token.token_type == "Bearer"

    @pytest.mark.asyncio
    async def test_exchange_code_is_single_use(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]

        redirect_url = provider.complete_auth(session_id, "jwt-token")
        code = redirect_url.split("code=")[1].split("&")[0]

        auth_code = await provider.load_authorization_code(sample_client, code)
        await provider.exchange_authorization_code(sample_client, auth_code)

        # Second exchange should fail
        with pytest.raises(TokenError):
            await provider.exchange_authorization_code(sample_client, auth_code)

    @pytest.mark.asyncio
    async def test_load_code_wrong_client_returns_none(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]

        redirect_url = provider.complete_auth(session_id, "jwt-token")
        code = redirect_url.split("code=")[1].split("&")[0]

        other_client = OAuthClientInformationFull(
            client_id="other-client",
            redirect_uris=[AnyHttpUrl("http://localhost:3000/callback")],
            grant_types=["authorization_code"],
            response_types=["code"],
            token_endpoint_auth_method="none",
        )
        result = await provider.load_authorization_code(other_client, code)
        assert result is None


class TestAccessToken:
    @pytest.mark.asyncio
    async def test_load_access_token_after_exchange(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]

        redirect_url = provider.complete_auth(session_id, "jwt-xyz")
        code = redirect_url.split("code=")[1].split("&")[0]

        auth_code = await provider.load_authorization_code(sample_client, code)
        await provider.exchange_authorization_code(sample_client, auth_code)

        token = await provider.load_access_token("jwt-xyz")
        assert token is not None
        assert token.token == "jwt-xyz"
        assert token.client_id == "test-client-123"

    @pytest.mark.asyncio
    async def test_verify_token_delegates_to_load(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]

        redirect_url = provider.complete_auth(session_id, "jwt-verify-test")
        code = redirect_url.split("code=")[1].split("&")[0]
        auth_code = await provider.load_authorization_code(sample_client, code)
        await provider.exchange_authorization_code(sample_client, auth_code)

        result = await provider.verify_token("jwt-verify-test")
        assert result is not None
        assert result.token == "jwt-verify-test"


class TestRefreshToken:
    @pytest.mark.asyncio
    async def test_load_refresh_token_returns_none(self, provider, sample_client):
        result = await provider.load_refresh_token(sample_client, "any-token")
        assert result is None

    @pytest.mark.asyncio
    async def test_exchange_refresh_token_raises(self, provider, sample_client):
        from mcp.server.auth.provider import RefreshToken

        rt = RefreshToken(token="fake", client_id="test", scopes=[], expires_at=None)
        with pytest.raises(TokenError):
            await provider.exchange_refresh_token(sample_client, rt, [])


class TestRevocation:
    @pytest.mark.asyncio
    async def test_revoke_removes_token(self, provider, sample_client, sample_params):
        await provider.register_client(sample_client)
        url = await provider.authorize(sample_client, sample_params)
        session_id = url.split("session_id=")[1]

        redirect_url = provider.complete_auth(session_id, "jwt-revoke")
        code = redirect_url.split("code=")[1].split("&")[0]
        auth_code = await provider.load_authorization_code(sample_client, code)
        await provider.exchange_authorization_code(sample_client, auth_code)

        token_obj = await provider.load_access_token("jwt-revoke")
        assert token_obj is not None

        await provider.revoke_token(token_obj)
        assert await provider.load_access_token("jwt-revoke") is None


class TestPendingAuthTTL:
    def test_expired_pending_auth_returns_none(self, provider):
        provider._pending_auth["expired-session"] = PendingAuth(
            params=AuthorizationParams(
                state="s",
                scopes=[],
                code_challenge="c",
                redirect_uri=AnyHttpUrl("http://localhost/cb"),
                redirect_uri_provided_explicitly=True,
            ),
            client_id="test",
            created_at=time.time() - 600,  # 10 minutes ago (> 5 min TTL)
        )
        result = provider.get_pending_auth("expired-session")
        assert result is None
