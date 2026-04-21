import asyncio
import types

import pytest
from copilot.core.mcp.auth_provider import CopilotAuthProvider


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
    """When the agent is not in the assistants list, verify_token returns None (not raises)."""

    async def fake_fetch(self, token):
        await asyncio.sleep(0)
        return [{"app_id": "other", "assistant_id": "other", "name": "other"}]

    monkeypatch.setattr(CopilotAuthProvider, "_fetch_assistants_for_token", fake_fetch)

    provider = CopilotAuthProvider(identifier="my-agent")
    res = await provider.verify_token("Bearer abc")
    assert res is None


@pytest.mark.asyncio
async def test_verify_token_fetch_error(monkeypatch):
    """When _fetch_assistants_for_token raises, verify_token returns None (not raises)."""

    async def fake_fetch(self, token):
        raise ConnectionError("connection error")

    monkeypatch.setattr(CopilotAuthProvider, "_fetch_assistants_for_token", fake_fetch)

    provider = CopilotAuthProvider(identifier="my-agent")
    res = await provider.verify_token("Bearer abc")
    assert res is None
