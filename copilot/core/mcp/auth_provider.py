"""Custom AuthProvider for Etendo Copilot.

Provides a small, easily-customizable AuthProvider implementation and two
factory functions: `createAuthProvider` (camelCase) and
`create_auth_provider` (snake_case) for backwards compatibility with
different call sites.
"""

from typing import Any, List, Optional

from copilot.baseutils.logging_envvar import copilot_debug, copilot_error
from copilot.core.mcp.tools.agent_tools import MCPException
from copilot.core.utils.etendo_utils import (
    call_etendo,
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
    - Otherwise it calls GET {ETENDO_HOST}/sws/assistants with the Bearer token and
      checks whether this provider's `identifier` appears in the returned list
      (matching by `assistant_id` or `name`). If present, the token is cached and
      an AccessToken is returned.
    """

    # Simple in-memory cache mapping token_value -> AccessToken
    authenticated_tokens: dict[str, AccessToken] = {}

    def __init__(
        self, identifier: Optional[str] = None, resource_server_url: Optional[str] = DEFAULT_RESOURCE_URL
    ):
        copilot_debug(resource_server_url)
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
            assistants = await self._fetch_assistants_for_token(token)
            if not assistants:
                copilot_debug("AuthProvider: no assistants returned by Etendo")
                return None
        except Exception as e:
            copilot_error(f"AuthProvider: error verifying token: {e}")
            raise MCPException(
                "Error getting assistants available to the user. Check the host and the token."
            ) from e

        for a in assistants:
            if a["app_id"] == self.identifier:
                access = AccessToken(token=token_value, client_id="copilot", scopes=[], expires_at=None)
                # Cache and return
                self.authenticated_tokens[token_value] = access
                copilot_debug(f"AuthProvider: token authorized for identifier {self.identifier}")
                return access

        raise MCPException(f"User not authorized for agent '{self.identifier}'")

    async def _fetch_assistants_for_token(self, token: str) -> List[Any]:
        """Helper: call Etendo /sws/assistants and return a list of assistant objects."""
        etendo_host = get_etendo_host()
        copilot_debug(f"AuthProvider: querying Etendo assistants at {etendo_host}/sws/assistants")

        # Use internal call_etendo util which handles headers and errors consistently
        response = call_etendo(
            method="GET",
            url=etendo_host,
            endpoint="/sws/copilot/assistants",
            body_params={},
            access_token=token,
        )

        if not response or isinstance(response, dict) and response.get("error"):
            copilot_debug(f"AuthProvider: call_etendo returned error: {response}")
            return []

        body = response

        # Determine assistants list shape
        if isinstance(body, list):
            return body
        if isinstance(body, dict):
            return body.get("assistants") or body.get("data") or []
        return []

    def _assistant_matches(self, a: Any) -> bool:
        """Helper: return True if assistant object/dict matches this provider's identifier."""
        try:
            aid = a.get("assistant_id") if isinstance(a, dict) else getattr(a, "assistant_id", None)
            aname = a.get("name") if isinstance(a, dict) else getattr(a, "name", None)
            return aid == self.identifier or aname == self.identifier
        except Exception:
            return False
