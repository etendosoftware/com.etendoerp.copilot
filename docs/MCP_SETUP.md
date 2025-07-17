# MCP Server Configuration

## Overview

The Etendo Copilot includes a Model Context Protocol (MCP) server that **always runs** in parallel with the main FastAPI server. This allows integration with Claude Desktop and other MCP clients.

**Transport**: The MCP server uses **HTTP Streaming**, the modern standard for real-time communication:
- ✅ **Native HTTP streaming** protocol (modern standard)
- ✅ **Direct HTTP communication** without SSE or WebSocket complexity
- ✅ **Bi-directional streaming** over standard HTTP
- ✅ **Modern replacement** for SSE and WebSockets
- ✅ **Protocol-native** streaming support

The MCP server implementation is completely contained within the `copilot/core/mcp/` module, keeping the main application code clean and modular.

## Environment Variables

### COPILOT_PORT_MCP
- **Description**: Port for the MCP server
- **Default**: 5007 (always runs, even if not specified)
- **Type**: Integer
- **Required**: Optional (uses default 5007 if not set)

## Configuration

The MCP server starts automatically on port 5007. To use a custom port, add to your `.env` file:

```bash
COPILOT_PORT_MCP=5009  # Optional: custom port
```

If `COPILOT_PORT_MCP` is not set, the server will use port **5007** by default.

## Usage

1. Start the application normally with `python run.py`
2. Both servers will start automatically:
   - Main FastAPI server on port 5000 (or `COPILOT_PORT`)
   - MCP server on port 5007 (or `COPILOT_PORT_MCP`)

**No configuration required** - the MCP server is always enabled.

## Server Architecture

### Parallel Execution
- The MCP server runs in a separate daemon thread with its own asyncio event loop
- Uses `uvicorn.Server` for async ASGI support (not `uvicorn.run` which blocks)
- Graceful shutdown handling with signal handlers
- No blocking of the main FastAPI server

### Threading Model
```
Main Process
├── Main Thread (FastAPI server via uvicorn.run)
└── MCP Thread (MCP server via asyncio event loop)
    └── uvicorn.Server (async ASGI)
```

## Server Output

When both servers start, you'll see colored output:
- 🟢 Green: Main server status
- 🔵 Blue: MCP server status
- 🟣 Purple: Debug information
- 🔴 Red: Error messages

## Integration with Claude Desktop

To integrate with Claude Desktop, add the following to your Claude Desktop configuration:

```json
{
  "mcpServers": {
    "etendo-copilot": {
      "command": "node",
      "args": ["path/to/mcp-client.js"],
      "env": {
        "MCP_SERVER_URL": "http://localhost:5009"
      }
    }
  }
}
```

## Development Notes

- **Always enabled**: MCP server starts automatically on every application launch
- **Default port 5007**: No configuration needed for basic usage
- **No uvicorn conflicts**: Uses `uvicorn.Server` (async) instead of `uvicorn.run` (blocking)
- **Modular design**: All MCP code is isolated in the `mcp/` module
- **Clean shutdown**: Automatic cleanup on SIGTERM/SIGINT
- **Custom ports**: Use `COPILOT_PORT_MCP` environment variable for custom ports
- **Debug support**: Inherits debug settings from main server
- **Thread-safe**: Proper asyncio loop management per thread

## Module Structure

```
copilot/core/mcp/
├── __init__.py                    # Module exports
├── simplified_dynamic_server.py   # Dynamic MCP server implementation
├── simplified_dynamic_manager.py  # Dynamic MCP lifecycle manager
├── simplified_dynamic_utils.py    # Dynamic MCP utilities and cleanup
├── utils.py                       # Integration utilities
├── tools/                         # MCP tool implementations
│   ├── __init__.py
│   └── base.py
├── resources/                     # MCP resource providers
│   ├── __init__.py
│   └── base.py
├── schemas/                       # Pydantic models
│   ├── __init__.py
│   └── protocol.py
└── handlers/                      # Request handlers
    └── __init__.py
```

## FastMCP Decorator Syntax

When developing tools and resources for the MCP server, use the correct FastMCP decorator syntax:

### ✅ Correct Syntax
```python
# Adding tools
@app.tool()  # Note: parentheses required
def my_tool():
    """Tool description"""
    return "result"

# Adding resources
@app.resource("uri://example")
def my_resource():
    """Resource description"""
    return "content"
```

### ❌ Incorrect Syntax
```python
# This will cause error: "The @tool decorator was used incorrectly"
@app.tool  # Missing parentheses
def my_tool():
    return "result"
```

## HTTP Streaming Transport

The MCP server uses modern HTTP streaming transport, the current standard for real-time communication:

### Connection Endpoints
- **HTTP Streaming**: `http://localhost:5007`
- **Direct Protocol**: Native MCP over HTTP streaming
- **No additional paths**: Direct connection to the server

### Client Connection
Modern MCP clients connect directly to the HTTP streaming endpoint:

```bash
# Direct HTTP streaming connection
curl -N http://localhost:5007
```

```python
# Python MCP client connection
import asyncio
import httpx

async def connect_to_mcp():
    async with httpx.AsyncClient() as client:
        async with client.stream('GET', 'http://localhost:5007') as response:
            async for chunk in response.aiter_text():
                print("MCP Response:", chunk)

asyncio.run(connect_to_mcp())
```

### Transport Benefits
- **Modern Standard**: Latest MCP transport protocol
- **Direct HTTP**: No SSE or WebSocket wrapper needed
- **Streaming**: Native bi-directional communication
- **Efficient**: Optimized for real-time data exchange
- **Compatible**: Works with standard HTTP tools and libraries
