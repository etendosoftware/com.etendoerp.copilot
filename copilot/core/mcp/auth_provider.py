"""Custom AuthProvider for Etendo Copilot.

Provides a small, easily-customizable AuthProvider implementation and two
factory functions: `createAuthProvider` (camelCase) and
`create_auth_provider` (snake_case) for backwards compatibility with
different call sites.
"""

import json
from typing import Any, List, Optional

import httpx
from copilot.baseutils.logging_envvar import (
    copilot_debug,
    copilot_error,
    copilot_warning,
)
from copilot.core.utils.etendo_utils import (
    get_etendo_host,
    normalize_etendo_token,
)
from fastmcp.server.auth.auth import AuthProvider
from mcp.server.auth.provider import AccessToken

DEFAULT_RESOURCE_URL = "http://localhost"


class CopilotAuthProvider(AuthProvider):
    """Minimal auth provider that verifies tokens against Etendo's /sws/assistants.

    Behavior:
    - If token is cached in `authenticated_tokens` it is accepted.
    - Otherwise it calls GET {ETENDO_HOST}/sws/copilot/assistants with the Bearer
      token and checks whether this provider's `identifier` appears in the returned
      list (matching by `app_id`). If present, the token is cached and an
      AccessToken is returned.
    """

    # Simple in-memory cache mapping token_value -> AccessToken
    authenticated_tokens: dict[str, AccessToken] = {}

    def __init__(self, identifier: Optional[str] = None):
        super().__init__()
        # instance identifier (agent/assistant id or name)
        self.identifier = identifier

    async def verify_token(self, token: str) -> Optional[AccessToken]:
        """Verify the provided token asynchronously.

        Returns an AccessToken when verified, otherwise None.
        """
        if not token:
            return None

        # Normalize token and strip Bearer for internal storage
        normalized = normalize_etendo_token(token)
        token_value = normalized[len("Bearer ") :] if normalized.startswith("Bearer ") else normalized

        # Return cached token if present
        if token_value in self.authenticated_tokens:
            copilot_debug("AuthProvider: token found in cache")
            return self.authenticated_tokens[token_value]

        # If no identifier configured, cannot verify against assistants
        if not self.identifier:
            copilot_debug("AuthProvider: no identifier configured, rejecting token")
            return None

        try:
            assistants = await self._fetch_assistants_for_token(token_value)
            if not assistants:
                copilot_warning(
                    f"AuthProvider: no assistants returned by Etendo for identifier '{self.identifier}'"
                )
                return None
        except Exception as e:
            copilot_error(f"AuthProvider: error fetching assistants: {e}")
            return None

        for a in assistants:
            app_id = a.get("app_id", "") if isinstance(a, dict) else ""
            if app_id == self.identifier:
                access = AccessToken(token=token_value, client_id="copilot", scopes=[], expires_at=None)
                # Cache and return
                self.authenticated_tokens[token_value] = access
                copilot_debug(f"AuthProvider: token authorized for identifier '{self.identifier}'")
                return access

        # Log details to help diagnose mismatches
        found_ids = [a.get("app_id", "?") if isinstance(a, dict) else "?" for a in assistants]
        copilot_warning(
            f"AuthProvider: agent '{self.identifier}' not found in assistants list. "
            f"Available app_ids: {found_ids}"
        )
        return None

    async def _fetch_assistants_for_token(self, token: str) -> List[Any]:
        """Fetch assistants list from Etendo using async HTTP to avoid blocking the event loop."""
        etendo_host = get_etendo_host()
        url = f"{etendo_host}/sws/copilot/assistants"
        headers = {
            "Authorization": normalize_etendo_token(token),
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

        copilot_debug(f"AuthProvider: querying {url}")

        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                response = await client.get(url, headers=headers)
        except httpx.RequestError as e:
            copilot_error(f"AuthProvider: HTTP error contacting Etendo: {e}")
            return []

        if not response.is_success:
            copilot_warning(f"AuthProvider: Etendo returned {response.status_code} for assistants endpoint")
            return []

        try:
            # Etendo may return ISO-8859-1 encoded responses
            body = json.loads(response.content.decode("iso-8859-1"))
        except Exception as e:
            copilot_error(f"AuthProvider: failed to parse assistants response: {e}")
            return []

        # Determine assistants list shape
        if isinstance(body, list):
            return body
        if isinstance(body, dict):
            return body.get("assistants") or body.get("data") or []
        return []
