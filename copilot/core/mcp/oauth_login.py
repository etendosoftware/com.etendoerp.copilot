"""
OAuth login routes for Etendo Copilot MCP Server.

Provides the custom login UI flow that bridges between the OAuth 2.1 authorization
endpoint and Etendo's /sws/login API. After successful login, the user is redirected
back to the MCP client with an authorization code.
"""

import html as html_module
import json
import logging
from pathlib import Path
from typing import TYPE_CHECKING, Optional

import httpx
from copilot.core.utils.etendo_utils import get_etendo_host
from fastapi import APIRouter, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse

_CONTENT_TYPE_JSON = "application/json"
_MSG_MISSING_SESSION = "Missing session ID."
_MSG_SESSION_EXPIRED = "Session expired. Please try connecting again."

if TYPE_CHECKING:
    from copilot.core.mcp.oauth_provider import EtendoOAuthProvider

logger = logging.getLogger(__name__)

# Load the login template once at module level
_TEMPLATE_PATH = Path(__file__).parent / "templates" / "login.html"
_TEMPLATE_CACHE: Optional[str] = None


def _get_template() -> str:
    global _TEMPLATE_CACHE
    if _TEMPLATE_CACHE is None:
        _TEMPLATE_CACHE = _TEMPLATE_PATH.read_text(encoding="utf-8")
    return _TEMPLATE_CACHE


def _render_login(
    session_id: str,
    error: str = "",
    username: str = "",
    step2_active: bool = False,
    login_token: str = "",
    role_options: str = "",
    org_options: str = "",
    role_org_map: Optional[dict] = None,
) -> HTMLResponse:
    """Render the login template with the given parameters."""
    template = _get_template()

    error_html = ""
    if error:
        error_html = f'<div class="error-msg">{error}</div>'

    html = template.replace("{{session_id}}", html_module.escape(session_id))
    html = html.replace("{{error_html}}", error_html)
    html = html.replace("{{username}}", html_module.escape(username))
    html = html.replace("{{step1_active}}", "" if step2_active else "active")
    html = html.replace("{{step2_active}}", "active" if step2_active else "")
    html = html.replace("{{login_token}}", login_token)
    html = html.replace("{{role_options}}", role_options)
    html = html.replace("{{org_options}}", org_options)
    html = html.replace("{{role_org_map_json}}", json.dumps(role_org_map or {}))

    return HTMLResponse(content=html)


def _build_role_options(role_list: list) -> str:
    """Build HTML option tags from a role list."""
    options = []
    for role in role_list:
        role_id = role.get("id", "")
        role_name = role.get("name", role_id)
        options.append(f'<option value="{role_id}">{role_name}</option>')
    return "\n".join(options)


def _build_org_options(org_list: list) -> str:
    """Build HTML option tags from an org list."""
    options = []
    for org in org_list:
        org_id = org.get("id", "")
        org_name = org.get("name", org_id)
        options.append(f'<option value="{org_id}">{org_name}</option>')
    return "\n".join(options)


def _build_role_org_map(role_list: list) -> dict:
    """Build a role->orgs mapping for the JavaScript cascade."""
    result = {}
    for role in role_list:
        role_id = role.get("id", "")
        orgs = role.get("orgList", [])
        result[role_id] = [{"id": org.get("id", ""), "name": org.get("name", "")} for org in orgs]
    return result


async def _call_etendo_login(url: str, payload: dict, extra_headers: Optional[dict] = None):
    """Call Etendo /sws/login and return (response, error_string)."""
    headers = {"Content-Type": _CONTENT_TYPE_JSON, "Accept": _CONTENT_TYPE_JSON}
    if extra_headers:
        headers.update(extra_headers)
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(url, json=payload, headers=headers)
        return response, None
    except httpx.RequestError as e:
        return None, str(e)


def _extract_error_message(response, default_msg: str) -> str:
    """Extract error message from a failed Etendo response."""
    try:
        err_data = response.json()
        if "message" in err_data:
            return err_data["message"]
    except Exception:
        pass
    return default_msg


def _parse_etendo_response(response) -> Optional[dict]:
    """Parse an Etendo ISO-8859-1 response body as JSON, or return None on failure."""
    try:
        return json.loads(response.content.decode("iso-8859-1"))
    except Exception as e:
        logger.error(f"OAuth login: could not parse Etendo response: {e}")
        return None


def _complete_auth_or_expired(provider, session_id: str, token: str):
    """Complete auth and return RedirectResponse, or expired error HTMLResponse."""
    redirect_url = provider.complete_auth(session_id, token)
    if redirect_url is None:
        return _render_login(session_id="", error=_MSG_SESSION_EXPIRED)
    return RedirectResponse(url=redirect_url, status_code=302)


async def _handle_login_page(provider, session_id: str):
    """Render the login page for a pending OAuth authorization."""
    if not session_id:
        return _render_login(session_id="", error=_MSG_MISSING_SESSION)

    if provider.get_pending_auth(session_id) is None:
        return _render_login(
            session_id="",
            error="Session expired or invalid. Please try connecting again from your MCP client.",
        )

    return _render_login(session_id=session_id)


