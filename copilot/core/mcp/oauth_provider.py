"""
OAuth 2.1 Authorization Server Provider for Etendo Copilot MCP Server.

Implements the full OAuth 2.1 flow with PKCE, using Etendo's login system
for user authentication. The access token IS the Etendo JWT - no separate
token system.
"""

import asyncio
import logging
import secrets
import time
from dataclasses import dataclass, field
from typing import Dict, Optional

from fastmcp.server.auth.auth import (
    ClientRegistrationOptions,
    OAuthProvider,
    RevocationOptions,
)
from mcp.server.auth.provider import (
    AccessToken,
    AuthorizationCode,
    AuthorizationParams,
    RefreshToken,
    TokenError,
)
from mcp.shared.auth import OAuthClientInformationFull, OAuthToken

logger = logging.getLogger(__name__)

# TTL constants
AUTH_CODE_TTL_SECONDS = 5 * 60  # 5 minutes
PENDING_AUTH_TTL_SECONDS = 5 * 60  # 5 minutes
CLEANUP_INTERVAL_SECONDS = 60  # cleanup every 60s


@dataclass
class PendingAuth:
    """Stores pending authorization session data during the login flow."""

    params: AuthorizationParams
    client_id: str
    created_at: float = field(default_factory=time.time)


@dataclass
class AuthCodeData:
    """Wraps an AuthorizationCode with the associated Etendo JWT."""

    auth_code: AuthorizationCode
    etendo_jwt: str
    created_at: float = field(default_factory=time.time)


