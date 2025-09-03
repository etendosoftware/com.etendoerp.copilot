import asyncio
import types

import pytest
from copilot.core.mcp.auth_provider import CopilotAuthProvider
from copilot.core.mcp.tools.agent_tools import MCPException


class DummyResp:
    def __init__(self, data):
        self._data = data

    def json(self):
        return self._data


@pytest.mark.asyncio
async def test_verify_token_no_identifier(monkeypatch):
    provider = CopilotAuthProvider(identifier=None)
    res = await provider.verify_token("Bearer token")
    assert res is None


@pytest.mark.asyncio
async def test_verify_token_cached(monkeypatch):
    provider = CopilotAuthProvider(identifier="my-agent")
    # Pre-populate cache
    provider.authenticated_tokens["token123"] = types.SimpleNamespace(token="token123")

    # Use normalized token with Bearer prefix
    res = await provider.verify_token("Bearer token123")
    assert res is not None


@pytest.mark.asyncio
async def test_verify_token_calls_etendo_and_authorizes(monkeypatch):
    called = {}

    async def fake_fetch(self, token):
        # emulate assistants returned by Etendo
        called["token"] = token
        await asyncio.sleep(0)
        return [{"app_id": "my-agent", "assistant_id": "my-agent", "name": "my-agent"}]

    monkeypatch.setattr(CopilotAuthProvider, "_fetch_assistants_for_token", fake_fetch)

    provider = CopilotAuthProvider(identifier="my-agent")
    res = await provider.verify_token("Bearer sometoken")
    assert res is not None
    # ensure cached
    assert "sometoken" in provider.authenticated_tokens


@pytest.mark.asyncio
async def test_verify_token_not_authorized(monkeypatch):
    async def fake_fetch(self, token):
        await asyncio.sleep(0)
        return [{"app_id": "other", "assistant_id": "other", "name": "other"}]

    monkeypatch.setattr(CopilotAuthProvider, "_fetch_assistants_for_token", fake_fetch)

    provider = CopilotAuthProvider(identifier="my-agent")
    with pytest.raises(MCPException):
        await provider.verify_token("Bearer abc")
