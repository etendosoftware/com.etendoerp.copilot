"""Utilities for OpenAI client creation.

Provides helper functions to read the proxy URL from environment and
create a configured OpenAI client.
"""

from copilot.baseutils.logging_envvar import read_optional_env_var
from openai import OpenAI


def get_proxy_url():
    """Return the proxy URL for the OpenAI client.

    Reads the COPILOT_PROXY_URL environment variable via read_optional_env_var.

    Returns:
        str | None: The proxy URL or None if not set.
    """
    return read_optional_env_var("copilot.proxy.url", None)


def get_api_key(provider_to_use):
    """Return the API key for the specified provider.

    Reads the API key from environment variables based on the provider name.

    Args:
        provider_to_use (str): The name of the provider for which to get the API key.

    Returns:
        str | None: The API key for the provider or None if not set.
    """
    if provider_to_use == "google_genai":
        env_var_name = "gemini.api.key"
    else:
        env_var_name = f"{provider_to_use.lower()}.api.key"
    return read_optional_env_var(env_var_name, None)


def get_openai_client():
    """Create and return an OpenAI client configured with the proxy URL.

    Uses get_proxy_url() to obtain a base URL and instantiates OpenAI with it.

    Returns:
        openai.OpenAI: Configured OpenAI client instance.
    """
    return OpenAI(base_url=get_proxy_url())