class EtendoOAuthProvider(OAuthProvider):
    """OAuth 2.1 provider that authenticates users via Etendo's login system.

    The access_token returned to MCP clients IS the Etendo JWT obtained
    during the login flow. CopilotAuthProvider validates it as usual.

    All state is kept in memory with TTL-based cleanup.
    """

    def __init__(
        self,
        base_url: str,
        issuer_url: Optional[str] = None,
    ):
        super().__init__(
            base_url=base_url,
            issuer_url=issuer_url or base_url,
            client_registration_options=ClientRegistrationOptions(
                enabled=True,
                valid_scopes=["mcp"],
                default_scopes=["mcp"],
            ),
            revocation_options=RevocationOptions(enabled=True),
        )

        # In-memory stores
        self._clients: Dict[str, OAuthClientInformationFull] = {}
        self._auth_codes: Dict[str, AuthCodeData] = {}
        self._tokens: Dict[str, AccessToken] = {}
        self._pending_auth: Dict[str, PendingAuth] = {}

        # Start background cleanup task
        self._cleanup_task: Optional[asyncio.Task] = None
        self._base_url_str = base_url

    def start_cleanup(self):
        """Start the background TTL cleanup task. Call after event loop is running."""
        if self._cleanup_task is None:
            self._cleanup_task = asyncio.create_task(self._cleanup_loop())

    async def _cleanup_loop(self):
        """Periodically remove expired entries from in-memory stores."""
        try:
            while True:
                await asyncio.sleep(CLEANUP_INTERVAL_SECONDS)
                now = time.time()

                # Clean expired auth codes
                expired_codes = [
                    k for k, v in self._auth_codes.items() if now - v.created_at > AUTH_CODE_TTL_SECONDS
                ]
                for k in expired_codes:
                    del self._auth_codes[k]

                # Clean expired pending auth sessions
                expired_pending = [
                    k for k, v in self._pending_auth.items() if now - v.created_at > PENDING_AUTH_TTL_SECONDS
                ]
                for k in expired_pending:
                    del self._pending_auth[k]

                if expired_codes or expired_pending:
                    logger.debug(
                        f"OAuth cleanup: removed {len(expired_codes)} auth codes, "
                        f"{len(expired_pending)} pending auth sessions"
                    )
        except asyncio.CancelledError:
            logger.debug("OAuth cleanup task cancelled")
            raise

    # ── Client Registration ──────────────────────────────────────────

    async def get_client(self, client_id: str) -> Optional[OAuthClientInformationFull]:
        return self._clients.get(client_id)

    async def register_client(self, client_info: OAuthClientInformationFull) -> None:
        self._clients[client_info.client_id] = client_info
        logger.debug(f"OAuth: registered client {client_info.client_id}")

    # ── Authorization ────────────────────────────────────────────────

    async def authorize(
        self,
        client: OAuthClientInformationFull,
        params: AuthorizationParams,
    ) -> str:
        """Start authorization: create a pending session and redirect to login page."""
        session_id = secrets.token_urlsafe(32)
        self._pending_auth[session_id] = PendingAuth(
            params=params,
            client_id=client.client_id,
        )
        logger.debug(f"OAuth: created pending auth session {session_id} for client {client.client_id}")
        # Redirect to our custom login page
        return f"{self._base_url_str}/oauth/login?session_id={session_id}"

    # ── Authorization Code ───────────────────────────────────────────

    async def load_authorization_code(
        self,
        client: OAuthClientInformationFull,
        authorization_code: str,
    ) -> Optional[AuthorizationCode]:
        data = self._auth_codes.get(authorization_code)
        if data is None:
            return None
        if data.auth_code.client_id != client.client_id:
            return None
        if data.auth_code.expires_at < time.time():
            del self._auth_codes[authorization_code]
            return None
        return data.auth_code

    async def exchange_authorization_code(
        self,
        client: OAuthClientInformationFull,
        authorization_code: AuthorizationCode,
    ) -> OAuthToken:
        """Exchange auth code for the Etendo JWT as access_token."""
        data = self._auth_codes.get(authorization_code.code)
        if data is None:
            raise TokenError(
                "invalid_grant",
                "Authorization code not found or already used.",
            )

        # Consume the code (single-use)
        del self._auth_codes[authorization_code.code]

        etendo_jwt = data.etendo_jwt

        # Store the token for later verification via load_access_token
        self._tokens[etendo_jwt] = AccessToken(
            token=etendo_jwt,
            client_id=client.client_id,
            scopes=authorization_code.scopes,
            expires_at=None,  # JWT expiry handled by Etendo
        )

        logger.debug(f"OAuth: exchanged auth code for JWT, client {client.client_id}")

        return OAuthToken(
            access_token=etendo_jwt,
            token_type="Bearer",
            expires_in=None,
            scope=" ".join(authorization_code.scopes) if authorization_code.scopes else None,
        )

    # ── Access Token ─────────────────────────────────────────────────

    async def load_access_token(self, token: str) -> Optional[AccessToken]:
        return self._tokens.get(token)

    async def verify_token(self, token: str) -> Optional[AccessToken]:
        """Verify token - delegates to load_access_token."""
        return await self.load_access_token(token)

    # ── Refresh Token (not supported) ────────────────────────────────

    async def load_refresh_token(
        self,
        client: OAuthClientInformationFull,
        refresh_token: str,
    ) -> Optional[RefreshToken]:
        return None

    async def exchange_refresh_token(
        self,
        client: OAuthClientInformationFull,
        refresh_token: RefreshToken,
        scopes: list[str],
    ) -> OAuthToken:
        raise TokenError(
            "unsupported_grant_type",
            "Refresh tokens are not supported.",
        )

    # ── Revocation ───────────────────────────────────────────────────

    async def revoke_token(
        self,
        token: AccessToken | RefreshToken,
    ) -> None:
        if isinstance(token, AccessToken) and token.token in self._tokens:
            del self._tokens[token.token]
            logger.debug("OAuth: revoked access token")

    # ── Pending Auth helpers (used by oauth_login.py) ────────────────

    def get_pending_auth(self, session_id: str) -> Optional[PendingAuth]:
        """Get a pending auth session by session_id."""
        pending = self._pending_auth.get(session_id)
        if pending and time.time() - pending.created_at > PENDING_AUTH_TTL_SECONDS:
            del self._pending_auth[session_id]
            return None
        return pending

    def complete_auth(self, session_id: str, etendo_jwt: str) -> Optional[str]:
        """Complete a pending auth session: create auth code and return redirect URL.

        Returns the full redirect URL with code and state, or None if session not found.
        """
        pending = self._pending_auth.pop(session_id, None)
        if pending is None:
            return None

        params = pending.params

        # Generate authorization code
        code_value = secrets.token_urlsafe(32)
        auth_code = AuthorizationCode(
            code=code_value,
            client_id=pending.client_id,
            redirect_uri=params.redirect_uri,
            redirect_uri_provided_explicitly=params.redirect_uri_provided_explicitly,
            scopes=params.scopes if params.scopes else [],
            expires_at=time.time() + AUTH_CODE_TTL_SECONDS,
            code_challenge=params.code_challenge,
            resource=params.resource,
        )

        self._auth_codes[code_value] = AuthCodeData(
            auth_code=auth_code,
            etendo_jwt=etendo_jwt,
        )

        # Build redirect URI
        redirect_url = str(params.redirect_uri)
        separator = "&" if "?" in redirect_url else "?"
        redirect_url += f"{separator}code={code_value}"
        if params.state:
            redirect_url += f"&state={params.state}"

        logger.debug(f"OAuth: completed auth for session {session_id}, code issued")
        return redirect_url
