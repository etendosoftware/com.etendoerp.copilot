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
    return read_optional_env_var("COPILOT_PROXY_URL", None)


def get_openai_client():
    """Create and return an OpenAI client configured with the proxy URL.

    Uses get_proxy_url() to obtain a base URL and instantiates OpenAI with it.

    Returns:
        openai.OpenAI: Configured OpenAI client instance.
    """
    return OpenAI(base_url=get_proxy_url())
