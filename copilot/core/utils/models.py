"""Utilities for OpenAI client creation.

Provides helper functions to read the proxy URL from environment and
create a configured OpenAI client.
"""

from copilot.baseutils.logging_envvar import read_optional_env_var
from copilot.core.utils.etendo_utils import get_extra_info
from openai import OpenAI


def get_request_llm():
    """Return per-request provider credentials passed privately by the Java runtime."""
    extra_info = get_extra_info() or {}
    return extra_info.get("llm") or {}


def get_proxy_url():
    """Return the request's gateway URL, falling back to legacy environment configuration."""
    request_llm = get_request_llm()
    if request_llm.get("gateway_mode"):
        return request_llm.get("base_url")
    return read_optional_env_var("copilot.proxy.url", None)


def get_api_key(provider_to_use):
    """Return the Java-resolved request key, or the legacy environment fallback."""
    request_llm = get_request_llm()
    if request_llm:
        provider = request_llm.get("provider")
        aliases = {"google_genai": "gemini"}
        if provider_to_use == "openai" and request_llm.get("gateway_mode"):
            return request_llm.get("api_key")
        if aliases.get(provider_to_use, provider_to_use) == provider:
            return request_llm.get("api_key")
    if provider_to_use == "google_genai":
        env_var_name = "gemini.api.key"
    else:
        env_var_name = f"{provider_to_use.lower()}.api.key"
    return read_optional_env_var(env_var_name, None)


def get_openai_client():
    """Create and return an OpenAI client configured with the current gateway URL."""
    return OpenAI(base_url=get_proxy_url(), api_key=get_api_key("openai"))
