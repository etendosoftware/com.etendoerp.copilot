import asyncio
import os
import platform
import sys
from typing import Dict

from langchain_mcp_adapters.client import MultiServerMCPClient


def get_default_user_agent():
    system = platform.system()
    if system == "Darwin":
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    elif system == "Windows":
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"


async def list_tools_from_mcp(mcp_config: Dict[str, Dict]):
    """Given a minimal mcp_config, ensure required headers are present and list tools."""
    # Ensure each server has headers and that required headers exist
    for _name, cfg in mcp_config.items():
        headers = cfg.get("headers", {})
        # Inject defaults if missing
        if "User-Agent" not in headers and "user-agent" not in map(str.lower, headers.keys()):
            headers["User-Agent"] = os.getenv("MCP_USER_AGENT", get_default_user_agent())
        if "accept" not in map(str.lower, headers.keys()):
            headers["accept"] = "application/json, text/event-stream"
        if "content-type" not in map(str.lower, headers.keys()):
            headers["content-type"] = "application/json"
        cfg["headers"] = headers

    # Initialize client and get tools
    client = MultiServerMCPClient(mcp_config)
    tools = await client.get_tools()
    return tools


async def main():
    # Minimal config the user wants to pass
    mcp_config = {
        "algolia": {
            "url": "https://mcp.us.algolia.com/1/BcExDoAgDAXQK9EKLQ2zm-7KQoj9Ji446ODxfW9bl0q1zrsWhxl3cHJJKVjUKBZOZBUCd6aCF8Pv5vfxtGs4Pi6kElmmkO0H/mcp",
            "transport": "streamable-http",
        }
    }

    print("Using minimal config:")
    print(mcp_config)

    try:
        tools = await list_tools_from_mcp(mcp_config)
        print(f"Found {len(tools)} tools:")
        for t in tools:
            print(f"- {t.name}: {getattr(t, 'description', '')}")

    except Exception as e:
        print(f"Error: {type(e).__name__}: {e}")
        # If ExceptionGroup, print subexceptions
        try:
            pass
        except Exception:
            pass
        if isinstance(e, BaseException) and hasattr(e, "exceptions"):
            for sub in getattr(e, "exceptions", []):
                print("  sub:", type(sub).__name__, sub)
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
