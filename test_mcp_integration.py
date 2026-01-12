import asyncio
import json
import os
import sys

from copilot.core.agent.multimodel_agent import (
    convert_mcp_servers_config,
    get_mcp_tools,
)

# Add current directory to sys.path to allow importing from copilot
sys.path.append(os.getcwd())


async def test_integration():
    print("--- TESTING MCP INTEGRATION LOGIC ---")

    # Input format similar to what comes from the UI/QuestionSchema
    mcp_servers_list = [
        {
            "name": "algolia",
            "url": "https://mcp.us.algolia.com/1/BcExDoAgDAXQK9EKLQ2zm-7KQoj9Ji446ODxfW9bl0q1zrsWhxl3cHJJKVjUKBZOZBUCd6aCF8Pv5vfxtGs4Pi6kElmmkO0H/mcp/",
            "type": "streamable-http",  # Using 'type' as it's common in manifests
        }
    ]

    print(f"Input configuration:\n{json.dumps(mcp_servers_list, indent=2)}")

    # Convert using the project's logic
    converted_config = convert_mcp_servers_config(mcp_servers_list)

    print(f"\nConverted configuration (autodetected headers):\n{json.dumps(converted_config, indent=2)}")

    # Verify headers were applied
    algolia_config = converted_config.get("algolia", {})
    headers = algolia_config.get("headers", {})

    if "User-Agent" in headers and "text/event-stream" in headers.get("Accept", ""):
        print("\n✅ Verification: Headers correctly autodetected and applied.")
    else:
        print("\n❌ Verification: Headers missing or incorrect.")
        sys.exit(1)

    print("\nAttempting to fetch tools using converted config...")
    try:
        tools = await get_mcp_tools(converted_config)

        if tools:
            print(f"\n✅ Success! Retrieved {len(tools)} tools:")
            for tool in tools:
                print(f"- {tool.name}")
        else:
            print("\n❌ Failure: No tools retrieved.")
            sys.exit(1)

    except Exception as e:
        print(f"\n❌ Error during tool retrieval: {type(e).__name__}: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(test_integration())
