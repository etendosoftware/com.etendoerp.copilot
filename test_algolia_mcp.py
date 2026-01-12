import asyncio
import os
import platform
import sys

import httpx
from langchain_mcp_adapters.client import MultiServerMCPClient


def get_default_user_agent():
    """Builds a credible User-Agent based on the current OS."""
    system = platform.system()
    if system == "Darwin":
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    elif system == "Windows":
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"


async def test_connection():
    # Use the base URL as provided
    url = "https://mcp.us.algolia.com/1/BcExDoAgDAXQK9EKLQ2zm-7KQoj9Ji446ODxfW9bl0q1zrsWhxl3cHJJKVjUKBZOZBUCd6aCF8Pv5vfxtGs4Pi6kElmmkO0H/mcp/"

    # Autodetect or get from Environment Variable (useful for Docker)
    user_agent = os.getenv("MCP_USER_AGENT", get_default_user_agent())

    print("--- TESTING CONNECTION ---")
    print(f"Using User-Agent: {user_agent}")

    mcp_servers_config = {
        "algolia": {
            "url": url,
            "transport": "streamable-http",  # Explicitly using the new transport type
            "headers": {
                "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
                "accept": "application/json, text/event-stream",
                "content-type": "application/json",
            },
        }
    }

    try:
        # Initialize the client (Note: option 1 as requested before)        import platform

        def get_browser_user_agent():
            """Genera un User-Agent creíble basado en el sistema operativo."""
            os_name = platform.system()
            # Mapeo básico para simular un navegador moderno
            if os_name == "Darwin":  # macOS
                return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
            elif os_name == "Windows":
                return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
            else:  # Linux / Docker
                return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"

        client = MultiServerMCPClient(mcp_servers_config)
        print("Client initialized. Fetching tools...")

        # 30 second timeout for tool retrieval
        tools = await asyncio.wait_for(client.get_tools(), timeout=30.0)

        print(f"\nSuccess! Found {len(tools)} tools:")
        for tool in tools:
            print(f"- {tool.name}")

    except asyncio.TimeoutError:
        print("\nError: Connection timed out.")
        sys.exit(1)
    except ExceptionGroup as eg:
        print(f"\nError: ExceptionGroup with {len(eg.exceptions)} sub-exceptions:")
        for i, e in enumerate(eg.exceptions):
            print(f"  Sub-exception {i+1}: {type(e).__name__}: {e}")
            if isinstance(e, httpx.HTTPStatusError):
                try:
                    await e.response.aread()
                    print(f"  Response content: {e.response.text}")
                except Exception:
                    pass
        sys.exit(1)
    except Exception as e:
        print(f"\nError: {type(e).__name__}: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(test_connection())