async def _handle_login_submit(provider, session_id, username, password, use_defaults):
    """Handle login form submission (Step 1: credentials)."""
    if not session_id:
        return _render_login(session_id="", error=_MSG_MISSING_SESSION)

    if provider.get_pending_auth(session_id) is None:
        return _render_login(session_id="", error=_MSG_SESSION_EXPIRED)

    if not username or not password:
        return _render_login(
            session_id=session_id,
            error="Username and password are required.",
            username=username,
        )

    etendo_host = get_etendo_host()
    response, conn_err = await _call_etendo_login(
        f"{etendo_host}/sws/login",
        {"username": username, "password": password},
    )
    if conn_err:
        logger.error(f"OAuth login: error contacting Etendo: {conn_err}")
        return _render_login(
            session_id=session_id,
            error="Could not connect to Etendo server. Please try again later.",
            username=username,
        )

    if not response.is_success:
        return _render_login(
            session_id=session_id,
            error=_extract_error_message(response, "Invalid credentials."),
            username=username,
        )

    data = _parse_etendo_response(response)
    if data is None:
        return _render_login(
            session_id=session_id,
            error="Unexpected response from Etendo server.",
            username=username,
        )

    token = data.get("token", "")
    if not token:
        return _render_login(
            session_id=session_id,
            error="Login failed: no token received.",
            username=username,
        )

    role_list = data.get("roleList", [])

    if use_defaults == "true" or _is_single_choice(role_list):
        return _complete_auth_or_expired(provider, session_id, token)

    all_orgs = [org for role in role_list for org in role.get("orgList", [])]

    return _render_login(
        session_id=session_id,
        step2_active=True,
        login_token=token,
        role_options=_build_role_options(role_list),
        org_options=_build_org_options(all_orgs),
        role_org_map=_build_role_org_map(role_list),
    )


async def _handle_login_select(provider, session_id, token, role, organization):
    """Handle role/org selection (Step 2)."""
    if not session_id:
        return _render_login(session_id="", error=_MSG_MISSING_SESSION)

    if provider.get_pending_auth(session_id) is None:
        return _render_login(session_id="", error=_MSG_SESSION_EXPIRED)

    if not token or not role or not organization:
        return _render_login(
            session_id=session_id,
            error="Please select a role and organization.",
            step2_active=True,
            login_token=token,
        )

    etendo_host = get_etendo_host()
    response, conn_err = await _call_etendo_login(
        f"{etendo_host}/sws/login",
        {"role": role, "organization": organization},
        extra_headers={"Authorization": f"Bearer {token}"},
    )
    if conn_err:
        logger.error(f"OAuth login select: error contacting Etendo: {conn_err}")
        return _render_login(
            session_id=session_id,
            error="Could not connect to Etendo server.",
            step2_active=True,
            login_token=token,
        )

    if not response.is_success:
        return _render_login(
            session_id=session_id,
            error=_extract_error_message(response, "Failed to set role/organization."),
            step2_active=True,
            login_token=token,
        )

    data = _parse_etendo_response(response)
    if data is None:
        return _render_login(
            session_id=session_id,
            error="Unexpected response from Etendo server.",
            step2_active=True,
            login_token=token,
        )

    final_token = data.get("token", "")
    if not final_token:
        return _render_login(
            session_id=session_id,
            error="Login failed: no token received after role selection.",
            step2_active=True,
            login_token=token,
        )

    return _complete_auth_or_expired(provider, session_id, final_token)


def create_oauth_login_router(provider: "EtendoOAuthProvider") -> APIRouter:
    """Create the FastAPI router for OAuth login endpoints.

    Args:
        provider: The EtendoOAuthProvider instance to use for auth state management.

    Returns:
        A FastAPI APIRouter with the login routes.
    """
    router = APIRouter()

    @router.get("/oauth/login")
    async def login_page(session_id: str = ""):
        return await _handle_login_page(provider, session_id)

    @router.post("/oauth/login")
    async def login_submit(
        request: Request,
        session_id: str = Form(""),
        username: str = Form(""),
        password: str = Form(""),
        use_defaults: str = Form(""),
    ):
        return await _handle_login_submit(provider, session_id, username, password, use_defaults)

    @router.post("/oauth/login/select")
    async def login_select(
        request: Request,
        session_id: str = Form(""),
        token: str = Form(""),
        role: str = Form(""),
        organization: str = Form(""),
    ):
        return await _handle_login_select(provider, session_id, token, role, organization)

    return router


def _is_single_choice(role_list: list) -> bool:
    """Check if there's effectively only one role/org combination."""
    if not role_list:
        return True  # No roles means use defaults
    if len(role_list) == 1:
        orgs = role_list[0].get("orgList", [])
        return len(orgs) <= 1
    return False
